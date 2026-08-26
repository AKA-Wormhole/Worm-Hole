package com.wormhole.browser.core.gecko

import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import kotlin.coroutines.resume

/**
 * Runs page-facing commands (read page text, tap, type, scroll, execute a
 * short JS expression, etc.) against a [GeckoSession].
 *
 * Unlike Android WebView, GeckoView has no public, stable
 * "evaluateJS(script)" method on GeckoSession -- Mozilla's own docs and
 * issue tracker (e.g. mozilla/geckoview#130) confirm the supported way to
 * run/communicate with page JS is a bundled WebExtension using content
 * scripts + native messaging. [GeckoExtensionBridge] is exactly that: it
 * installs assets/extensions/knot-bridge as a built-in extension and talks
 * to its content script over a native-messaging Port.
 *
 * [evaluate] therefore tries the bridge first. [evaluateAsync] still probes
 * reflectively for a same-named evaluateJS method as a last-resort fallback
 * for GeckoView builds/forks that happen to expose one; on a standard
 * release build this will find nothing, same as before.
 *
 * Every caller in this codebase (BrowserAgent's read_page, tap, type_text,
 * execute_js, etc.) must still treat [UNAVAILABLE_SENTINEL] from [evaluate]
 * as "could not run this command at all" (bridge extension not installed
 * yet, or no port open for this session), not as "the page has no content"
 * -- those are different situations and conflating them lets the agent
 * believe a tap/type/read succeeded when nothing happened.
 */
object GeckoJs {
    @Suppress("UNCHECKED_CAST")
    fun evaluateAsync(session: GeckoSession, script: String): GeckoResult<String>? {
        return try {
            val method = session.javaClass.methods.firstOrNull { m ->
                m.name == "evaluateJS" && m.parameterTypes.size == 1
            } ?: return null
            method.invoke(session, script) as? GeckoResult<String>
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * @return the script's result, or [UNAVAILABLE_SENTINEL] if neither the
     * WebExtension bridge nor a reflective evaluateJS fallback is available
     * on this build/session. Callers must check for this sentinel
     * explicitly rather than treating a blank result as "ran successfully
     * with no output."
     */
    suspend fun evaluate(session: GeckoSession, script: String): String {
        if (GeckoExtensionBridge.isReady) {
            val out = GeckoExtensionBridge.send(session, "execute_js", mapOf("code" to script))
            if (out != UNAVAILABLE_SENTINEL) return out
        }
        val result = evaluateAsync(session, script) ?: return UNAVAILABLE_SENTINEL
        return suspendCancellableCoroutine { cont ->
            result.accept { value ->
                if (cont.isActive) cont.resume(value?.toString()?.trim()?.removeSurrounding("\"") ?: "")
            }
            try {
                result.exceptionally { ex ->
                    if (cont.isActive) cont.resume("ERR:${ex.message}")
                    GeckoResult.fromValue(null as String?)
                }
            } catch (_: Throwable) {
            }
        }
    }

    const val UNAVAILABLE_SENTINEL = "ERR:JS_EVAL_UNAVAILABLE"
}
