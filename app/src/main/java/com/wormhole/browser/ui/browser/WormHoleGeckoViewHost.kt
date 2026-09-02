package com.wormhole.browser.ui.browser

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.wormhole.browser.core.browser.NavigationUrls
import com.wormhole.browser.core.browser.Tab
import com.wormhole.browser.core.gecko.EngineCallbacks
import com.wormhole.browser.core.gecko.GeckoScrollTracker
import com.wormhole.browser.core.gecko.GeckoSessionPool
import com.wormhole.browser.core.gecko.GeckoToolbarChrome
import com.wormhole.browser.core.gecko.GeckoToolbarChromeState
import com.wormhole.browser.core.gecko.pageLoadFailureOf
import com.wormhole.browser.ui.theme.HighRefreshRate
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import com.wormhole.browser.core.permissions.AndroidPermissionRequests
import org.mozilla.geckoview.GeckoView
import com.wormhole.browser.core.gecko.NestedGeckoView
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse

@Composable
fun WormHoleGeckoViewHost(
    tab: Tab,
    sessionPool: GeckoSessionPool,
    callbacks: EngineCallbacks,
    dynamicToolbarMaxHeightPx: Int,
    toolbarTranslationYPx: Float,
    minReservedBottomPx: Int = 0,
    @Suppress("UNUSED_PARAMETER") topClippingPx: Int = 0,
    popupBlockingEnabled: Boolean = true,
    onScroll: (scrollDeltaY: Int, scrollY: Int, isScrollable: Boolean) -> Unit = { _, _, _ -> },
    onScrollSettled: () -> Unit = {},
    // Bumped by the caller (e.g. every time the tab switcher is opened) to
    // request a fresh thumbnail of whatever is currently on screen. Without
    // this, TabThumbnailCache.capture only ever ran on a tab-to-tab switch
    // or when this host left composition entirely -- since the switcher
    // overlay is drawn on top rather than removing this host, the active
    // tab's own thumbnail was never refreshed while you were looking at it,
    // so it opened the switcher and still showed a stale/placeholder card
    // for the tab you were just on.
    thumbnailCaptureRequest: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val latestCallbacks by rememberUpdatedState(callbacks)
    val latestOnScroll by rememberUpdatedState(onScroll)
    val latestOnScrollSettled by rememberUpdatedState(onScrollSettled)
    val latestTab by rememberUpdatedState(tab)
    val latestPopupBlockingEnabled by rememberUpdatedState(popupBlockingEnabled)
    val latestThumbnailCaptureRequest by rememberUpdatedState(thumbnailCaptureRequest)
    var lastHandledCaptureRequest by remember(tab.id) { mutableStateOf(0) }

    val handle = remember(tab.id, tab.isIncognito) {
        sessionPool.getOrCreateHandle(context, tab.id, privateMode = tab.isIncognito)
    }
    val session = handle.session
    val painted = remember(tab.id) { mutableStateOf(false) }

    DisposableEffect(session, tab.id) {
        var lastSeenUrl = handle.lastCommittedUrl.ifBlank { tab.url }
        var scrollTracker: GeckoScrollTracker? = null

        fun commitUrl(url: String) {
            if (url.isBlank() || NavigationUrls.isAboutBlank(url)) return
            lastSeenUrl = url
            sessionPool.markCommitted(tab.id, url)
            latestCallbacks.onUrlChanged(tab.id, url)
        }

        fun emitNavigation(canGoBack: Boolean? = null, canGoForward: Boolean? = null) {
            val (back, forward) = sessionPool.updateNavigation(tab.id, canGoBack, canGoForward)
            latestCallbacks.onNavigationStateChanged(tab.id, back, forward)
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                runCatching {
                    val activity = context as? android.app.Activity
                    val token = activity?.currentFocus?.windowToken
                        ?: activity?.window?.decorView?.windowToken
                    if (token != null) {
                        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                            as android.view.inputmethod.InputMethodManager
                        imm.hideSoftInputFromWindow(token, 0)
                    }
                    activity?.currentFocus?.clearFocus()
                }
                if (url.isBlank() || NavigationUrls.isAboutBlank(url)) return
                lastSeenUrl = url
                sessionPool.markRequested(tab.id, url)
                latestCallbacks.onPageStarted(tab.id, url)
                scrollTracker?.reset()
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                val url = lastSeenUrl.ifBlank { handle.lastCommittedUrl }.ifBlank { latestTab.url }
                if (url.isNotBlank() && !NavigationUrls.isAboutBlank(url)) {
                    sessionPool.markCommitted(tab.id, url)
                    latestCallbacks.onPageFinished(tab.id, url)
                }
                PullRefresh.finish(tab.id)
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                latestCallbacks.onProgressChanged(tab.id, progress)
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
            ) {
                val url = lastSeenUrl.ifBlank { latestTab.url }
                if (url.isNotBlank()) latestCallbacks.onUrlChanged(tab.id, url)
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (title != null) latestCallbacks.onTitleChanged(tab.id, title)
            }

            override fun onCrash(session: GeckoSession) {
                latestCallbacks.onRendererCrashed(tab.id)
            }

            override fun onKill(session: GeckoSession) {
                latestCallbacks.onRendererCrashed(tab.id)
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val uri = response.uri ?: return
                val headers = response.headers
                val mime = headers["Content-Type"] ?: "application/octet-stream"
                val disposition = headers["Content-Disposition"] ?: ""
                val length = headers["Content-Length"]?.toLongOrNull() ?: -1L
                latestCallbacks.onDownloadRequested(
                    tab.id,
                    uri,
                    "",
                    mime,
                    disposition,
                    length,
                )
            }

            override fun onCloseRequest(session: GeckoSession) {
            }

            override fun onFirstComposite(session: GeckoSession) {
                painted.value = true
            }

            override fun onFirstContentfulPaint(session: GeckoSession) {
                painted.value = true
            }

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement,
            ) {
                val url = element.linkUri ?: element.srcUri ?: return
                if (url.isBlank()) return
                latestCallbacks.onSiteContextMenu(
                    tab.id,
                    url,
                    element.title?.takeIf { it.isNotBlank() } ?: url,
                )
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (!url.isNullOrBlank()) {
                    commitUrl(url)
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                emitNavigation(canGoBack = canGoBack)
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                emitNavigation(canGoForward = canGoForward)
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                val uri = request.uri.orEmpty()
                if (uri.isBlank() ||
                    uri.startsWith("http://") ||
                    uri.startsWith("https://") ||
                    uri.startsWith("about:") ||
                    uri.startsWith("blob:") ||
                    uri.startsWith("data:")
                ) {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
                if (latestCallbacks.shouldOverrideUrl(tab.id, uri)) {
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onSubframeLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val newTabId = latestCallbacks.onNewWindowRequested(tab.id, uri)
                    ?: return GeckoResult.fromValue(null)
                val child = sessionPool.getOrCreateHandle(context, newTabId, privateMode = latestTab.isIncognito)
                if (uri.isNotBlank() && !NavigationUrls.isAboutBlank(uri)) {
                    sessionPool.markRequested(newTabId, uri)
                }
                return GeckoResult.fromValue(child.session)
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError,
            ): GeckoResult<String>? {
                val isMainFrame = uri.isNullOrBlank() || uri == lastSeenUrl || uri == latestTab.url
                // Security-category errors (bad/expired/mismatched cert, untrusted
                // issuer) previously fell through to onReceivedError only, so
                // onSslErrorReceived/SslWarningSheet -- fully built, but never
                // triggered by anything -- silently never appeared; the user only
                // ever saw GeckoView's own built-in interstitial with no path back
                // into this app's warning UI.
                if (isMainFrame && !uri.isNullOrBlank() && error.category == WebRequestError.ERROR_CATEGORY_SECURITY) {
                    latestCallbacks.onSslErrorReceived(
                        tabId = tab.id,
                        url = uri,
                        primaryErrorCode = error.code,
                        onProceed = { session.loadUri(uri) },
                        onCancel = {
                            if (sessionPool.updateNavigation(tab.id).first) session.goBack() else latestCallbacks.onReceivedError(tab.id, error.toString(), true)
                        },
                    )
                    return null
                }
                val failure = pageLoadFailureOf(error, uri ?: latestTab.url)
                latestCallbacks.onReceivedError(
                    tabId = tab.id,
                    errorDescription = failure.title,
                    isMainFrame = isMainFrame,
                    failedUrl = failure.url,
                    title = failure.title,
                    hint = failure.hint,
                )
                return null
            }
        }

        session.historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onHistoryStateChange(
                session: GeckoSession,
                historyList: GeckoSession.HistoryDelegate.HistoryList,
            ) {
                val canBack = historyList.currentIndex > 0
                val canForward = historyList.currentIndex < historyList.size - 1
                emitNavigation(canGoBack = canBack, canGoForward = canForward)
            }
        }

        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onPopupPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.PopupPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                // Without this override, GeckoView's default popup handling runs
                // (usually allow), so the popup-blocking setting had no effect at
                // all -- window.open() calls went straight through regardless of
                // what the user chose in Settings.
                val allowed = !latestPopupBlockingEnabled
                return GeckoResult.fromValue(prompt.confirm(if (allowed) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
            }

            override fun onAlertPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AlertPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return showJsAlert(context, prompt.title, prompt.message) { prompt.dismiss() }
            }

            override fun onButtonPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ButtonPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return showJsConfirm(context, prompt.title, prompt.message) { ok ->
                    if (ok) prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE)
                    else prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE)
                }
            }

            override fun onTextPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.TextPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return showJsPrompt(context, prompt.title, prompt.message, prompt.defaultValue) { value ->
                    if (value == null) prompt.dismiss() else prompt.confirm(value)
                }
            }
        }

        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<out String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                val needed = permissions?.filterNotNull()?.toTypedArray() ?: emptyArray()
                if (needed.isEmpty()) {
                    callback.grant()
                    return
                }
                AndroidPermissionRequests.request(needed) { granted ->
                    if (granted) callback.grant() else callback.reject()
                }
            }

            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int> {
                val result = GeckoResult<Int>()
                val origin = perm.uri ?: lastSeenUrl
                when (perm.permission) {
                    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> {
                        latestCallbacks.onGeolocationPermissionRequested(
                            tab.id,
                            origin,
                            onAllow = { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW) },
                            onDeny = { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) },
                        )
                    }
                    else -> result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT)
                }
                return result
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) {
                val resources = buildList {
                    if (!video.isNullOrEmpty()) add(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    if (!audio.isNullOrEmpty()) add(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                }
                latestCallbacks.onMediaPermissionRequested(
                    tab.id,
                    uri,
                    resources,
                    onGrant = { granted ->
                        val videoSource = if (granted.contains(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                            video?.firstOrNull()
                        } else {
                            null
                        }
                        val audioSource = if (granted.contains(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                            audio?.firstOrNull()
                        } else {
                            null
                        }
                        callback.grant(videoSource, audioSource)
                    },
                    onDeny = { callback.reject() },
                )
            }
        }

        scrollTracker = GeckoScrollTracker(
            session = session,
            onScroll = { delta, y, scrollable ->
                PullRefresh.scrollY = y
                latestOnScroll(delta, y, scrollable)
            },
            onScrollSettled = { latestOnScrollSettled() },
        )
        scrollTracker.start()

        onDispose {
            scrollTracker?.stop()
        }
    }

    fun attachSession(view: GeckoView, next: GeckoSession) {
        if (view.session !== next) {
            // This view is about to start showing a different tab's session.
            // Grab a preview of whatever is on screen right now before we swap
            // it out, so the tab switcher reflects the outgoing tab's last
            // state. onRelease alone doesn't cover this: switching between two
            // non-null tabs never removes this AndroidView from composition,
            // it just calls attachSession again with a new session.
            val outgoingTabId = sessionPool.tabIdForSession(view.session)
            if (outgoingTabId != null && outgoingTabId != tab.id) {
                com.wormhole.browser.core.webview.TabThumbnailCache.capture(outgoingTabId, view)
            }
            runCatching { view.releaseSession() }
            runCatching { view.setDynamicToolbarMaxHeight(0) }
            runCatching { view.setVerticalClipping(0) }
            // setSession opens the session if it is still closed. Never call
            // session.open() here — an already-open session must only be attached.
            runCatching { view.setSession(next) }
        }
        runCatching { next.setActive(true) }
        runCatching { next.setFocused(true) }
    }

    fun loadIfNeeded() {
        val url = latestTab.url
        if (url.isBlank() || NavigationUrls.isAboutBlank(url)) return
        if (sessionPool.needsAttachLoad(tab.id, url)) {
            sessionPool.markRequested(tab.id, url)
            session.loadUri(url)
        }
    }

    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                val geckoView = NestedGeckoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                    onVerticalDrag = { dy ->
                        PullRefresh.onDrag(tab.id, dy) {
                            runCatching { session.reload() }
                        }
                    }
                    isFocusable = true
                    isFocusableInTouchMode = true
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                    setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
                    runCatching { setDynamicToolbarMaxHeight(0) }
                    runCatching { setVerticalClipping(0) }
                    HighRefreshRate.applyToView(this)
                    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            HighRefreshRate.applyToView(v)
                            post {
                                attachSession(this@apply, session)
                                if (width > 0 && height > 0) loadIfNeeded()
                            }
                        }
                        override fun onViewDetachedFromWindow(v: View) = Unit
                    })
                }
                addView(geckoView)
                addView(PullRefresh.spinner(ctx, tab.id))
                tag = GeckoHostTag(geckoView)
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { container ->
            val host = container.tag as? GeckoHostTag ?: return@AndroidView
            attachSession(host.geckoView, session)
            if (painted.value) {
                GeckoToolbarChrome.apply(
                    view = host.geckoView,
                    state = host.chrome,
                    maxHeightPx = dynamicToolbarMaxHeightPx,
                    translationY = toolbarTranslationYPx,
                    minReservedPx = minReservedBottomPx,
                )
            }
            if (host.geckoView.width > 0 && host.geckoView.height > 0) {
                loadIfNeeded()
            }
            if (painted.value) {
                com.wormhole.browser.core.webview.TabThumbnailCache.capture(latestTab.id, host.geckoView, latestTab.url)
                lastHandledCaptureRequest = latestThumbnailCaptureRequest
            }
        },
        onRelease = { container ->
            val host = container.tag as? GeckoHostTag
            // Capture a preview for the tab switcher before detaching -- this
            // was previously never called anywhere (TabThumbnailCache.capture
            // was written for the old WebView host and dead since the
            // GeckoView migration), so TabGridCard always fell back to its
            // placeholder icon for every tab.
            if (host != null && painted.value) {
                com.wormhole.browser.core.webview.TabThumbnailCache.capture(tab.id, host.geckoView)
            }
            // Detach from this view only. The tab session stays alive in the pool.
            runCatching { host?.geckoView?.releaseSession() }
            container.removeAllViews()
        },
    )
}

