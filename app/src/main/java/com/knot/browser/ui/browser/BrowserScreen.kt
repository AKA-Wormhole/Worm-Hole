package com.knot.browser.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.knot.browser.core.ai.TranslateLanguage
import com.knot.browser.core.browser.AiRequestState
import com.knot.browser.core.browser.BrowserEvent
import com.knot.browser.core.browser.BrowserViewModel
import com.knot.browser.core.browser.DownloadHandler
import com.knot.browser.core.browser.ExternalIntentLauncher
import com.knot.browser.core.browser.SpaceAccent
import com.knot.browser.core.webview.DesktopSiteMode
import com.knot.browser.core.webview.FindInPageController
import com.knot.browser.core.webview.PageContentExtractor
import com.knot.browser.core.webview.WebViewPool
import com.knot.browser.ui.downloads.DownloadsScreen
import com.knot.browser.ui.library.LibrarySheet
import com.knot.browser.ui.settings.SettingsScreen
import com.knot.browser.ui.sidebar.KnotSidebar
import com.knot.browser.ui.theme.KnotMotion
import com.knot.browser.ui.theme.bouncyClickable
import kotlinx.coroutines.launch

/**
 * Stage 3+ chrome: sidebar (Spaces + tab grid) + summonable command bar +
 * a bottom pill search/address bar with a 3-dot page-tools menu (Downloads,
 * desktop site, Translate, Assistant), replacing the earlier top-address-bar
 * layout. Built against docs/UI_DESIGN_BRIEF.md -- see that file for the
 * rationale behind the sidebar/command-bar pieces; the bottom bar and page
 * tools are a later addition on top of that shell.
 */
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = viewModel(),
    webViewPool: WebViewPool = remember { WebViewPool() },
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentEngine by viewModel.searchEngine.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val assistantState by viewModel.assistantState.collectAsState()
    val translateState by viewModel.translateState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Starts closed: since the tab switcher is now a full-screen overlay,
    // opening by default would hide the page on launch instead of showing it.
    var isSidebarExpanded by remember { mutableStateOf(false) }
    var isCommandBarOpen by remember { mutableStateOf(false) }
    var commandBarQuery by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var isFindInPageOpen by remember { mutableStateOf(false) }
    var isPageToolsMenuOpen by remember { mutableStateOf(false) }
    var isDesktopSiteEnabled by remember { mutableStateOf(false) }
    var isTranslateLanguageSheetOpen by remember { mutableStateOf(false) }
    var isAssistantSheetOpen by remember { mutableStateOf(false) }
    var isTranslateSheetOpen by remember { mutableStateOf(false) }
    // Holds the most recent download request while the confirm sheet is
    // shown -- null means no sheet is up. Set from the ViewModel's event
    // stream below, cleared on confirm/dismiss.
    var pendingDownload by remember { mutableStateOf<BrowserEvent.DownloadRequested?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BrowserEvent.LaunchExternalApp -> ExternalIntentLauncher.launch(context, event.uri)
                is BrowserEvent.DownloadRequested -> pendingDownload = event
                is BrowserEvent.LoadError -> Unit // surfaced as an error page in a later stage
            }
        }
    }

    val activeTab = uiState.activeTab

    if (showDownloads) {
        DownloadsScreen(onBack = { showDownloads = false })
        return
    }

    if (showLibrary) {
        LibrarySheet(
            bookmarks = viewModel.bookmarks.collectAsState().value,
            history = viewModel.history.collectAsState().value,
            onDismiss = { showLibrary = false },
            onOpen = { url ->
                activeTab?.let { tab ->
                    webViewPool.get(tab.id)?.loadUrl(url)
                    viewModel.updateTabUrl(tab.id, url)
                }
                showLibrary = false
            },
            onRemoveBookmark = viewModel::removeBookmark,
            onClearHistory = viewModel::clearHistory,
        )
    }

    // Desktop-site state is per-tab in spirit but tracked here as a single
    // toggle for the active tab only -- reset whenever the active tab
    // changes, since "is this tab desktop mode" isn't modeled on Tab
    // itself yet. Good enough for a single-tab toggle; a future pass can
    // promote this into Tab state if per-tab persistence across switches
    // turns out to matter.
    LaunchedEffect(activeTab?.id) { isDesktopSiteEnabled = false }

    // FindInPageController wraps a specific WebView, so it must be
    // recreated whenever the active tab (and therefore the underlying
    // WebView instance) changes -- it is NOT shared across tabs.
    // Note: on a brand new tab's very first composition, the WebView may
    // not exist in the pool yet (KnotWebViewHost creates it during this
    // same pass), so this can transiently be null and self-correct on
    // the next recomposition once the pool has the WebView. Fine for
    // find-in-page, which is never needed on a tab's first frame anyway.
    val findInPageController = remember(activeTab?.id) {
        activeTab?.let { tab -> webViewPool.get(tab.id) }?.let { FindInPageController(it) }
    }
    DisposableEffect(activeTab?.id) {
        onDispose { findInPageController?.stop() }
    }

    // Measured live so the WebView/new-tab content and the find-in-page
    // pill can reserve exactly the bottom bar's real on-screen height
    // (which includes the device's gesture-nav inset) instead of a
    // magic-number guess that drifts out of sync with the bar's actual
    // padding/inset.
    val density = LocalDensity.current
    var bottomBarHeightPx by remember { mutableStateOf(0) }
    val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() }

    Box(modifier = Modifier.fillMaxSize()) {
        // The sidebar used to live inline here as a side-by-side Row
        // panel; it's now a full-screen overlay rendered separately
        // below, so this content column simply fills the whole screen.
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (activeTab != null) {
                    if (activeTab.url.isNotBlank()) {
                        KnotWebViewHost(
                            tab = activeTab,
                            webViewPool = webViewPool,
                            callbacks = viewModel,
                            // Reserve space for the bottom bar so the page's
                            // last ~64dp+inset isn't permanently hidden
                            // underneath it -- previously the WebView filled
                            // the full screen and the bar simply floated on
                            // top of the content instead of the layout
                            // making room for it.
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = bottomBarHeight),
                        )
                    } else {
                        NewTabSurface(
                            activeSpace = uiState.activeSpace,
                            onCommandBarRequested = {
                                commandBarQuery = ""
                                isCommandBarOpen = true
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = bottomBarHeight),
                        )
                    }
                }

                if (activeTab?.isLoading == true) {
                    // Sits just below the status bar/notch instead of
                    // directly under the physical top edge -- previously
                    // it had no top inset at all, so on a phone with a
                    // notch/punch-hole the load progress line could be
                    // partly obscured by the status bar icons above it.
                    LinearProgressIndicator(
                        progress = { activeTab.loadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(2.dp),
                        color = uiState.activeSpace?.accent?.color ?: MaterialTheme.colorScheme.primary,
                    )
                }

                if (findInPageController != null) {
                    FindInPageBar(
                        controller = findInPageController,
                        onClose = { isFindInPageOpen = false },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomBarHeight + 12.dp)
                            .fillMaxWidth(),
                    )
                    LaunchedEffect(isFindInPageOpen) {
                        if (isFindInPageOpen) findInPageController.start() else findInPageController.stop()
                    }
                }

                BottomBar(
                    title = activeTab?.title.orEmpty(),
                    url = activeTab?.displayUrl.orEmpty(),
                    isMenuOpen = isPageToolsMenuOpen,
                    isDesktopSiteEnabled = isDesktopSiteEnabled,
                    showSearchPill = activeTab?.url?.isNotBlank() == true,
                    tabCount = uiState.tabs.size,
                    onBackClick = { activeTab?.let { webViewPool.get(it.id)?.goBack() } },
                    onForwardClick = { activeTab?.let { webViewPool.get(it.id)?.goForward() } },
                    onReloadClick = { activeTab?.let { webViewPool.get(it.id)?.reload() } },
                    canGoBack = activeTab?.canGoBack == true,
                    canGoForward = activeTab?.canGoForward == true,
                    onSidebarToggle = { isSidebarExpanded = !isSidebarExpanded },
                    onSearchBarClick = {
                        commandBarQuery = activeTab?.displayUrl.orEmpty()
                        isCommandBarOpen = true
                    },
                    onNewTabClick = { viewModel.newTab(spaceId = uiState.activeSpaceId) },
                    onMenuButtonClick = { isPageToolsMenuOpen = true },
                    onMenuDismiss = { isPageToolsMenuOpen = false },
                    onDownloadsClick = {
                        isPageToolsMenuOpen = false
                        showDownloads = true
                    },
                    onLibraryClick = {
                        isPageToolsMenuOpen = false
                        showLibrary = true
                    },
                    onBookmarkClick = {
                        isPageToolsMenuOpen = false
                        activeTab?.let(viewModel::addBookmark)
                    },
                    onDuplicateTabClick = {
                        isPageToolsMenuOpen = false
                        activeTab?.let(viewModel::duplicateTab)
                    },
                    onReopenClosedTabClick = {
                        isPageToolsMenuOpen = false
                        viewModel.reopenClosedTab()
                    },
                    onRequestDesktopSiteClick = {
                        isPageToolsMenuOpen = false
                        val tab = activeTab
                        if (tab != null) {
                            val webView = webViewPool.get(tab.id)
                            if (webView != null) {
                                isDesktopSiteEnabled = !isDesktopSiteEnabled
                                DesktopSiteMode.apply(webView, isDesktopSiteEnabled)
                            }
                        }
                    },
                    onTranslateClick = {
                        isPageToolsMenuOpen = false
                        isTranslateLanguageSheetOpen = true
                    },
                    onAssistantClick = {
                        isPageToolsMenuOpen = false
                        val tab = activeTab
                        val webView = tab?.let { webViewPool.get(it.id) }
                        if (webView == null) {
                            viewModel.resetAssistantState()
                        } else {
                            isAssistantSheetOpen = true
                            viewModel.setAssistantLoading()
                            coroutineScope.launch {
                                val pageText = PageContentExtractor.extractVisibleText(webView)
                                viewModel.summarizePage(pageText)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { bottomBarHeightPx = it.height },
                )

                CommandBar(
                    isOpen = isCommandBarOpen,
                    query = commandBarQuery,
                    onQueryChange = { commandBarQuery = it },
                    onSubmit = { input ->
                        val tab = activeTab ?: viewModel.newTab(spaceId = uiState.activeSpaceId)
                        val resolved = viewModel.resolveInput(input)
                        viewModel.updateTabUrl(tab.id, resolved)
                        webViewPool.get(tab.id)?.loadUrl(resolved)
                        isCommandBarOpen = false
                    },
                    onDismiss = { isCommandBarOpen = false },
                    modifier = Modifier.fillMaxSize(),
                )

                if (isTranslateLanguageSheetOpen) {
                    TranslateLanguageSheet(
                        onLanguageSelected = { language: TranslateLanguage ->
                            isTranslateLanguageSheetOpen = false
                            val tab = activeTab
                            val webView = tab?.let { webViewPool.get(it.id) }
                            if (webView == null) {
                                viewModel.resetTranslateState()
                            } else {
                                isTranslateSheetOpen = true
                                viewModel.setTranslateLoading()
                                coroutineScope.launch {
                                    val pageText = PageContentExtractor.extractVisibleText(webView)
                                    viewModel.translatePage(pageText, language)
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

                if (isTranslateSheetOpen) {
                    AiResultSheet(
                        title = "Translation",
                        state = translateState,
                        onDismiss = {
                            isTranslateSheetOpen = false
                            viewModel.resetTranslateState()
                        },
                    )
                }

                pendingDownload?.let { download ->
                    DownloadConfirmSheet(
                        fileName = DownloadHandler.guessFileName(download.url, download.contentDisposition, download.mimeType),
                        sourceUrl = download.url,
                        contentLength = download.contentLength,
                        mimeType = download.mimeType,
                        onConfirm = {
                            DownloadHandler.enqueue(
                                context = context,
                                url = download.url,
                                userAgent = download.userAgent,
                                contentDisposition = download.contentDisposition,
                                mimeType = download.mimeType,
                            )
                            pendingDownload = null
                        },
                        onDismiss = { pendingDownload = null },
                    )
                }
            }
        }

        // The tab switcher is a full-screen overlay (not an inline side
        // panel) so the tab grid gets the whole screen's width on a
        // phone instead of being squeezed into a fixed 280dp column --
        // dismissed via the explicit close (X) button inside KnotSidebar
        // itself, or by picking a tab.
        KnotSidebar(
            isExpanded = isSidebarExpanded,
            spaces = uiState.spaces,
            activeSpaceId = uiState.activeSpaceId,
            tabsInActiveSpace = uiState.visibleTabs,
            activeTabId = uiState.activeTabId,
            onSpaceSelected = viewModel::switchSpace,
            onAddSpace = {
                // Cycles through accents rather than prompting for a name/
                // color up front -- a naming dialog is a natural follow-up,
                // not required to prove the Spaces model out end to end.
                val nextAccent = SpaceAccent.entries.getOrElse(
                    uiState.spaces.size % SpaceAccent.entries.size,
                ) { SpaceAccent.CORAL }
                viewModel.createSpace(name = "Space ${uiState.spaces.size + 1}", accent = nextAccent)
            },
            onTabSelected = viewModel::selectTab,
            onTabClosed = viewModel::closeTab,
            onNewTab = { viewModel.newTab(spaceId = uiState.activeSpaceId) },
            onSettingsClick = {
                isSidebarExpanded = false
                showSettings = true
            },
            onClose = { isSidebarExpanded = false },
            modifier = Modifier.fillMaxSize(),
        )

        // Settings as a slide-over panel per UI_DESIGN_BRIEF.md 2.6 ("not
        // a separate Activity"), rather than a hard content swap -- slides
        // in from the right with `bouncy` (matches the sidebar's own
        // open/close motion) while the backdrop fades with `settled`, and
        // reverses the same way on dismiss instead of just disappearing.
        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn(animationSpec = KnotMotion.settled()) +
                slideInHorizontally(animationSpec = KnotMotion.bouncy(), initialOffsetX = { it / 3 }),
            exit = fadeOut(animationSpec = KnotMotion.settled()) +
                slideOutHorizontally(animationSpec = KnotMotion.bouncy(), targetOffsetX = { it / 3 }),
        ) {
            SettingsScreen(
                currentEngine = currentEngine,
                onEngineSelected = viewModel::setSearchEngine,
                themeMode = themeMode,
                onThemeModeSelected = viewModel::setThemeMode,
                geminiApiKey = geminiApiKey,
                onGeminiApiKeyChanged = viewModel::setGeminiApiKey,
                onBack = { showSettings = false },
            )
        }
    }
}

/**
 * The bottom chrome: a circular sidebar-toggle button, the pill-shaped
 * search/address bar (tapping it opens the full CommandBar), a circular
 * 3-dot page-tools button, and the PageToolsMenu dropdown anchored to it.
 */
@Composable
private fun BottomBar(
    title: String,
    tabCount: Int,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onReloadClick: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    url: String,
    isMenuOpen: Boolean,
    isDesktopSiteEnabled: Boolean,
    showSearchPill: Boolean,
    onSidebarToggle: () -> Unit,
    onSearchBarClick: () -> Unit,
    onNewTabClick: () -> Unit,
    onMenuButtonClick: () -> Unit,
    onMenuDismiss: () -> Unit,
    onDownloadsClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onDuplicateTabClick: () -> Unit,
    onReopenClosedTabClick: () -> Unit,
    onRequestDesktopSiteClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onAssistantClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            // Clears the gesture-navigation strip on modern devices --
            // previously this bar sat flush with the physical bottom
            // edge, so its icons landed right on top of the system
            // gesture-nav area, making them easy to mis-tap or trigger a
            // system-back swipe instead.
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // Back/Forward/Reload used to be three standalone CompactNavButtons
        // here. Moved into PageToolsMenu (as its first three items, above
        // a divider) since they crowded the bar and squeezed the search
        // pill -- they're used far less often than the pill itself, so
        // they belong behind the ⋮ menu rather than occupying prime bar
        // real estate.
        CircularIconButton(
            icon = Icons.Default.Menu,
            contentDescription = "Toggle sidebar",
            onClick = onSidebarToggle,
        )

        if (showSearchPill) {
            // The home surface (NewTabSurface) has its own centered search
            // pill as the primary entry point on that screen, so this bar's
            // pill is hidden there to avoid showing two search fields at
            // once -- it reappears once a page is loaded, where the home
            // surface's pill isn't present.
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    // Grew from 46dp -> 52dp now that the bar only holds
                    // four controls instead of six -- the pill is the
                    // primary action on this bar and should read as such.
                    .height(52.dp)
                    .bouncyClickable(onClick = onSearchBarClick),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = title.ifBlank { url.ifBlank { "Search or enter address" } },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Box {
            CircularIconButton(
                icon = Icons.Default.MoreVert,
                contentDescription = "Page tools",
                onClick = onMenuButtonClick,
            )
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
                onBookmarkClick = onBookmarkClick,
                onDuplicateTabClick = onDuplicateTabClick,
                onReopenClosedTabClick = onReopenClosedTabClick,
                onRequestDesktopSiteClick = onRequestDesktopSiteClick,
                onTranslateClick = onTranslateClick,
                onAssistantClick = onAssistantClick,
            )
        }

        // Tab-count indicator doubles as the "open tab switcher" affordance,
        // so it should read as tappable and alive among the surrounding
        // circular icon buttons -- previously it used the same muted
        // surfaceVariant fill/ink as inert chrome (e.g. the disabled
        // back/forward icons), which made it look disabled rather than
        // like a live count you can tap into.
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(40.dp)
                .bouncyClickable(onClick = onSidebarToggle),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = tabCount.coerceAtLeast(1).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        CircularIconButton(
            icon = Icons.Default.Add,
            contentDescription = "New tab",
            onClick = onNewTabClick,
            spin = true,
        )
    }
}

@Composable
private fun CircularIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    spin: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) KnotMotion.PRESS_SCALE else 1f,
        animationSpec = KnotMotion.snappy(),
        label = "circularIconButtonScale",
    )
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(46.dp)
            .scale(scale)
            .rotate(rotation.value),
    ) {
        IconButton(
            onClick = {
                if (spin) {
                    scope.launch {
                        rotation.animateTo(90f, animationSpec = KnotMotion.bouncy())
                        rotation.animateTo(0f, animationSpec = KnotMotion.bouncy())
                    }
                }
                onClick()
            },
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}
