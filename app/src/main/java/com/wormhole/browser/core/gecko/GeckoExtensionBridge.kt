package com.wormhole.browser.core.gecko

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Bridges the app to page content via a bundled WebExtension instead of the
 * nonexistent GeckoSession.evaluateJS.
 *
 * GeckoView deliberately does not expose "run this JS string in the page"
 * as a session method (see GeckoJs.kt's doc comment for the background).
 * The supported route Mozilla ships in Fenix/Focus for this kind of thing is:
 *
 *   1. Bundle a WebExtension (assets/extensions/knot-bridge) with a
 *      background script and a content script.
 *   2. Register it as a *built-in* extension on the [GeckoRuntime] via
 *      [org.mozilla.geckoview.WebExtensionController.ensureBuiltIn].
 *   3. The background script opens a native-messaging [WebExtension.Port]
 *      to the app (browser.runtime.connectNative("knot-bridge")).
 *   4. The app receives that port through a [WebExtension.MessageDelegate]
 *      registered for the "knot-bridge" native-app id, and uses it to send
 *      JSON commands ({requestId, command, args}) and match JSON replies
 *      ({requestId, ok, result|error}).
 *
 * One extension install is shared by the whole runtime; what's per-tab is
 * the [WebExtension.Port] -- GeckoView opens a fresh port each time the
 * extension's background script calls connectNative, and (per Mozilla's own
 * docs) the delegate should be attached per-session so replies route back
 * to the right caller. In practice the background script forwards each
 * command to whichever tab is currently active in its window, so a single
 * shared port per session is sufficient here.
 */
object GeckoExtensionBridge {
    private const val TAG = "GeckoExtensionBridge"
    private const val EXTENSION_LOCATION = "resource://android/assets/extensions/knot-bridge/"
    private const val EXTENSION_ID = "knot-bridge@wormhole.browser"
    private const val NATIVE_APP_ID = "knot-bridge"
    private const val COMMAND_TIMEOUT_MS = 8_000L
    private const val MAX_INSTALL_ATTEMPTS = 3

    @Volatile private var extension: WebExtension? = null
    @Volatile private var installFailure: String? = null
    private val installLock = Any()

    private data class PendingCall(val deferred: CompletableDeferred<JSONObject>)

    private class SessionChannel {
        @Volatile var port: WebExtension.Port? = null
        val pending = ConcurrentHashMap<String, PendingCall>()
    }

    private val channels = ConcurrentHashMap<GeckoSession, SessionChannel>()

    /** True once the bridge extension is installed and ready to accept commands. */
    val isReady: Boolean get() = extension != null

    /** Non-null if the bridge extension failed to install (for diagnostics/settings UI). */
    val lastInstallError: String? get() = installFailure

    /**
     * Installs the bundled bridge extension on [runtime] if it hasn't been
     * already. Safe to call repeatedly (e.g. once per new session) -- the
     * install only happens once per process.
     */
    fun ensureInstalled(runtime: GeckoRuntime) {
        if (extension != null) return
        synchronized(installLock) {
            if (extension != null) return
            installOnce(runtime, attempt = 1)
        }
    }

    private fun installOnce(runtime: GeckoRuntime, attempt: Int) {
        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
            .accept(
                { ext ->
                    extension = ext
                    installFailure = null
                    Log.i(TAG, "knot-bridge extension installed (attempt $attempt)")
                },
                { error ->
                    val message = error?.message ?: "unknown install error"
                    Log.w(TAG, "knot-bridge extension failed to install (attempt $attempt): $message", error)
                    // ensureBuiltIn can fail transiently very early in process
                    // startup (runtime not fully warmed up yet); one retry
                    // clears most of those without leaving translation/agent
                    // features permanently dead for the rest of the process.
                    if (attempt < MAX_INSTALL_ATTEMPTS) {
                        installOnce(runtime, attempt + 1)
                    } else {
                        installFailure = message
                    }
                },
            )
    }