private class GeckoHostTag(
    val geckoView: GeckoView,
    val chrome: GeckoToolbarChromeState = GeckoToolbarChromeState(),
)

private fun showJsAlert(
    context: android.content.Context,
    title: String?,
    message: String?,
    dismiss: () -> GeckoSession.PromptDelegate.PromptResponse,
): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
    runCatching {
        AlertDialog.Builder(context)
            .setTitle(title?.ifBlank { "Page says" } ?: "Page says")
            .setMessage(message.orEmpty())
            .setPositiveButton(android.R.string.ok) { _, _ -> result.complete(dismiss()) }
            .setOnCancelListener { result.complete(dismiss()) }
            .show()
    }.onFailure { result.complete(dismiss()) }
    return result
}

private fun showJsConfirm(
    context: android.content.Context,
    title: String?,
    message: String?,
    confirm: (Boolean) -> GeckoSession.PromptDelegate.PromptResponse,
): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
    runCatching {
        AlertDialog.Builder(context)
            .setTitle(title?.ifBlank { "Page says" } ?: "Page says")
            .setMessage(message.orEmpty())
            .setPositiveButton(android.R.string.ok) { _, _ -> result.complete(confirm(true)) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.complete(confirm(false)) }
            .setOnCancelListener { result.complete(confirm(false)) }
            .show()
    }.onFailure { result.complete(confirm(false)) }
    return result
}

