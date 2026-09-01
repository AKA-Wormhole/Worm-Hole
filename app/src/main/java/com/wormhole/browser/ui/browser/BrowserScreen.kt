@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.wormhole.browser.ui.browser

import com.wormhole.browser.core.settings.SearchEngine

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.wormhole.browser.core.permissions.AndroidPermissionRequests
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wormhole.browser.R
import com.wormhole.browser.core.ai.TranslateLanguage
import com.wormhole.browser.core.browser.AiRequestState
import com.wormhole.browser.core.browser.BrowserEvent
import com.wormhole.browser.core.browser.BrowserViewModel
import com.wormhole.browser.core.downloads.DownloadRepository
import com.wormhole.browser.core.security.BiometricAuthenticator
import com.wormhole.browser.core.browser.ExternalIntentLauncher
import com.wormhole.browser.core.browser.SpaceAccent
import com.wormhole.browser.ui.ai.AiSheet
import com.wormhole.browser.ui.onboarding.OnboardingScreen
import com.wormhole.browser.ui.settings.AboutScreen
import com.wormhole.browser.ui.settings.LogsScreen
import com.wormhole.browser.ui.downloads.DownloadsSheet
import com.wormhole.browser.ui.library.LibrarySheet
import com.wormhole.browser.ui.settings.SettingsScreen
import com.wormhole.browser.ui.theme.WormHoleMotion
import com.wormhole.browser.ui.theme.bouncyClickable

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = viewModel(),
    geckoSessionPool: com.wormhole.browser.core.gecko.GeckoSessionPool,
    onWebViewVisibleChanged: (Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val siteMenu by viewModel.siteContextMenu.collectAsState()
    val currentEngine by viewModel.searchEngine.collectAsState()
    val homeBackground by viewModel.homeBackground.collectAsState()
    val dynamicBackgroundEnabled by viewModel.dynamicBackgroundEnabled.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val assistantState by viewModel.assistantState.collectAsState()
    val aiWorking = assistantState is AiRequestState.Loading
    val shortcuts by viewModel.shortcuts.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val hasStoredRecentSearches by viewModel.hasStoredRecentSearches.collectAsState()
    val trackerBlockingEnabled by viewModel.trackerBlockingEnabled.collectAsState()
    val adBlockingEnabled by viewModel.adBlockingEnabled.collectAsState()
    val popupBlockingEnabled by viewModel.popupBlockingEnabled.collectAsState()
    val webDarkModeEnabled by viewModel.webDarkModeEnabled.collectAsState()
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val homepageUrl by viewModel.homepageUrl.collectAsState()

    val appIsDark = when (themeMode) {
        com.wormhole.browser.core.settings.ThemeMode.LIGHT -> false
        com.wormhole.browser.core.settings.ThemeMode.DARK -> true
        com.wormhole.browser.core.settings.ThemeMode.SYSTEM ->
            androidx.compose.foundation.isSystemInDarkTheme()
    }
    LaunchedEffect(webDarkModeEnabled, appIsDark) {
        // When the toggle is on, sites follow the app light/dark theme.
        // When off, sites stay in light mode.
        com.wormhole.browser.core.gecko.GeckoRuntimeHolder.setContentPrefersDark(
            dark = webDarkModeEnabled && appIsDark,
        )
    }

    val context = LocalContext.current
    val pendingAndroidPermission = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val androidPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.isNotEmpty() && result.values.all { it }
        pendingAndroidPermission.value?.invoke(granted)
        pendingAndroidPermission.value = null
    }
    androidx.compose.runtime.DisposableEffect(androidPermissionLauncher) {
        AndroidPermissionRequests.bind { permissions, onResult ->
            val already = permissions.all { perm ->
                androidx.core.content.ContextCompat.checkSelfPermission(context, perm) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (already) {
                onResult(true)
            } else {
                pendingAndroidPermission.value = onResult
                androidPermissionLauncher.launch(Array(permissions.size) { permissions[it] })
            }
        }
        onDispose { AndroidPermissionRequests.bind(null) }
    }

    val coroutineScope = rememberCoroutineScope()

    val fragmentActivity = context as? androidx.fragment.app.FragmentActivity

    var isCommandBarOpen by remember { mutableStateOf(false) }
    var commandBarQuery by remember { mutableStateOf("") }

    var commandBarMode by remember { mutableStateOf(CommandBarMode.SEARCH) }

    var aiAnswerQuery by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var showOpenSourceLicenses by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showPasskeys by remember { mutableStateOf(false) }

    var isPasskeysAuthenticated by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var libraryInitialTab by remember { mutableIntStateOf(0) }
    var isFindInPageOpen by remember { mutableStateOf(false) }
    var isPageToolsMenuOpen by remember { mutableStateOf(false) }

    var isHomeToolsMenuOpen by remember { mutableStateOf(false) }
    var isDesktopSiteEnabled by remember { mutableStateOf(false) }
    var isTranslateLanguageSheetOpen by remember { mutableStateOf(false) }
    var translateOffer by remember { mutableStateOf<com.wormhole.browser.core.gecko.PageTranslator.Detection?>(null) }
    var translateTarget by remember {
        mutableStateOf(com.wormhole.browser.core.ai.TranslateLanguages.ENGLISH)
    }
    var translateBusy by remember { mutableStateOf(false) }
    var pageTranslated by remember { mutableStateOf(false) }
    var translateMode by remember { mutableStateOf(com.wormhole.browser.core.gecko.PageTranslator.Mode.IN_PAGE) }
    var trendingSearches by remember { mutableStateOf<List<String>>(emptyList()) }
    val dismissedTranslateHosts = remember { mutableSetOf<String>() }
    var isAssistantSheetOpen by remember { mutableStateOf(false) }

    var isAiOpen by remember { mutableStateOf(false) }
    var isTabSwitcherOpen by remember { mutableStateOf(false) }
    var thumbnailCaptureRequest by remember { mutableStateOf(0) }
    LaunchedEffect(isTabSwitcherOpen) {
        if (isTabSwitcherOpen) thumbnailCaptureRequest++
    }

    var isIncognitoConsentPending by remember { mutableStateOf(false) }

    var pendingIncognitoSpaceId by remember { mutableStateOf<String?>(null) }
    val requestNewIncognitoTab: (String) -> Unit = { spaceId ->
        pendingIncognitoSpaceId = spaceId
        isIncognitoConsentPending = true
    }
    var pendingDownload by remember { mutableStateOf<BrowserEvent.DownloadRequested?>(null) }
    var webViewRecoveryRevision by remember { mutableStateOf(0) }

    // Tracks the most recent load failure per tab. GeckoView's onLoadError fires
    // when a navigation fails outright (DNS, TLS, connection refused, blocked
    // content, etc.) -- without this, the page never paints and the toolbar
    // still looks "loaded", leaving a blank/dark screen with no explanation.
    val tabLoadErrors = remember { mutableStateMapOf<String, com.wormhole.browser.core.gecko.PageLoadFailure>() }

    var pendingSslError by remember { mutableStateOf<BrowserEvent.SslErrorOccurred?>(null) }

    var pendingMediaPermission by remember { mutableStateOf<BrowserEvent.MediaPermissionRequested?>(null) }
    var pendingGeolocationPermission by remember { mutableStateOf<BrowserEvent.GeolocationPermissionRequested?>(null) }

    var mediaSiteConsent by remember { mutableStateOf<BrowserEvent.MediaPermissionRequested?>(null) }
    var geolocationSiteConsent by remember { mutableStateOf<BrowserEvent.GeolocationPermissionRequested?>(null) }

    BackHandler(
        enabled = isAiOpen || showSettings || showPasskeys || showDownloads ||
            showLibrary || isTabSwitcherOpen || isCommandBarOpen ||
            isTranslateLanguageSheetOpen || isAssistantSheetOpen || isFindInPageOpen ||
            isPageToolsMenuOpen || isHomeToolsMenuOpen || showAbout || showLogs ||
            showPrivacyPolicy || showTerms || showOpenSourceLicenses,
    ) {
        when {
            isAiOpen -> isAiOpen = false
            showPasskeys -> showPasskeys = false
            showAbout -> showAbout = false
            showLogs -> showLogs = false
            showPrivacyPolicy -> showPrivacyPolicy = false
            showTerms -> showTerms = false
            showOpenSourceLicenses -> showOpenSourceLicenses = false
            showSettings -> showSettings = false
            showDownloads -> showDownloads = false
            showLibrary -> showLibrary = false
            isTabSwitcherOpen -> isTabSwitcherOpen = false
            isTranslateLanguageSheetOpen -> isTranslateLanguageSheetOpen = false
            isAssistantSheetOpen -> isAssistantSheetOpen = false
            isFindInPageOpen -> isFindInPageOpen = false
            isPageToolsMenuOpen -> isPageToolsMenuOpen = false
            isHomeToolsMenuOpen -> isHomeToolsMenuOpen = false
            isCommandBarOpen -> isCommandBarOpen = false
        }
    }

    val activeWebViewCanGoBack = uiState.activeTab?.canGoBack == true
    BackHandler(enabled = activeWebViewCanGoBack) {
        uiState.activeTab?.id?.let { geckoSessionPool.get(it)?.goBack() }
    }

    var downloadToast by remember { mutableStateOf<com.wormhole.browser.core.downloads.DownloadStartResult?>(null) }

    var permissionRequestedDownload by remember { mutableStateOf<BrowserEvent.DownloadRequested?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val download = permissionRequestedDownload
        permissionRequestedDownload = null
        if (granted && download != null) {
            coroutineScope.launch {
                try {
                    downloadToast = DownloadRepository.start(
                        context = context,
                        url = download.url,
                        userAgent = download.userAgent,
                        contentDisposition = download.contentDisposition,
                        mimeType = download.mimeType,
                    )
                } catch (_: Throwable) {
                    downloadToast = null
                }
            }
        }

    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val pending = pendingMediaPermission
        pendingMediaPermission = null
        if (pending == null) return@rememberLauncherForActivityResult
        if (grants.values.all { it }) {

            mediaSiteConsent = pending
        } else {
            pending.onDeny()
        }
    }

    val geolocationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val pending = pendingGeolocationPermission
        pendingGeolocationPermission = null
        if (pending == null) return@rememberLauncherForActivityResult
        if (grants.values.all { it }) {
            geolocationSiteConsent = pending
        } else {
            pending.onDeny()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {  }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {  }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val hasNotificationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        trendingSearches = com.wormhole.browser.core.search.TrendingSearchesClient.load()
    }

    LaunchedEffect(dynamicBackgroundEnabled) {
        if (dynamicBackgroundEnabled) {
            val hasLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasLocationPermission) {
                locationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BrowserEvent.LaunchExternalApp -> ExternalIntentLauncher.launch(context, event.uri)
                is BrowserEvent.DownloadRequested -> pendingDownload = event
                is BrowserEvent.BlobDownloadReady -> {

                    downloadToast = DownloadRepository.saveBase64(
                        context = context,
                        fileName = event.fileName,
                        mimeType = event.mimeType,
                        base64Data = event.base64Data,
                    )
                }
                is BrowserEvent.BlobDownloadFailed -> Unit
                is BrowserEvent.LoadError -> {
                    tabLoadErrors[event.tabId] = com.wormhole.browser.core.gecko.PageLoadFailure(
                        url = event.url.ifBlank { uiState.tabs.firstOrNull { it.id == event.tabId }?.url.orEmpty() },
                        title = event.title,
                        hint = event.hint,
                    )
                }
                is BrowserEvent.SslErrorOccurred -> pendingSslError = event
                is BrowserEvent.MediaPermissionRequested -> {
                    val neededPermissions = event.resources.mapNotNull { resource ->
                        when (resource) {
                            android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                            android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                            else -> null
                        }
                    }.distinct()
                    val alreadyGranted = neededPermissions.isNotEmpty() && neededPermissions.all {
                        androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    when {
                        neededPermissions.isEmpty() -> event.onDeny()
                        alreadyGranted -> mediaSiteConsent = event
                        else -> {
                            pendingMediaPermission = event
                            mediaPermissionLauncher.launch(neededPermissions.toTypedArray())
                        }
                    }
                }
                is BrowserEvent.GeolocationPermissionRequested -> {
                    val neededPermissions = arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                    val alreadyGranted = neededPermissions.all {
                        androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (alreadyGranted) {
                        geolocationSiteConsent = event
                    } else {
                        pendingGeolocationPermission = event
                        geolocationPermissionLauncher.launch(neededPermissions)
                    }
                }
                is BrowserEvent.RendererCrashed -> {

                    geckoSessionPool.remove(event.tabId)
                    webViewRecoveryRevision++
                }
            }
        }
    }

    val activeTab = uiState.activeTab
    val onVoiceSearchResult: (String) -> Unit = { spoken ->
        commandBarQuery = spoken
        commandBarMode = CommandBarMode.SEARCH
        viewModel.recordTypedQueryIfSearch(spoken)
        val tab = activeTab ?: viewModel.newTab(spaceId = uiState.activeSpaceId)
        val resolved = viewModel.resolveInput(spoken)
        viewModel.updateTabUrl(tab.id, resolved)
        geckoSessionPool.requestLoad(tab.id, resolved)
        isCommandBarOpen = false
    }

    val isWebViewVisible = activeTab != null && activeTab.url.isNotBlank() && !isTabSwitcherOpen
    LaunchedEffect(isWebViewVisible) { onWebViewVisibleChanged(isWebViewVisible) }

    LaunchedEffect(activeTab?.id) { isDesktopSiteEnabled = false }
    LaunchedEffect(activeTab?.id, activeTab?.url, activeTab?.isLoading) {
        val tab = activeTab
        if (tab == null || tab.url.isBlank() || tab.isLoading) {
            if (tab?.isLoading == true) {
                translateOffer = null
                pageTranslated = false
                translateBusy = false
                translateMode = com.wormhole.browser.core.gecko.PageTranslator.Mode.IN_PAGE
            }
            return@LaunchedEffect
        }
        val host = runCatching { android.net.Uri.parse(tab.url).host }.getOrNull().orEmpty()
        if (host.isBlank() || host in dismissedTranslateHosts) {
            translateOffer = null
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(450)
        val session = geckoSessionPool.get(tab.id) ?: return@LaunchedEffect
        val detected = runCatching {
            com.wormhole.browser.core.gecko.PageTranslator.detectLanguage(session)
        }.getOrNull()
        if (detected != null && detected.code != "en") {
            translateOffer = detected
            if (!pageTranslated) {
                translateTarget = com.wormhole.browser.core.ai.TranslateLanguages.ENGLISH
            }
        } else {
            translateOffer = null
        }
    }
    // Clear any stale load-error banner for this tab as soon as a fresh
    // navigation starts (reload, new URL, link tap, etc).
    LaunchedEffect(activeTab?.id, activeTab?.isLoading) {
        if (activeTab != null && activeTab.isLoading) {
            tabLoadErrors.remove(activeTab.id)
        }
        if (activeTab != null && activeTab.isLoading == false) {
            // Page finished loading.
        }
    }

    // Gecko finder when Find opens.
    var findInPageController by remember { mutableStateOf<com.wormhole.browser.core.gecko.FindController?>(null) }
    LaunchedEffect(isFindInPageOpen, activeTab?.id) {
        if (!isFindInPageOpen) {
            findInPageController?.stop()
            findInPageController = null
            return@LaunchedEffect
        }
        repeat(30) {
            val tabId = activeTab?.id
            val session = tabId?.let { geckoSessionPool.get(it) }
            if (session != null) {
                val controller = findInPageController
                    ?: com.wormhole.browser.core.gecko.GeckoFindController(session)
                findInPageController = controller
                controller.start()
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(50)
        }
    }
    DisposableEffect(activeTab?.id) {
        onDispose {
            findInPageController?.stop()
            findInPageController = null
        }
    }

    val density = LocalDensity.current
    var bottomBarHeightPx by remember { mutableStateOf(0) }
    val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() }

    val topInsetPx = WindowInsets.statusBars
        .getTop(density)
        .coerceAtLeast(
            WindowInsets.displayCutout.getTop(density),
        )
    // System navigation bar stays visible and must never be covered by the WebView.
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
    // Soft keyboard (IME). With edge-to-edge + setDecorFitsSystemWindows(false),
    // adjustResize alone does not shrink the Gecko surface — we must apply the
    // IME inset ourselves or site inputs stay behind the keyboard.
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val isKeyboardOpen = imeBottomPx > navBarBottomPx
    val animatedImeBottomPx by androidx.compose.animation.core.animateIntAsState(
        targetValue = if (isKeyboardOpen) imeBottomPx else 0,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
        label = "imeBottom",
    )

    // Iceraven/Mozilla dynamic bottom toolbar (ViewYTranslator + clipping model):
    // - Bar follows scroll 1:1 while dragging.
    // - On finger-up it snaps fully shown or hidden; Gecko clipping follows so
    //   the webpage (ChatGPT composer, etc.) snaps with the chrome.
    val dynamicToolbar = remember { com.wormhole.browser.core.webview.DynamicToolbarController() }
    val toolbarScrollScope = rememberCoroutineScope()
    var toolbarOffsetPx by remember { mutableFloatStateOf(0f) }
    var toolbarScrollY by remember { mutableIntStateOf(0) }
    var toolbarSettleJob by remember { mutableStateOf<Job?>(null) }
    var toolbarSnapTarget by remember { mutableFloatStateOf(Float.NaN) }

    fun cancelToolbarSettle() {
        toolbarSettleJob?.cancel()
        toolbarSettleJob = null
        toolbarSnapTarget = Float.NaN
    }

    fun animateToolbarTo(target: Float) {
        if (bottomBarHeightPx <= 0) {
            toolbarOffsetPx = 0f
            return
        }
        val bounded = target.coerceIn(0f, bottomBarHeightPx.toFloat())
        if (toolbarSettleJob?.isActive == true && kotlin.math.abs(toolbarSnapTarget - bounded) < 0.5f) {
            return
        }
        val start = toolbarOffsetPx
        if (kotlin.math.abs(start - bounded) < 0.5f) {
            toolbarOffsetPx = bounded
            dynamicToolbar.syncTranslation(bounded)
            return
        }
        cancelToolbarSettle()
        toolbarSnapTarget = bounded
        toolbarSettleJob = toolbarScrollScope.launch {
            try {
                animate(
                    initialValue = start,
                    targetValue = bounded,
                    animationSpec = WormHoleMotion.chrome(),
                ) { value, _ ->
                    toolbarOffsetPx = value
                    dynamicToolbar.syncTranslation(value)
                }
            } finally {
                toolbarOffsetPx = bounded
                dynamicToolbar.syncTranslation(bounded)
                toolbarSnapTarget = Float.NaN
            }
        }
    }

    fun applyToolbarDrag(scrollDeltaY: Int, scrollY: Int) {
        toolbarScrollY = scrollY
        dynamicToolbar.syncTranslation(toolbarOffsetPx)
        val next = dynamicToolbar.onScrollDelta(scrollDeltaY, scrollY)
        if (dynamicToolbar.lastIgnored) return
        cancelToolbarSettle()
        toolbarOffsetPx = next
    }

    fun snapToolbarToRest() {
        if (bottomBarHeightPx <= 0) return
        val target = dynamicToolbar.snapTarget(toolbarScrollY)
        animateToolbarTo(target)
        dynamicToolbar.endGesture()
    }

    LaunchedEffect(bottomBarHeightPx) {
        dynamicToolbar.updateToolbarHeight(bottomBarHeightPx)
        toolbarOffsetPx = toolbarOffsetPx.coerceIn(0f, bottomBarHeightPx.toFloat().coerceAtLeast(0f))
    }
    LaunchedEffect(activeTab?.id) {
        cancelToolbarSettle()
        dynamicToolbar.forceExpand()
        toolbarOffsetPx = 0f
    }
    LaunchedEffect(isPageToolsMenuOpen, isHomeToolsMenuOpen, isFindInPageOpen, isCommandBarOpen) {
        if (isPageToolsMenuOpen || isHomeToolsMenuOpen || isFindInPageOpen || isCommandBarOpen) {
            cancelToolbarSettle()
            val start = toolbarOffsetPx
            dynamicToolbar.forceExpand()
            if (start <= 0.5f) {
                toolbarOffsetPx = 0f
            } else {
                toolbarSettleJob = toolbarScrollScope.launch {
                    animate(
                        initialValue = start,
                        targetValue = 0f,
                        animationSpec = WormHoleMotion.chrome(),
                    ) { value, _ ->
                        toolbarOffsetPx = value
                        dynamicToolbar.syncTranslation(value)
                    }
                    toolbarOffsetPx = 0f
                    dynamicToolbar.syncTranslation(0f)
                }
            }
        }
    }

    // Mozilla EngineViewClippingBehavior equivalent for WebView:
    // bottom inset = visible toolbar height so position:fixed bottom UI (ChatGPT, Copilot)
    // sits above the app bar. Floored at system nav so content never draws under it.
    // Iceraven EngineViewClippingBehavior: clipping = -toolbar.translationY
    val estimatedToolbarPx = with(density) { 110.dp.toPx() }.toInt() + navBarBottomPx
    val dynamicToolbarMaxHeightPx = bottomBarHeightPx.takeIf { it > 0 } ?: estimatedToolbarPx
    // While the keyboard is open: pad the Gecko surface by the IME height so the
    // page reflows above the keyboard, zero dynamic-toolbar clipping (the pad
    // already reserves the space), and slide the app bottom bar fully off-screen.
    val geckoDynamicToolbarMaxPx = if (isKeyboardOpen) 0 else dynamicToolbarMaxHeightPx
    val geckoToolbarTranslationYPx = if (isKeyboardOpen) 0f else toolbarOffsetPx
    val geckoMinReservedBottomPx = if (isKeyboardOpen) 0 else navBarBottomPx

    if (onboardingCompleted == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    if (onboardingCompleted == false) {
        OnboardingScreen(
            currentEngine = currentEngine,
            onEngineSelected = viewModel::setSearchEngine,
            onFinished = { viewModel.setOnboardingCompleted(true) },
            onPrivacyPolicyClick = { showPrivacyPolicy = true },
            onTermsClick = { showTerms = true },
        )
        if (showPrivacyPolicy) {
            com.wormhole.browser.ui.settings.PrivacyPolicyScreen(onBack = { showPrivacyPolicy = false })
        }
        if (showTerms) {
            com.wormhole.browser.ui.settings.TermsOfServiceScreen(onBack = { showTerms = false })
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val showStatusBarStrip = activeTab?.url?.isNotBlank() == true
        if (showStatusBarStrip) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(with(density) { topInsetPx.toDp() })
                    .background(MaterialTheme.colorScheme.background)
                    .zIndex(30f),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (aiWorking) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    } else Modifier
                ),
        ) {

        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (activeTab != null) {
                // Do not wrap GeckoView in AnimatedContent/scale — that destroys the
                // TextureView surface and leaves a permanent white page.
                if (activeTab.url.isNotBlank()) {
                        val applyToolbarScroll: (Int, Int) -> Unit = applyScroll@{ scrollDeltaY, scrollY ->
                            if (isPageToolsMenuOpen || isFindInPageOpen || isCommandBarOpen) return@applyScroll
                            applyToolbarDrag(scrollDeltaY, scrollY)
                        }
                        WormHoleGeckoViewHost(
                            tab = activeTab,
                            sessionPool = geckoSessionPool,
                            callbacks = viewModel,
                            dynamicToolbarMaxHeightPx = geckoDynamicToolbarMaxPx,
                            toolbarTranslationYPx = geckoToolbarTranslationYPx,
                            minReservedBottomPx = geckoMinReservedBottomPx,
                            topClippingPx = 0,
                            popupBlockingEnabled = popupBlockingEnabled,
                            thumbnailCaptureRequest = thumbnailCaptureRequest,
                            onScroll = onScroll@{ scrollDeltaY, scrollY, isScrollable ->
                                if (isKeyboardOpen) return@onScroll
                                if (!isScrollable && scrollY <= 8) {
                                    cancelToolbarSettle()
                                    dynamicToolbar.forceExpand()
                                    toolbarOffsetPx = 0f
                                    return@onScroll
                                }
                                applyToolbarScroll(scrollDeltaY, scrollY)
                            },
                            onScrollSettled = { if (!isKeyboardOpen) snapToolbarToRest() },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = with(density) { topInsetPx.toDp() })
                                .padding(
                                    bottom = with(density) { animatedImeBottomPx.toDp() },
                                ),
                        )

                        val activeError = tabLoadErrors[activeTab.id]
                        if (activeError != null && !activeTab.isLoading) {
                            LoadErrorOverlay(
                                failure = activeError,
                                fallbackUrl = activeTab.url,
                                modifier = Modifier.padding(bottom = bottomBarHeight),
                                onRetry = {
                                    val retryUrl = activeError.url.ifBlank { activeTab.url }
                                    tabLoadErrors.remove(activeTab.id)
                                    if (retryUrl.isNotBlank()) {
                                        geckoSessionPool.requestLoad(activeTab.id, retryUrl, force = true)
                                    } else {
                                        geckoSessionPool.get(activeTab.id)?.reload()
                                    }
                                },
                            )
                        }
                    } else if (activeTab.isIncognito) {
                        IncognitoHomeSurface(
                            tabCount = uiState.tabs.count { it.isIncognito && it.url.isNotBlank() },
                            onSearchClick = {
                                commandBarQuery = ""
                                commandBarMode = CommandBarMode.SEARCH
                                isCommandBarOpen = true
                            },
                            onVoiceSearch = onVoiceSearchResult,
                            onTabSwitcherClick = { isTabSwitcherOpen = true },
                            onMenuClick = { isHomeToolsMenuOpen = true },
                            isMenuOpen = isHomeToolsMenuOpen,
                            onMenuDismiss = { isHomeToolsMenuOpen = false },
                            onDownloadsClick = { isHomeToolsMenuOpen = false; showDownloads = true },
                            onLibraryClick = { isHomeToolsMenuOpen = false; libraryInitialTab = 0; showLibrary = true },
                            onHistoryClick = { isHomeToolsMenuOpen = false; libraryInitialTab = 1; showLibrary = true },
                            onPasswordsClick = { isHomeToolsMenuOpen = false; showPasskeys = true },
                            onSettingsClick = { isHomeToolsMenuOpen = false; showSettings = true },
                            onNewIncognitoTabClick = {
                                isHomeToolsMenuOpen = false
                                requestNewIncognitoTab(uiState.activeSpaceId)
                            },
                            onAssistantClick = { isHomeToolsMenuOpen = false; isAiOpen = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        NewTabSurface(
                            activeSpace = uiState.activeSpace,
                            shortcuts = shortcuts,
                            history = viewModel.history.collectAsState().value,
                            onCommandBarRequested = {
                                commandBarQuery = ""
                                commandBarMode = CommandBarMode.SEARCH
                                isCommandBarOpen = true
                            },
                            onVoiceSearch = onVoiceSearchResult,
                            onShortcutClick = { shortcut ->
                                activeTab?.let { tab ->
                                    geckoSessionPool.requestLoad(tab.id, shortcut.url)
                                    viewModel.updateTabUrl(tab.id, shortcut.url)
                                }
                            },
                            onShortcutRemove = { shortcut -> viewModel.removeShortcut(shortcut.url) },
                            onAddShortcut = { title, url -> viewModel.addShortcut(title, url) },
                            trendingSearches = trendingSearches,
                            onTrendingSearch = { term ->
                                val tab = activeTab ?: viewModel.newTab(spaceId = uiState.activeSpaceId)
                                val resolved = viewModel.resolveInput(term)
                                viewModel.updateTabUrl(tab.id, resolved)
                                geckoSessionPool.requestLoad(tab.id, resolved)
                            },
                            onAskWormHoleClick = { isAiOpen = true },
                            onHistoryClick = { entry ->
                                val tab = activeTab ?: viewModel.newTab(spaceId = uiState.activeSpaceId)
                                geckoSessionPool.requestLoad(tab.id, entry.url)
                                viewModel.updateTabUrl(tab.id, entry.url)
                            },
                            bookmarks = bookmarks,
                            onToggleBookmark = { title, url ->
                                if (bookmarks.any { it.url == url }) viewModel.removeBookmark(url)
                                else viewModel.addBookmark(
                                    com.wormhole.browser.core.browser.Tab(
                                        title = title,
                                        url = url,
                                        displayUrl = url,
                                        isBlankTab = false,
                                    ),
                                )
                            },
                            searchEngine = currentEngine,
                            onEngineSelected = viewModel::setSearchEngine,
                            homeBackground = homeBackground,
                            tabCount = uiState.tabs.count { it.url.isNotBlank() },
                            onTabSwitcherClick = { isTabSwitcherOpen = true },
                            canGoBack = activeTab?.canGoBack == true,
                            canGoForward = activeTab?.canGoForward == true,
                            onBackClick = {
                                activeTab?.let { tab ->
                                    geckoSessionPool.get(tab.id)?.goBack()
                                }
                            },
                            onForwardClick = {
                                activeTab?.let { tab ->
                                    geckoSessionPool.get(tab.id)?.goForward()
                                }
                            },
                            isMenuOpen = isHomeToolsMenuOpen,
                            onMenuButtonClick = { isHomeToolsMenuOpen = true },
                            onMenuDismiss = { isHomeToolsMenuOpen = false },
                            onDownloadsClick = { isHomeToolsMenuOpen = false; showDownloads = true },
                            onLibraryClick = { isHomeToolsMenuOpen = false; libraryInitialTab = 0; showLibrary = true },
                            onOpenHistoryLibrary = { isHomeToolsMenuOpen = false; libraryInitialTab = 1; showLibrary = true },
                            onPasswordsClick = { isHomeToolsMenuOpen = false; showPasskeys = true },
                            onSettingsClick = { isHomeToolsMenuOpen = false; showSettings = true },
                            onNewIncognitoTabClick = {
                                isHomeToolsMenuOpen = false
                                requestNewIncognitoTab(uiState.activeSpaceId)
                            },

                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                if (activeTab?.isLoading == true) {

                    LinearProgressIndicator(
                        progress = { activeTab.loadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(2.dp),
                        color = uiState.activeSpace?.accent?.color ?: MaterialTheme.colorScheme.primary,
                    )
                }

                if (isFindInPageOpen) {
                    val controller = findInPageController
                    if (controller != null) {
                        FindInPageBar(
                            controller = controller,
                            onClose = {
                                controller.stop()
                                isFindInPageOpen = false
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = bottomBarHeight + 12.dp)
                                .fillMaxWidth()
                                .zIndex(8f),
                        )
                    }
                }

                // Always compose the bar while a page is open so bottomBarHeightPx stays
                // accurate. Visibility is driven purely by toolbarOffset (hide = slide down).
                if (activeTab?.url?.isNotBlank() == true) {
                BottomBar(
                    searchEngine = currentEngine,
                    onEngineSelected = viewModel::setSearchEngine,
                    isMenuOpen = isPageToolsMenuOpen,
                    isDesktopSiteEnabled = isDesktopSiteEnabled,
                    tabCount = uiState.tabs.count { it.url.isNotBlank() },
                    displayUrl = activeTab?.displayUrl.orEmpty(),
                    isSecure = activeTab?.isSecure == true,
                    onAddressBarClick = {
                        commandBarQuery = activeTab?.url.orEmpty()
                        commandBarMode = CommandBarMode.SEARCH
                        isCommandBarOpen = true
                    },
                    onVoiceSearchClick = onVoiceSearchResult,
                    onBackClick = {

                        activeTab?.let { tab ->
                            geckoSessionPool.get(tab.id)?.goBack()
                        }
                    },
                    onForwardClick = {
                        activeTab?.let { tab ->
                            geckoSessionPool.get(tab.id)?.goForward()
                        }
                    },
                    onReloadClick = { activeTab?.let { geckoSessionPool.get(it.id)?.reload() } },
                    onStopLoadingClick = { activeTab?.let { geckoSessionPool.get(it.id)?.stop() } },
                    isLoading = activeTab?.isLoading == true,
                    canGoBack = activeTab?.canGoBack == true,
                    canGoForward = activeTab?.canGoForward == true,
                    onTabSwitcherClick = { isTabSwitcherOpen = true },
                    onNewTabFromBarClick = {
                        viewModel.newTab(spaceId = uiState.activeSpaceId)
                    },
                    onHomeClick = {
                        activeTab?.let { tab ->
                            viewModel.goHome(tab.id)
                            geckoSessionPool.goHome(tab.id)
                            cancelToolbarSettle()
                            dynamicToolbar.forceExpand()
                            toolbarOffsetPx = 0f
                        }
                    },
                    onMenuButtonClick = { isPageToolsMenuOpen = true },
                    onMenuDismiss = { isPageToolsMenuOpen = false },
                    onDownloadsClick = {
                        showDownloads = true
                    },
                    onLibraryClick = {
                        libraryInitialTab = 0
                        showLibrary = true
                    },
                    onHistoryClick = {
                        libraryInitialTab = 1
                        showLibrary = true
                    },
                    onPasswordsClick = {
                        showPasskeys = true
                    },
                    onBookmarkClick = {
                        val tab = activeTab
                        if (tab == null || tab.url.isBlank()) {
                            android.widget.Toast.makeText(context, "Nothing to bookmark", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addBookmark(tab)
                            android.widget.Toast.makeText(context, "Bookmark saved", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAddShortcutClick = {
                        val tab = activeTab
                        if (tab == null || tab.url.isBlank()) {
                            android.widget.Toast.makeText(context, "Nothing to pin", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addShortcut(tab.title.ifBlank { tab.url }, tab.url)
                            android.widget.Toast.makeText(context, "Added to Shortcuts", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDuplicateTabClick = {
                        val tab = activeTab
                        if (tab == null) {
                            android.widget.Toast.makeText(context, "No tab to duplicate", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.duplicateTab(tab)
                            // Load the same URL into the new tab's session once it appears
                            android.widget.Toast.makeText(context, "Tab duplicated", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onReopenClosedTabClick = {
                        val hadClosed = uiState.recentlyClosedTabs.isNotEmpty()
                        viewModel.reopenClosedTab()
                        android.widget.Toast.makeText(
                            context,
                            if (hadClosed) "Tab restored" else "No recently closed tabs",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onNewIncognitoTabClick = {
                        requestNewIncognitoTab(uiState.activeSpaceId)
                    },
                    onRequestDesktopSiteClick = {
                        val tab = activeTab
                        if (tab != null) {
                        val enable = !isDesktopSiteEnabled
                        val applied = geckoSessionPool.setDesktopMode(tab.id, enable)
                        isDesktopSiteEnabled = enable
                        // Also push viewport hint via bridge for the current page
                        val session = geckoSessionPool.get(tab.id)
                        if (session != null) {
                            coroutineScope.launch {
                                runCatching {
                                    com.wormhole.browser.core.gecko.GeckoExtensionBridge.send(
                                        session,
                                        "set_desktop_viewport",
                                        mapOf("desktop" to enable.toString()),
                                    )
                                }
                            }
                        }
                        android.widget.Toast.makeText(
                            context,
                            if (enable) "Desktop site requested" else "Mobile site requested",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        }
                    },
                    onTranslateClick = {
                        if (activeTab?.url.isNullOrBlank()) {
                            android.widget.Toast.makeText(context, "Open a page to translate", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            isTranslateLanguageSheetOpen = true
                        }
                    },
                    onFindInPageClick = {
                        if (activeTab?.url.isNullOrBlank()) {
                            android.widget.Toast.makeText(context, "Open a page first", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            isFindInPageOpen = true
                        }
                    },
                    onAssistantClick = {
                        isAiOpen = true
                    },
                    onSettingsClick = {
                        showSettings = true
                    },
                    onShareClick = {
                        val url = activeTab?.url.orEmpty()
                        if (url.isBlank()) {
                            android.widget.Toast.makeText(context, "Nothing to share", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            runCatching {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, url)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, activeTab?.title.orEmpty())
                                }
                                context.startActivity(
                                    android.content.Intent.createChooser(shareIntent, "Share link").apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                )
                            }.onFailure {
                                android.widget.Toast.makeText(context, "Could not open share sheet", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onCopyLinkClick = {
                        val url = activeTab?.url.orEmpty()
                        if (url.isBlank()) {
                            android.widget.Toast.makeText(context, "Nothing to copy", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            runCatching {
                                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Link", url))
                                android.widget.Toast.makeText(context, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                android.widget.Toast.makeText(context, "Copy failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationY = if (animatedImeBottomPx > 0) {
                                bottomBarHeightPx.toFloat().coerceAtLeast(1f) *
                                    (animatedImeBottomPx.toFloat() / imeBottomPx.coerceAtLeast(1).toFloat())
                            } else {
                                toolbarOffsetPx
                            }
                        }
                        .onSizeChanged { bottomBarHeightPx = it.height },
                )
                }

                TranslateBar(
                    visible = translateOffer != null &&
                        !isCommandBarOpen &&
                        !isTabSwitcherOpen &&
                        !isKeyboardOpen &&
                        activeTab?.url?.isNotBlank() == true,
                    sourceLabel = translateOffer?.displayName ?: "another language",
                    target = translateTarget,
                    translated = pageTranslated,
                    busy = translateBusy,
                    onTranslate = {
                        val tab = activeTab
                        val session = tab?.id?.let { geckoSessionPool.get(it) } ?: return@TranslateBar
                        translateBusy = true
                        coroutineScope.launch {
                            try {
                                when (
                                    val result = com.wormhole.browser.core.gecko.PageTranslator.translatePage(
                                        session = session,
                                        language = translateTarget,
                                        pageUrl = tab.url,
                                        onOpenViewer = { viewer ->
                                            viewModel.updateTabUrl(tab.id, viewer)
                                            geckoSessionPool.requestLoad(tab.id, viewer)
                                        },
                                    )
                                ) {
                                    is com.wormhole.browser.core.gecko.PageTranslator.Result.Applied -> {
                                        pageTranslated = true
                                        translateMode = result.mode
                                    }
                                    is com.wormhole.browser.core.gecko.PageTranslator.Result.Error -> {
                                        android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Throwable) {
                                android.widget.Toast.makeText(
                                    context,
                                    e.message ?: "Translation failed",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            } finally {
                                translateBusy = false
                            }
                        }
                    },
                    onShowOriginal = {
                        val tab = activeTab
                        val session = tab?.id?.let { geckoSessionPool.get(it) } ?: return@TranslateBar
                        coroutineScope.launch {
                            val restored = com.wormhole.browser.core.gecko.PageTranslator.restoreOriginal(session, tab.url)
                            if (restored) pageTranslated = false
                        }
                    },
                    onPickLanguage = {
                        isTranslateLanguageSheetOpen = true
                    },
                    onDismiss = {
                        val host = runCatching { android.net.Uri.parse(activeTab?.url.orEmpty()).host }.getOrNull()
                        if (!host.isNullOrBlank()) dismissedTranslateHosts.add(host)
                        translateOffer = null
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = with(density) {
                            (bottomBarHeightPx - toolbarOffsetPx.toInt()).coerceAtLeast(0).toDp() + 10.dp
                        }),
                )

                CommandBar(
                    isOpen = isCommandBarOpen,
                    query = commandBarQuery,
                    mode = commandBarMode,
                    onModeChange = { commandBarMode = it },
                    onQueryChange = { commandBarQuery = it },
                    onSearchOmnibox = { query -> viewModel.searchOmnibox(query) },
                    onFetchSearchSuggestions = { query -> viewModel.fetchSearchSuggestions(query) },
                    searchEngine = currentEngine,
                    recentSearches = if (activeTab?.isIncognito == true) emptyList() else recentSearches,
                    shortcuts = if (activeTab?.isIncognito == true) emptyList() else shortcuts,
                    onFillQuery = { commandBarQuery = it },
                    onClearRecentSearches = { viewModel.clearRecentSearches() },
                    onShortcutClick = { shortcut ->
                        val tab = activeTab ?: viewModel.newTab(spaceId = uiState.activeSpaceId)
                        geckoSessionPool.requestLoad(tab.id, shortcut.url)
                        viewModel.updateTabUrl(tab.id, shortcut.url)
                        isCommandBarOpen = false
                    },
                    onAddShortcut = { title, url -> viewModel.addShortcut(title, url) },
                    hasStoredRecentSearches = if (activeTab?.isIncognito == true) true else hasStoredRecentSearches,
                    trendingSearches = if (activeTab?.isIncognito == true) emptyList() else trendingSearches,
                    onSubmit = { input ->
                        if (input.isBlank()) return@CommandBar
                        when (commandBarMode) {
                            CommandBarMode.SEARCH -> {
                                viewModel.recordTypedQueryIfSearch(input)
                                val tab = activeTab ?: viewModel.newTab(spaceId = uiState.activeSpaceId)
                                val resolved = viewModel.resolveInput(input)
                                viewModel.updateTabUrl(tab.id, resolved)
                                geckoSessionPool.requestLoad(tab.id, resolved)
                            }
                            CommandBarMode.AI -> {
                                viewModel.recordTypedQueryIfSearch(input)
                                aiAnswerQuery = input
                            }
                        }
                        isCommandBarOpen = false
                    },
                    onDismiss = { isCommandBarOpen = false },
                    modifier = Modifier.fillMaxSize(),
                )

                if (isTranslateLanguageSheetOpen) {
                    TranslateLanguageSheet(
                        onLanguageSelected = { language: TranslateLanguage ->
                            isTranslateLanguageSheetOpen = false
                            translateTarget = language
                            val tab = activeTab
                            val session = tab?.id?.let { geckoSessionPool.get(it) }
                            if (tab == null || session == null) {
                                android.widget.Toast.makeText(context, "Open a page to translate", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                translateOffer = translateOffer ?: com.wormhole.browser.core.gecko.PageTranslator.Detection(
                                    code = "und",
                                    displayName = "this page",
                                    confident = false,
                                )
                                translateBusy = true
                                coroutineScope.launch {
                                    try {
                                        when (
                                            val result = com.wormhole.browser.core.gecko.PageTranslator.translatePage(
                                                session = session,
                                                language = language,
                                                pageUrl = tab.url,
                                                onOpenViewer = { viewer ->
                                                    viewModel.updateTabUrl(tab.id, viewer)
                                                    geckoSessionPool.requestLoad(tab.id, viewer)
                                                },
                                            )
                                        ) {
                                            is com.wormhole.browser.core.gecko.PageTranslator.Result.Applied -> {
                                                pageTranslated = true
                                                translateMode = result.mode
                                            }
                                            is com.wormhole.browser.core.gecko.PageTranslator.Result.Error -> {
                                                android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } catch (e: Throwable) {
                                        android.widget.Toast.makeText(
                                            context,
                                            e.message ?: "Translation failed",
                                            android.widget.Toast.LENGTH_LONG,
                                        ).show()
                                    } finally {
                                        translateBusy = false
                                    }
                                }
                            }
                        },
                        onDismiss = { isTranslateLanguageSheetOpen = false },
                    )
                }

                if (isAssistantSheetOpen) {
                    AiResultSheet(
                        title = "Page summary",
                        state = assistantState,
                        onDismiss = {
                            isAssistantSheetOpen = false
                            viewModel.resetAssistantState()
                        },
                    )
                }

                siteMenu?.let { menu ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { viewModel.dismissSiteContextMenu() },
                        title = { Text(menu.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        text = {
                            Column {
                                val row: @Composable (String, () -> Unit) -> Unit = { label, action ->
                                    Text(
                                        label,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .bouncyClickable(onClick = {
                                                action()
                                                viewModel.dismissSiteContextMenu()
                                            })
                                            .padding(vertical = 12.dp),
                                    )
                                }
                                row("Open in new tab") {
                                    val tab = viewModel.openInNewTab(menu.url, incognito = activeTab?.isIncognito == true)
                                    geckoSessionPool.requestLoad(tab.id, menu.url)
                                }
                                row("Open in new tab group") {
                                    val tab = viewModel.openInNewTabGroup(menu.url, incognito = activeTab?.isIncognito == true)
                                    geckoSessionPool.requestLoad(tab.id, menu.url)
                                }
                                row("Open in incognito tab") {
                                    val tab = viewModel.openInNewTab(menu.url, incognito = true)
                                    geckoSessionPool.requestLoad(tab.id, menu.url)
                                }
                                row("Add to shortcuts") { viewModel.addShortcut(menu.title, menu.url) }
                                row("Share") {
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, menu.url)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(send, "Share"))
                                }
                                row("Copy link") {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Link", menu.url))
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { viewModel.dismissSiteContextMenu() }) { Text("Close") }
                        },
                    )
                }

                if (isIncognitoConsentPending) {
                    IncognitoConsentDialog(
                        onAgree = {
                            isIncognitoConsentPending = false
                            viewModel.newTab(
                                spaceId = pendingIncognitoSpaceId ?: uiState.activeSpaceId,
                                incognito = true,
                            )
                            pendingIncognitoSpaceId = null
                        },
                        onDecline = {
                            isIncognitoConsentPending = false

                            viewModel.newTab(
                                spaceId = pendingIncognitoSpaceId ?: uiState.activeSpaceId,
                                incognito = false,
                            )
                            pendingIncognitoSpaceId = null
                        },
                    )
                }

                pendingDownload?.let { download ->
                    DownloadConfirmSheet(
                        fileName = DownloadRepository.guessFileName(download.url, download.contentDisposition, download.mimeType),
                        sourceUrl = download.url,
                        contentLength = download.contentLength,
                        mimeType = download.mimeType,
                        onConfirm = {
                            pendingDownload = null
                            val needsPermission = DownloadRepository.needsStoragePermission() &&
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (needsPermission) {
                                permissionRequestedDownload = download
                                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {

                                coroutineScope.launch {
                                    try {
                                        downloadToast = DownloadRepository.start(
                                            context = context,
                                            url = download.url,
                                            userAgent = download.userAgent,
                                            contentDisposition = download.contentDisposition,
                                            mimeType = download.mimeType,
                                        )
                                        if (downloadToast == null) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Couldn't start download",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    } catch (e: Throwable) {
                                        android.widget.Toast.makeText(
                                            context,
                                            e.message ?: "Download failed",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        },
                        onDismiss = { pendingDownload = null },
                    )
                }

                pendingSslError?.let { sslError ->
                    SslWarningSheet(
                        url = sslError.url,
                        primaryErrorCode = sslError.primaryErrorCode,
                        onGoBack = {
                            pendingSslError = null
                            sslError.onCancel()
                        },
                    )
                }

                mediaSiteConsent?.let { request ->
                    val grantableResources = request.resources.filter { resource ->
                        when (resource) {
                            android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE,
                            android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE -> true
                            else -> false
                        }
                    }
                    val kinds = grantableResources.mapNotNull { resource ->
                        when (resource) {
                            android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE -> SitePermissionKind.CAMERA
                            android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE -> SitePermissionKind.MICROPHONE
                            else -> null
                        }
                    }.distinct()
                    SitePermissionSheet(
                        origin = request.origin,
                        kinds = kinds.ifEmpty { listOf(SitePermissionKind.CAMERA) },
                        onAllow = {
                            mediaSiteConsent = null

                            request.onGrant(grantableResources)
                        },
                        onDeny = {
                            mediaSiteConsent = null
                            request.onDeny()
                        },
                    )
                }

                geolocationSiteConsent?.let { request ->
                    SitePermissionSheet(
                        origin = request.origin,
                        kinds = listOf(SitePermissionKind.LOCATION),
                        onAllow = {
                            geolocationSiteConsent = null

                            request.onAllow(false)
                        },
                        onDeny = {
                            geolocationSiteConsent = null
                            request.onDeny()
                        },
                    )
                }

                DownloadToast(
                    fileName = downloadToast?.fileName.orEmpty(),
                    mimeType = downloadToast?.mimeType,
                    visible = downloadToast != null,
                    onClick = {
                        downloadToast = null
                        showDownloads = true
                    },
                    onDismiss = { downloadToast = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomBarHeight),
                )
            }
        }

        AnimatedVisibility(
            visible = isAiOpen,
            enter = fadeIn(animationSpec = WormHoleMotion.settled()),
            exit = fadeOut(animationSpec = WormHoleMotion.settled()),
        ) {
            AiSheet(
                apiKey = geminiApiKey,
                activeTab = activeTab,
                viewModel = viewModel,
                geckoSessionPool = geckoSessionPool,
                onSummarise = {
                    isAiOpen = false
                    val tab = activeTab
                    val session = tab?.id?.let { geckoSessionPool.get(it) }
                    if (session == null) {
                        viewModel.resetAssistantState()
                    } else {
                        isAssistantSheetOpen = true
                        viewModel.setAssistantLoading()
                        coroutineScope.launch {
                            val pageText = com.wormhole.browser.core.gecko.PageTranslator.readReadableText(session)
                            viewModel.summarizePage(pageText)
                        }
                    }
                },
                onTranslate = {
                    isAiOpen = false
                    isTranslateLanguageSheetOpen = true
                },
                onDismiss = { isAiOpen = false },
            )
        }

        val currentAiAnswerQuery = aiAnswerQuery
        if (currentAiAnswerQuery != null) {
            LaunchedEffect(currentAiAnswerQuery) {
                viewModel.askWormHole(currentAiAnswerQuery)
            }
            val aiAnswerState by viewModel.aiAnswerState.collectAsState()
            AiAnswerScreen(
                query = currentAiAnswerQuery,
                state = aiAnswerState,
                onBack = {
                    aiAnswerQuery = null
                    viewModel.resetAiAnswerState()
                },
                onAskFollowUp = {

                    aiAnswerQuery = null
                    viewModel.resetAiAnswerState()
                    commandBarQuery = currentAiAnswerQuery
                    commandBarMode = CommandBarMode.AI
                    isCommandBarOpen = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isTabSwitcherOpen) {
            TabSwitcherOverlay(
                tabs = uiState.visibleTabs,
                activeTabId = uiState.activeTabId,
                onTabSelected = {
                    viewModel.selectTab(it)
                    isTabSwitcherOpen = false
                },
                onTabClosed = { tabId ->
                    geckoSessionPool.remove(tabId)
                    viewModel.closeTab(tabId)
                },
                onForceCloseTab = { tabId ->
                    geckoSessionPool.remove(tabId)
                    viewModel.closeTab(tabId, force = true)
                },
                onNewTab = {
                    viewModel.newTab(spaceId = uiState.activeSpaceId)
                    isTabSwitcherOpen = false
                },
                onNewIncognitoTab = {
                    requestNewIncognitoTab(uiState.activeSpaceId)
                    isTabSwitcherOpen = false
                },
                onHistory = {
                    isTabSwitcherOpen = false
                    showLibrary = true
                },
                onClose = { isTabSwitcherOpen = false },
                onCloseAllTabs = { closeIncognito ->
                    val idsToClose = uiState.visibleTabs
                        .filter { it.isIncognito == closeIncognito && !it.isPinned }
                        .map { it.id }
                    idsToClose.forEach { geckoSessionPool.remove(it) }
                    viewModel.closeAllTabsInSpace(uiState.activeSpaceId, incognitoOnly = closeIncognito)
                },
                onPinTab = { id, pinned -> viewModel.setTabPinned(id, pinned) },
                onAddShortcut = { title, url -> viewModel.addShortcut(title, url) },
            )
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn(animationSpec = WormHoleMotion.settled()) +
                slideInHorizontally(animationSpec = WormHoleMotion.bouncy(), initialOffsetX = { it / 3 }),
            exit = fadeOut(animationSpec = WormHoleMotion.settled()) +
                slideOutHorizontally(animationSpec = WormHoleMotion.bouncy(), targetOffsetX = { it / 3 }),
        ) {
            SettingsScreen(
                currentEngine = currentEngine,
                onEngineSelected = viewModel::setSearchEngine,
                homeBackground = homeBackground,
                onHomeBackgroundSelected = viewModel::setHomeBackground,
                geminiApiKey = geminiApiKey,
                onGeminiApiKeyChanged = viewModel::setGeminiApiKey,
                trackerBlockingEnabled = trackerBlockingEnabled,
                onTrackerBlockingChanged = viewModel::setTrackerBlockingEnabled,
                adBlockingEnabled = adBlockingEnabled,
                onAdBlockingChanged = viewModel::setAdBlockingEnabled,
                popupBlockingEnabled = popupBlockingEnabled,
                onPopupBlockingChanged = viewModel::setPopupBlockingEnabled,
                webDarkModeEnabled = webDarkModeEnabled,
                onWebDarkModeChanged = viewModel::setWebDarkModeEnabled,
                dynamicBackgroundEnabled = dynamicBackgroundEnabled,
                onDynamicBackgroundChanged = viewModel::setDynamicBackgroundEnabled,
                onSetDefaultBrowserClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                        )
                    }
                },
                appVersionName = com.wormhole.browser.BuildConfig.VERSION_NAME,
                themeMode = themeMode,
                onThemeModeSelected = viewModel::setThemeMode,
                homepageUrl = homepageUrl,
                onHomepageUrlChanged = viewModel::setHomepageUrl,
                onFeedbackClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/AKA-Wormhole/Worm-Hole/issues"),
                    )
                    runCatching { context.startActivity(intent) }
                },
                onAboutClick = { showAbout = true },
                onLogsClick = { showLogs = true },
                onPasskeysClick = { showPasskeys = true },
                onClearBrowsingData = { viewModel.clearAllBrowsingData() },
                onPrivacyPolicyClick = { showPrivacyPolicy = true },
                onTermsClick = { showTerms = true },
                onOpenSourceLicensesClick = { showOpenSourceLicenses = true },
                onBack = { showSettings = false },
                hasDiagnosticReport = com.wormhole.browser.core.crash.CrashHandler.hasReports(context),
                onShareDiagnosticReport = {
                    val report = com.wormhole.browser.core.crash.CrashHandler.latestReport(context)
                    if (!report.isNullOrBlank()) {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, report)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "WormHole diagnostic report")
                        }
                        runCatching {
                            context.startActivity(
                                android.content.Intent.createChooser(shareIntent, "Share diagnostic report"),
                            )
                        }
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = showPrivacyPolicy,
            enter = fadeIn(animationSpec = WormHoleMotion.settled()) +
                slideInHorizontally(animationSpec = WormHoleMotion.bouncy(), initialOffsetX = { it / 3 }),
            exit = fadeOut(animationSpec = WormHoleMotion.settled()) +
                slideOutHorizontally(animationSpec = WormHoleMotion.bouncy(), targetOffsetX = { it / 3 }),
        ) {
            com.wormhole.browser.ui.settings.PrivacyPolicyScreen(onBack = { showPrivacyPolicy = false })
        }

        AnimatedVisibility(
            visible = showTerms,
            enter = fadeIn(animationSpec = WormHoleMotion.settled()) +
                slideInHorizontally(animationSpec = WormHoleMotion.bouncy(), initialOffsetX = { it / 3 }),
            exit = fadeOut(animationSpec = WormHoleMotion.settled()) +
                slideOutHorizontally(animationSpec = WormHoleMotion.bouncy(), targetOffsetX = { it / 3 }),
        ) {
            com.wormhole.browser.ui.settings.TermsOfServiceScreen(onBack = { showTerms = false })
        }

        AnimatedVisibility(
            visible = showOpenSourceLicenses,
            enter = fadeIn(animationSpec = WormHoleMotion.settled()) +
                slideInHorizontally(animationSpec = WormHoleMotion.bouncy(), initialOffsetX = { it / 3 }),
            exit = fadeOut(animationSpec = WormHoleMotion.settled()) +
                slideOutHorizontally(animationSpec = WormHoleMotion.bouncy(), targetOffsetX = { it / 3 }),
        ) {
            com.wormhole.browser.ui.settings.OpenSourceLicensesScreen(onBack = { showOpenSourceLicenses = false })
        }

        AnimatedVisibility(
            visible = showAbout,
            enter = fadeIn(animationSpec = WormHoleMotion.settled()) +
                slideInHorizontally(animationSpec = WormHoleMotion.bouncy(), initialOffsetX = { it / 3 }),
            exit = fadeOut(animationSpec = WormHoleMotion.settled()) +
                slideOutHorizontally(animationSpec = WormHoleMotion.bouncy(), targetOffsetX = { it / 3 }),
        ) {
            AboutScreen(onBack = { showAbout = false })
        }

        AnimatedVisibility(
            visible = showLogs,
            enter = fadeIn(animationSpec = WormHoleMotion.settled()) +
                slideInHorizontally(animationSpec = WormHoleMotion.bouncy(), initialOffsetX = { it / 3 }),
            exit = fadeOut(animationSpec = WormHoleMotion.settled()) +
                slideOutHorizontally(animationSpec = WormHoleMotion.bouncy(), targetOffsetX = { it / 3 }),
        ) {
            LogsScreen(onBack = { showLogs = false })
        }

        LaunchedEffect(showPasskeys) {
            if (!showPasskeys) {
                isPasskeysAuthenticated = false
                return@LaunchedEffect
            }
            val activity = fragmentActivity
            if (activity == null || !BiometricAuthenticator.isAvailable(activity)) {

                isPasskeysAuthenticated = true
                return@LaunchedEffect
            }
            BiometricAuthenticator.authenticate(
                activity = activity,
                title = "Unlock Passkeys",
                subtitle = "Verify it's you to manage passkeys and passwords",
                onSuccess = { isPasskeysAuthenticated = true },
                onFailure = { showPasskeys = false },
                // There's no in-place "retry" UI for a locked-but-open Passkeys sheet
                // today, so backing out of the prompt still closes it -- but the user
                // can immediately reopen it (unlike before, this is now only reachable
                // via genuine cancellation vs. a failed biometric attempt, which future
                // UI can distinguish, e.g. showing a "try again" state instead).
                onCancelled = { showPasskeys = false },
            )
        }
        AnimatedVisibility(
            visible = showPasskeys && isPasskeysAuthenticated,
            enter = fadeIn(animationSpec = WormHoleMotion.settled()) +
                slideInHorizontally(animationSpec = WormHoleMotion.bouncy(), initialOffsetX = { it / 3 }),
            exit = fadeOut(animationSpec = WormHoleMotion.settled()) +
                slideOutHorizontally(animationSpec = WormHoleMotion.bouncy(), targetOffsetX = { it / 3 }),
        ) {
            com.wormhole.browser.ui.settings.PasskeysScreen(onBack = { showPasskeys = false })
        }

        AnimatedVisibility(
            visible = showDownloads,
            enter = fadeIn(animationSpec = WormHoleMotion.settled()),
            exit = fadeOut(animationSpec = WormHoleMotion.settled()),
        ) {
            DownloadsSheet(onDismiss = { showDownloads = false })
        }

        AnimatedVisibility(
            visible = showLibrary,
            enter = fadeIn(animationSpec = WormHoleMotion.settled()) +
                slideInHorizontally(animationSpec = WormHoleMotion.bouncy(), initialOffsetX = { it / 3 }),
            exit = fadeOut(animationSpec = WormHoleMotion.settled()) +
                slideOutHorizontally(animationSpec = WormHoleMotion.bouncy(), targetOffsetX = { it / 3 }),
        ) {
            LibrarySheet(
                bookmarks = viewModel.bookmarks.collectAsState().value,
                history = viewModel.history.collectAsState().value,
                initialTab = libraryInitialTab,
                onDismiss = { showLibrary = false },
                onOpen = { url ->
                    activeTab?.let { tab ->
                        geckoSessionPool.requestLoad(tab.id, url)
                        viewModel.updateTabUrl(tab.id, url)
                    }
                    showLibrary = false
                },
                onRemoveBookmark = viewModel::removeBookmark,
                onClearHistory = viewModel::clearHistory,
            )
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomBar(
    searchEngine: SearchEngine = SearchEngine.DEFAULT,
    onEngineSelected: (SearchEngine) -> Unit = {},
    tabCount: Int,
    displayUrl: String,
    isSecure: Boolean,
    onAddressBarClick: () -> Unit,
    onVoiceSearchClick: (String) -> Unit = {},
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onReloadClick: () -> Unit,
    onStopLoadingClick: () -> Unit,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isMenuOpen: Boolean,
    isDesktopSiteEnabled: Boolean,
    onTabSwitcherClick: () -> Unit,
    onNewTabFromBarClick: () -> Unit,
    onHomeClick: () -> Unit,
    onMenuButtonClick: () -> Unit,
    onMenuDismiss: () -> Unit,
    onDownloadsClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onHistoryClick: () -> Unit = onLibraryClick,
    onPasswordsClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onAddShortcutClick: () -> Unit,
    onDuplicateTabClick: () -> Unit,
    onReopenClosedTabClick: () -> Unit,
    onNewIncognitoTabClick: () -> Unit,
    onRequestDesktopSiteClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onFindInPageClick: () -> Unit,
    onAssistantClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onShareClick: () -> Unit = {},
    onCopyLinkClick: () -> Unit = {},
) {

    val adaptiveIconColor = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.onSurface
    var showEnginePicker by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .navigationBarsPadding(),
    ) {
        // Single-row bar: back, forward, search/AI pill, tab count, menu.
        // Flush edge-to-edge tray, rounded only at the top corners.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Search / address pill.
            // Engine logo opens the search-engine picker.
            // tapping the rest of the pill opens the normal search/address bar.
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                ),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .bouncyClickable(onClick = onAddressBarClick)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.bouncyClickable(onClick = { showEnginePicker = true })) {
                        SearchEngineLogo(engine = searchEngine, modifier = Modifier.size(18.dp))
                    }
                    if (isSecure) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Secure connection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        text = displayUrl.ifBlank { "Search or type URL" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (displayUrl.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    VoiceMicButton(
                        onResult = onVoiceSearchClick,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconSize = 18.dp,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .border(
                        width = 1.6.dp,
                        color = adaptiveIconColor,
                        shape = RoundedCornerShape(7.dp),
                    )
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTabSwitcherClick,
                        onLongClick = onNewTabFromBarClick,
                        onClickLabel = "Tab switcher, ${tabCount.coerceAtLeast(1)} " +
                            if (tabCount.coerceAtLeast(1) == 1) "tab open" else "tabs open",
                        onLongClickLabel = "New tab",
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tabCount.coerceAtLeast(1).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = adaptiveIconColor,
                )
            }

            Icon(
                Icons.Default.Add,
                contentDescription = "New tab",
                tint = adaptiveIconColor,
                modifier = Modifier
                    .size(22.dp)
                    .bouncyClickable(onClick = onNewTabFromBarClick),
            )

            Icon(
                Icons.Default.Menu,
                contentDescription = "Menu",
                tint = adaptiveIconColor,
                modifier = Modifier
                    .size(22.dp)
                    .bouncyClickable(onClick = onMenuButtonClick),
            )
        }
    }
    if (showEnginePicker) {
        SearchEnginePicker(
            current = searchEngine,
            onSelected = onEngineSelected,
            onDismiss = { showEnginePicker = false },
        )
    }
    PageToolsMenu(
        isExpanded = isMenuOpen,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        onBackClick = onBackClick,
        onForwardClick = onForwardClick,
        onReloadClick = onReloadClick,
        isDesktopSiteEnabled = isDesktopSiteEnabled,
        onDismiss = onMenuDismiss,
        onDownloadsClick = onDownloadsClick,
        onLibraryClick = onLibraryClick,
        onHistoryClick = onHistoryClick,
        onPasswordsClick = onPasswordsClick,
        onBookmarkClick = onBookmarkClick,
        onAddShortcutClick = onAddShortcutClick,
        onDuplicateTabClick = onDuplicateTabClick,
        onReopenClosedTabClick = onReopenClosedTabClick,
        onNewIncognitoTabClick = onNewIncognitoTabClick,
        onRequestDesktopSiteClick = onRequestDesktopSiteClick,
        onTranslateClick = onTranslateClick,
        onFindInPageClick = onFindInPageClick,
        onAssistantClick = onAssistantClick,
        onSettingsClick = onSettingsClick,
        onShareClick = onShareClick,
        onCopyLinkClick = onCopyLinkClick,
    )
    }
}

@Composable
private fun TabSwitcherOverlay(
    tabs: List<com.wormhole.browser.core.browser.Tab>,
    activeTabId: String?,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onForceCloseTab: (String) -> Unit = onTabClosed,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onHistory: () -> Unit,
    onClose: () -> Unit,
    onCloseAllTabs: (incognito: Boolean) -> Unit = {},
    onPinTab: (String, Boolean) -> Unit = { _, _ -> },
    onAddShortcut: (title: String, url: String) -> Unit = { _, _ -> },
) {
    var incognito by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var isOverflowMenuOpen by remember { mutableStateOf(false) }
    var showCloseAllConfirm by remember { mutableStateOf(false) }
    var tabQuery by remember { mutableStateOf("") }
    val visibleTabs = tabs.filter { it.isIncognito == incognito && it.url.isNotBlank() }
        .filter {
            tabQuery.isBlank() ||
                it.title.contains(tabQuery, ignoreCase = true) ||
                it.url.contains(tabQuery, ignoreCase = true)
        }
        .sortedByDescending { it.isPinned }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CircularIconButton(
                    icon = Icons.Default.History,
                    contentDescription = "History",
                    onClick = onHistory,
                )

                Surface(
                    shape = RoundedCornerShape(27.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier

                        .weight(1f, fill = false)
                        .widthIn(max = 270.dp)
                        .height(54.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TabModeChip(
                            text = "${tabs.count { !it.isIncognito && it.url.isNotBlank() }} Tabs",
                            selected = !incognito,
                            onClick = { incognito = false },
                            modifier = Modifier.weight(1f),
                        )
                        TabModeChip(
                            text = "${tabs.count { it.isIncognito && it.url.isNotBlank() }} Incognito",
                            selected = incognito,
                            onClick = { incognito = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                CircularIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "Tab options",
                    onClick = { isOverflowMenuOpen = true },
                )
                androidx.compose.material3.DropdownMenu(
                    expanded = isOverflowMenuOpen,
                    onDismissRequest = { isOverflowMenuOpen = false },
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(if (selectionMode) "Cancel select" else "Select tabs") },
                        onClick = {
                            selectionMode = !selectionMode
                            isOverflowMenuOpen = false
                        },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(if (incognito) "Close all incognito tabs" else "Close all tabs") },
                        enabled = visibleTabs.isNotEmpty(),
                        onClick = {
                            isOverflowMenuOpen = false
                            showCloseAllConfirm = true
                        },
                    )
                }
            }

            if (selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { selectionMode = false }) {
                        Text("Cancel")
                    }
                }
            }

            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = tabQuery,
                    onValueChange = { tabQuery = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        if (tabQuery.isEmpty()) {
                            Text("Search tabs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    },
                )
            }

            if (visibleTabs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(com.wormhole.browser.ui.theme.WormHoleSurface.Fill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (incognito) Icons.Default.Shield else Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text(
                        if (incognito) "No Incognito tabs" else "No open tabs",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    Text(
                        if (incognito) "Open a private tab with the + button below."
                        else "Open a new tab with the + button below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(visibleTabs, key = { it.id }) { tab ->
                        TabGridCard(
                            tab = tab,
                            active = tab.id == activeTabId,
                            onClick = { onTabSelected(tab.id) },
                            onClose = { onTabClosed(tab.id) },
                            onPin = { onPinTab(tab.id, !tab.isPinned) },
                            onAddShortcut = {
                                if (tab.url.isNotBlank()) onAddShortcut(tab.title.ifBlank { tab.url }, tab.url)
                            },
                            onRemove = { onForceCloseTab(tab.id) },

                            modifier = Modifier.animateItem(
                                placementSpec = WormHoleMotion.bouncy(),
                            ),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { selectionMode = !selectionMode }) {
                    Text("Select")
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(56.dp)
                        .bouncyClickable(
                            onClick = if (incognito) onNewIncognitoTab else onNewTab,
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = if (incognito) "New incognito tab" else "New tab",
                            tint = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                TextButton(onClick = onClose) {
                    Text("Done")
                }
            }
        }
    }

    if (showCloseAllConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCloseAllConfirm = false },
            title = { Text(if (incognito) "Close all incognito tabs?" else "Close all tabs?") },
            text = {
                Text(
                    if (incognito) {
                        "This closes every incognito tab. This can't be undone."
                    } else {
                        "This closes every open tab in this space. This can't be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCloseAllConfirm = false
                    onCloseAllTabs(incognito)
                }) { Text("Close all") }
            },
            dismissButton = {
                TextButton(onClick = { showCloseAllConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TabModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(23.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier
            .height(46.dp)
            .bouncyClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TabGridCard(
    tab: com.wormhole.browser.core.browser.Tab,
    active: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onPin: () -> Unit = {},
    onAddShortcut: () -> Unit = {},
    onRemove: () -> Unit = onClose,
    modifier: Modifier = Modifier,
) {

    val entranceScale = remember { androidx.compose.animation.core.Animatable(0.72f) }
    LaunchedEffect(Unit) {
        entranceScale.animateTo(1f, animationSpec = WormHoleMotion.bouncy())
    }

    var isClosing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val closeScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isClosing) 0.75f else 1f,
        animationSpec = WormHoleMotion.settled(),
        label = "tabCardCloseScale",
    )
    val closeAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isClosing) 0f else 1f,
        animationSpec = WormHoleMotion.settled(),
        label = "tabCardCloseAlpha",
        finishedListener = { if (isClosing) onClose() },
    )

    val thumbnail = com.wormhole.browser.core.webview.TabThumbnailCache.get(tab.id)
        ?.takeUnless { it.isRecycled }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (active) 3.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = entranceScale.value * closeScale
                scaleY = entranceScale.value * closeScale
                alpha = closeAlpha
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuOpen = true },
            ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (thumbnail != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                if (tab.isPinned) {
                    Text(
                        "Pinned",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (!tab.isPinned) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(34.dp)
                        .bouncyClickable(onClick = { isClosing = true }),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close tab",
                            tint = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false; moreOpen = false },
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Remove") },
                        onClick = { menuOpen = false; onRemove() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(if (tab.isPinned) "Unpin" else "Pin") },
                        onClick = { menuOpen = false; onPin() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Add to shortcuts") },
                        onClick = { menuOpen = false; onAddShortcut() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("More") },
                        onClick = { moreOpen = true },
                    )
                    if (moreOpen) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                menuOpen = false
                                moreOpen = false
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, tab.url)
                                }
                                context.startActivity(android.content.Intent.createChooser(send, "Share"))
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Copy link") },
                            onClick = {
                                menuOpen = false
                                moreOpen = false
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Link", tab.url))
                            },
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val favicon = com.wormhole.browser.core.webview.FaviconCache.get(tab.url)
                    if (favicon != null) {
                        androidx.compose.foundation.Image(
                            bitmap = favicon.asImageBitmap(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 6.dp),
                        )
                    }
                    if (!tab.groupName.isNullOrBlank()) {
                        Text(
                            tab.groupName,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        tab.title.ifBlank { "New tab" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    tab.displayUrl.ifBlank { "New tab" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun CircularIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    spin: Boolean = false,
) {
    CircularIconButtonShell(contentDescription, onClick, spin) { tint ->
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

@Composable
private fun CircularIconButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    onClick: () -> Unit,
    spin: Boolean = false,
) {
    CircularIconButtonShell(contentDescription, onClick, spin) { tint ->
        Icon(painter, contentDescription = contentDescription, tint = tint)
    }
}

@Composable
private fun CircularIconButtonShell(
    contentDescription: String,
    onClick: () -> Unit,
    spin: Boolean,
    content: @Composable (tint: Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) WormHoleMotion.PRESS_SCALE else 1f,
        animationSpec = WormHoleMotion.snappy(),
        label = "circularIconButtonScale",
    )
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .size(46.dp)
            .scale(scale)
            .rotate(rotation.value)
            .clip(CircleShape)
            .background(com.wormhole.browser.ui.theme.WormHoleSurface.Fill)
            .border(1.dp, com.wormhole.browser.ui.theme.WormHoleSurface.HairlineBorder, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (spin) {
                        scope.launch {
                            rotation.animateTo(90f, animationSpec = WormHoleMotion.bouncy())
                            rotation.animateTo(0f, animationSpec = WormHoleMotion.bouncy())
                        }
                    }
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Shown over the (otherwise blank) content area when GeckoView's onLoadError
 * fires -- e.g. no connection, DNS failure, TLS error, or blocked content.
 * Without this, a failed navigation leaves nothing but the app's background
 * color behind a normal-looking toolbar, with no indication anything went wrong.
 */
@Composable
private fun LoadErrorOverlay(
    failure: com.wormhole.browser.core.gecko.PageLoadFailure,
    fallbackUrl: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shownUrl = failure.url.ifBlank { fallbackUrl }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = failure.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = failure.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (shownUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = shownUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 3,
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = com.wormhole.browser.ui.theme.WormHoleSurface.Fill,
                border = com.wormhole.browser.ui.theme.WormHoleSurface.border(),
                modifier = Modifier.bouncyClickable(onClick = onRetry),
            ) {
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }
        }
    }
}