    /**
     * Wires up message/port delegates for [session] so the bridge can route
     * commands to whichever page is loaded in it. Call once per session
     * right after [ensureInstalled] (or lazily before the first [send]).
     */
    fun attach(session: GeckoSession) {
        val ext = extension ?: return
        if (channels.containsKey(session)) return
        val channel = SessionChannel()
        channels[session] = channel

        session.webExtensionController.setMessageDelegate(
            ext,
            object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    if (port.name != NATIVE_APP_ID) return
                    channel.port = port
                    port.setDelegate(object : WebExtension.PortDelegate {
                        override fun onPortMessage(message: Any, port: WebExtension.Port) {
                            handleReply(channel, message)
                        }

                        override fun onDisconnect(port: WebExtension.Port) {
                            if (channel.port === port) channel.port = null
                        }
                    })
                }
            },
            NATIVE_APP_ID,
        )
    }

    fun detach(session: GeckoSession) {
        val channel = channels.remove(session) ?: return
        channel.port?.disconnect()
        channel.pending.values.forEach { it.deferred.cancel() }
        val ext = extension ?: return
        session.webExtensionController.setMessageDelegate(ext, null, NATIVE_APP_ID)
    }

    private fun handleReply(channel: SessionChannel, raw: Any) {
        val json = try {
            when (raw) {
                is JSONObject -> raw
                is String -> JSONObject(raw)
                else -> JSONObject(raw.toString())
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Malformed reply from bridge extension: $raw", e)
            return
        }
        val requestId = json.optString("requestId").takeIf { it.isNotBlank() } ?: return
        channel.pending.remove(requestId)?.deferred?.complete(json)
    }

    /** How long to wait for the extension's port to open before giving up on a call. */
    private const val PORT_READY_TIMEOUT_MS = 4_000L
    private const val PORT_READY_POLL_MS = 100L

    /** Commands worth retrying transparently when the bridge isn't ready yet or a call drops. */
    private const val DEFAULT_RETRIES = 2
    private const val RETRY_BACKOFF_MS = 250L

    /**
     * Sends [command] with [args] to the page loaded in [session] and
     * suspends for the content script's reply.
     *
     * @return the reply's "result" as a string on success, or
     * [GeckoJs.UNAVAILABLE_SENTINEL] if the bridge isn't installed / has no
     * open port for this session (e.g. the extension failed to load, or the
     * page hasn't finished wiring its content script yet), or an "ERR:..."
     * string if the content script itself reported an error or the call
     * timed out.
     */
    suspend fun send(session: GeckoSession, command: String, args: Map<String, String> = emptyMap()): String {
        val json = JSONObject()
        args.forEach { (key, value) -> json.put(key, value) }
        return send(session, command, json)
    }

    suspend fun send(session: GeckoSession, command: String, args: JSONObject): String {
        var lastError = GeckoJs.UNAVAILABLE_SENTINEL
        repeat(DEFAULT_RETRIES + 1) { attempt ->
            val result = sendOnce(session, command, args)
            if (!isTransientFailure(result)) return result
            lastError = result
            if (attempt < DEFAULT_RETRIES) {
                kotlinx.coroutines.delay(RETRY_BACKOFF_MS * (attempt + 1))
            }
        }
        return lastError
    }

    /** Failures worth retrying: the extension/port simply wasn't ready yet, or the round trip timed out. */
    private fun isTransientFailure(result: String): Boolean {
        return result == GeckoJs.UNAVAILABLE_SENTINEL ||
            result == "ERR:BRIDGE_PORT_NOT_READY" ||
            result == "ERR:BRIDGE_TIMEOUT"
    }

    private suspend fun sendOnce(session: GeckoSession, command: String, args: JSONObject): String {
        // The extension installs asynchronously (ensureBuiltIn's callback can
        // land well after the first tab's session is created), so attach()
        // called from GeckoSessionPool at session-creation time can silently
        // no-op if `extension` wasn't set yet. Re-attach here too, once the
        // extension is actually ready, so that first session doesn't end up
        // permanently un-wired just because of that ordering race.
        val ext = extension ?: return GeckoJs.UNAVAILABLE_SENTINEL
        if (channels[session] == null) attach(session)
        val channel = channels[session] ?: return GeckoJs.UNAVAILABLE_SENTINEL
        val port = awaitPort(channel) ?: return "ERR:BRIDGE_PORT_NOT_READY"

        val requestId = UUID.randomUUID().toString()
        val payload = JSONObject().apply {
            put("requestId", requestId)
            put("command", command)
            put("args", args)
        }

        val deferred = CompletableDeferred<JSONObject>()
        channel.pending[requestId] = PendingCall(deferred)

        return try {
            port.postMessage(payload)
            val reply = withTimeoutOrNull(COMMAND_TIMEOUT_MS) { deferred.await() }
                ?: run {
                    channel.pending.remove(requestId)
                    return "ERR:BRIDGE_TIMEOUT"
                }
            if (reply.optBoolean("ok", false)) {
                reply.opt("result")?.toString() ?: ""
            } else {
                "ERR:${reply.optString("error", "unknown")}"
            }
        } catch (e: Throwable) {
            channel.pending.remove(requestId)
            "ERR:${e.message ?: e.javaClass.simpleName}"
        } finally {
            ext.let { /* keep reference alive for smart-cast clarity */ }
        }
    }

    /**
     * The extension's background script opens its native port asynchronously
     * after the page's content script connects, which can race a command
     * sent right after navigation. Poll briefly for the port instead of
     * failing immediately the first time it isn't there yet.
     */
    private suspend fun awaitPort(channel: SessionChannel): WebExtension.Port? {
        channel.port?.let { return it }
        val deadline = System.currentTimeMillis() + PORT_READY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(PORT_READY_POLL_MS)
            channel.port?.let { return it }
        }
        return null
    }
}