private fun showJsPrompt(
    context: android.content.Context,
    title: String?,
    message: String?,
    defaultValue: String?,
    confirm: (String?) -> GeckoSession.PromptDelegate.PromptResponse,
): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
    runCatching {
        val input = EditText(context).apply { setText(defaultValue.orEmpty()) }
        AlertDialog.Builder(context)
            .setTitle(title?.ifBlank { "Page says" } ?: "Page says")
            .setMessage(message.orEmpty())
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.complete(confirm(input.text?.toString().orEmpty())) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.complete(confirm(null)) }
            .setOnCancelListener { result.complete(confirm(null)) }
            .show()
    }.onFailure { result.complete(confirm(null)) }
    return result
}

/** Pull-down at the top of a page reloads it, like Chrome / Firefox. */
private object PullRefresh {
    @Volatile var scrollY: Int = 0
    private var pulled = 0
    private var refreshingTab: String? = null
    private val bars = java.util.concurrent.ConcurrentHashMap<String, ProgressBar>()

    fun spinner(context: android.content.Context, tabId: String): ProgressBar {
        val bar = ProgressBar(context).apply {
            isIndeterminate = true
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = (12 * context.resources.displayMetrics.density).toInt() }
        }
        bars[tabId] = bar
        return bar
    }

    fun onDrag(tabId: String, dy: Int, reload: () -> Unit) {
        if (refreshingTab != null) return
        // NestedGeckoView dy is lastY - event.y: finger down => negative.
        if (scrollY > 8 || dy >= 0) {
            pulled = 0
            return
        }
        pulled += -dy
        if (pulled > 140) {
            pulled = 0
            refreshingTab = tabId
            bars[tabId]?.visibility = View.VISIBLE
            reload()
        }
    }

    fun finish(tabId: String) {
        if (refreshingTab == tabId || refreshingTab == null) {
            bars[tabId]?.visibility = View.GONE
            refreshingTab = null
            pulled = 0
        }
    }
}
