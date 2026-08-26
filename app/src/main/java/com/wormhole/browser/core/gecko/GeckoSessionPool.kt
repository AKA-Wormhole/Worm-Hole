package com.wormhole.browser.core.gecko

import android.content.Context
import com.wormhole.browser.core.browser.NavigationUrls
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import java.util.concurrent.ConcurrentHashMap

/**
 * One [GeckoSession] per tab id, plus the last requested / committed URL so the
 * Compose host never re-loads a page just because Amazon (etc.) canonicalizes
 * www vs non-www.
 */
class GeckoSessionPool {
    data class Handle(
        val session: GeckoSession,
        @Volatile var lastRequestedUrl: String = "",
        @Volatile var lastCommittedUrl: String = "",
        @Volatile var canGoBack: Boolean = false,
        @Volatile var canGoForward: Boolean = false,
    )

    private val sessions = ConcurrentHashMap<String, Handle>()

    fun getOrCreate(context: Context, tabId: String, privateMode: Boolean = false): GeckoSession {
        return getOrCreateHandle(context, tabId, privateMode).session
    }

    fun getOrCreateHandle(context: Context, tabId: String, privateMode: Boolean = false): Handle {
        sessions[tabId]?.let { return it }
        val runtime = GeckoRuntimeHolder.get(context)
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .useTrackingProtection(true)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .suspendMediaWhenInactive(true)
            .build()
        val session = GeckoSession(settings)
        if (!session.isOpen) {
            runCatching { session.open(runtime) }.onFailure {
                android.util.Log.w("GeckoSessionPool", "session.open failed for tab $tabId", it)
            }
        }
        // Wire the page-bridge message delegate for this session so
        // GeckoExtensionBridge.send() has a port to talk to as soon as the
        // content script connects (happens automatically once the bundled
        // extension's content script loads on the first page).
        GeckoExtensionBridge.attach(session)
        val handle = Handle(session)
        sessions[tabId] = handle
        return handle
    }

    fun get(tabId: String): GeckoSession? = sessions[tabId]?.session

    /** Reverse lookup used to identify which tab a live [GeckoSession] belongs to. */
    fun tabIdForSession(session: GeckoSession?): String? {
        if (session == null) return null
        return sessions.entries.firstOrNull { it.value.session === session }?.key
    }

    /**
     * Toggle desktop vs mobile browsing for a tab. Uses GeckoView's real
     * user-agent + viewport modes (not just a viewport meta hack), then reloads
     * so the site re-negotiates layout and content.
     */
    fun setDesktopMode(tabId: String, enabled: Boolean): Boolean {
        val handle = sessions[tabId] ?: return false
        return try {
            val settings = handle.session.settings
            val ua = if (enabled) {
                GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            } else {
                GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            }
            val vp = if (enabled) {
                GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            } else {
                GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            }
            // Prefer property setters; fall back to setUserAgentMode/setViewportMode
            // for GeckoView builds that only expose methods.
            try {
                settings.userAgentMode = ua
                settings.viewportMode = vp
            } catch (_: Throwable) {
                try {
                    settings.javaClass.getMethod("setUserAgentMode", Int::class.javaPrimitiveType)
                        .invoke(settings, ua)
                    settings.javaClass.getMethod("setViewportMode", Int::class.javaPrimitiveType)
                        .invoke(settings, vp)
                } catch (_: Throwable) {
                    return false
                }
            }
            handle.session.reload()
            true
        } catch (_: Throwable) {
            false
        }
    }


    fun getHandle(tabId: String): Handle? = sessions[tabId]

    /**
     * User-initiated navigation. Always talks to Gecko if the session exists.
     * Returns false when the tab has no session yet (the host will attach-load).
     */
    fun requestLoad(tabId: String, url: String, force: Boolean = true): Boolean {
        val handle = sessions[tabId] ?: return false
        if (url.isBlank() || NavigationUrls.isAboutBlank(url)) return false
        if (!force && NavigationUrls.shouldSkipAutomaticLoad(url, handle.lastRequestedUrl, handle.lastCommittedUrl)) {
            return false
        }
        handle.lastRequestedUrl = url
        handle.session.loadUri(url)
        return true
    }

    fun needsAttachLoad(tabId: String, url: String): Boolean {
        if (url.isBlank() || NavigationUrls.isAboutBlank(url)) return false
        val handle = sessions[tabId] ?: return true
        return !NavigationUrls.shouldSkipAutomaticLoad(url, handle.lastRequestedUrl, handle.lastCommittedUrl)
    }

    fun markRequested(tabId: String, url: String) {
        sessions[tabId]?.lastRequestedUrl = url
    }

    fun markCommitted(tabId: String, url: String) {
        val handle = sessions[tabId] ?: return
        handle.lastCommittedUrl = url
        if (handle.lastRequestedUrl.isBlank()) {
            handle.lastRequestedUrl = url
        }
    }

    /** Stop the live page and park the session on about:blank so Home can take over. */
    fun goHome(tabId: String) {
        val handle = sessions[tabId] ?: return
        handle.lastRequestedUrl = ""
        handle.lastCommittedUrl = ""
        handle.canGoBack = false
        handle.canGoForward = false
        runCatching { handle.session.stop() }
        runCatching { handle.session.loadUri("about:blank") }
    }

    fun updateNavigation(
        tabId: String,
        canGoBack: Boolean? = null,
        canGoForward: Boolean? = null,
    ): Pair<Boolean, Boolean> {
        val handle = sessions[tabId] ?: return Pair(canGoBack ?: false, canGoForward ?: false)
        if (canGoBack != null) handle.canGoBack = canGoBack
        if (canGoForward != null) handle.canGoForward = canGoForward
        return handle.canGoBack to handle.canGoForward
    }

    fun remove(tabId: String) {
        sessions.remove(tabId)?.let { handle ->
            try {
                GeckoExtensionBridge.detach(handle.session)
                handle.session.close()
            } catch (_: Throwable) {
            }
        }
    }

    fun destroyAll() {
        sessions.keys.toList().forEach { remove(it) }
    }
}
