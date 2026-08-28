@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.wormhole.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wormhole.browser.R
import com.wormhole.browser.core.browser.Space
import com.wormhole.browser.core.library.LibraryEntry
import com.wormhole.browser.core.library.ShortcutEntry
import com.wormhole.browser.core.settings.SearchEngine
import com.wormhole.browser.core.webview.FaviconCache
import com.wormhole.browser.core.webview.HistoryThumbnailCache
import com.wormhole.browser.ui.theme.WormHoleGold
import com.wormhole.browser.ui.theme.WormHoleMint
import com.wormhole.browser.ui.theme.WormHoleMotion
import com.wormhole.browser.ui.theme.WormHoleSky
import com.wormhole.browser.ui.theme.WormHoleViolet
import com.wormhole.browser.ui.theme.bouncyClickable
import java.net.URI

/** Real search-engine logo: prefers the official favicon extracted from the engine's site, falls back to accurate local vector. */
@Composable
fun SearchEngineLogo(engine: SearchEngine, modifier: Modifier = Modifier) {
    val logoRes = when (engine) {
        SearchEngine.GOOGLE -> R.drawable.ic_google_logo
        SearchEngine.DUCKDUCKGO -> R.drawable.ic_ddg_logo
        SearchEngine.BING -> R.drawable.ic_bing_logo
        SearchEngine.YAHOO -> R.drawable.ic_yahoo_logo
        SearchEngine.BRAVE -> R.drawable.ic_brave_logo
        SearchEngine.ECOSIA -> R.drawable.ic_ecosia_logo
        SearchEngine.STARTPAGE -> R.drawable.ic_startpage_logo
    }
    // Trigger extraction of the real logo from the engine domain (cached on disk/memory).
    androidx.compose.runtime.LaunchedEffect(engine.id) {
        FaviconCache.fetchAndCache(engine.homeUrl, engine.logoUrl)
    }
    val real = FaviconCache.get(engine.homeUrl)
    if (real != null && !real.isRecycled) {
        androidx.compose.foundation.Image(
            bitmap = real.asImageBitmap(),
            contentDescription = engine.displayName,
            modifier = modifier.size(20.dp),
        )
    } else {
        Icon(
            painter = androidx.compose.ui.res.painterResource(logoRes),
            contentDescription = engine.displayName,
            tint = Color.Unspecified,
            modifier = modifier.size(20.dp),
        )
    }
}

/** Official multi-color Google "G" mark, rendered from the accurate vector asset. */
@Composable
fun GoogleGGlyph(modifier: Modifier = Modifier) {
    Icon(
        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_google_logo),
        contentDescription = "Google",
        tint = Color.Unspecified,
        modifier = modifier,
    )
}

@Composable
fun NewTabSurface(
    activeSpace: Space?,
    shortcuts: List<ShortcutEntry>,

    history: List<LibraryEntry>,
    onCommandBarRequested: () -> Unit,
    onVoiceSearch: (String) -> Unit = {},
    onShortcutClick: (ShortcutEntry) -> Unit,
    onShortcutRemove: (ShortcutEntry) -> Unit,
    onAddShortcut: (title: String, url: String) -> Unit,

    onTrendingSearch: (String) -> Unit,
    trendingSearches: List<String> = emptyList(),

    onAskWormHoleClick: () -> Unit,

    onHistoryClick: (LibraryEntry) -> Unit,

    searchEngine: SearchEngine = SearchEngine.DEFAULT,
    onEngineSelected: (SearchEngine) -> Unit = {},
    homeBackground: com.wormhole.browser.core.settings.HomeBackground =
        com.wormhole.browser.core.settings.HomeBackground.DEFAULT,
    modifier: Modifier = Modifier,

    tabCount: Int = 1,
    onTabSwitcherClick: () -> Unit = {},

    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    onBackClick: () -> Unit = {},
    onForwardClick: () -> Unit = {},

    isMenuOpen: Boolean = false,
    onMenuButtonClick: () -> Unit = {},
    onMenuDismiss: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onLibraryClick: () -> Unit = {},
    onOpenHistoryLibrary: () -> Unit = onLibraryClick,
    onPasswordsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNewIncognitoTabClick: () -> Unit = {},
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showAllShortcuts by remember { mutableStateOf(false) }
    var showEnginePicker by remember { mutableStateOf(false) }
    var menuAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    val onBackground = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val isDarkChrome = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val tileFill = if (isDarkChrome) Color(0xFF141414) else Color.White
    val hairline = if (isDarkChrome) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)
    val accent = onBackground

    // The new-tab surface's starfield background is meant to run fully
    // edge-to-edge with nothing drawn over it up top. Merely calling
    // hide(statusBars()) leaves the OEM-painted scrim color
    // (SystemBarStyle.dark from MainActivity) visible as a strip on some
    // devices, so instead make the status bar background itself transparent
    // while this surface is shown, and put it back on dispose so every other
    // screen (which does expect the dark scrim) is unaffected.
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context as? android.app.Activity
        val window = activity?.window
        val originalStatusBarColor = window?.statusBarColor
        window?.statusBarColor = android.graphics.Color.TRANSPARENT
        onDispose {
            originalStatusBarColor?.let { window.statusBarColor = it }
        }
    }

    val solidHome = when (homeBackground) {
        com.wormhole.browser.core.settings.HomeBackground.PHOTON ->
            if (isDarkChrome) Color(0xFF1C1B22) else Color(0xFFF9F9FB)
        com.wormhole.browser.core.settings.HomeBackground.VIOLET -> Color(0xFF2B1066)
        com.wormhole.browser.core.settings.HomeBackground.NAVY -> Color(0xFF0F1B3D)
        com.wormhole.browser.core.settings.HomeBackground.PAPER -> Color(0xFFF4EFE6)
        com.wormhole.browser.core.settings.HomeBackground.DEFAULT ->
            if (isDarkChrome) Color(0xFF0B1A3A) else Color(0xFFF9F9FB)
        else -> null
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(solidHome ?: if (isDarkChrome) Color.Black else Color(0xFFE8F2FF)),
    ) {
        if (solidHome == null) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    if (isDarkChrome) R.drawable.bg_starfield else R.drawable.bg_home_light,
                ),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()

                .padding(horizontal = 20.dp)

                .padding(bottom = 96.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Wormhole",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onBackground,
                )

                Icon(
                    Icons.Default.Shield,
                    contentDescription = "Incognito",
                    tint = onBackground,
                    modifier = Modifier
                        .size(22.dp)
                        .bouncyClickable(onClick = onNewIncognitoTabClick),
                )
            }

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Shortcuts", style = MaterialTheme.typography.titleSmall, color = muted)
                Text(
                    "Show all  >",
                    style = MaterialTheme.typography.labelLarge,
                    color = muted,
                    modifier = Modifier.bouncyClickable(onClick = { showAllShortcuts = true }),
                )
            }
            Spacer(Modifier.height(10.dp))

            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 4,
            ) {
                shortcuts.take(com.wormhole.browser.core.library.ShortcutCatalog.HOME_VISIBLE).forEach { shortcut ->
                    ShortcutTile(
                        shortcut = shortcut,
                        onClick = { onShortcutClick(shortcut) },
                        onRemove = { onShortcutRemove(shortcut) },
                    )
                }
                if (shortcuts.size < com.wormhole.browser.core.library.ShortcutCatalog.MAX) {
                    AddShortcutTile(onClick = { showAddDialog = true })
                }
            }

            Spacer(Modifier.height(22.dp))

            history.firstOrNull()?.let { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Jump back in", style = MaterialTheme.typography.titleSmall, color = muted)
                    Text(
                        "Show all  >",
                        style = MaterialTheme.typography.labelLarge,
                        color = muted,
                        modifier = Modifier.bouncyClickable(onClick = onOpenHistoryLibrary),
                    )
                }
                Spacer(Modifier.height(10.dp))
                ContinueBrowsingCard(
                    entry = entry,
                    onOpen = { onHistoryClick(entry) },
                    onOpenHistory = onOpenHistoryLibrary,
                )
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(16.dp))
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = tileFill,
                border = androidx.compose.foundation.BorderStroke(1.dp, hairline),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .bouncyClickable(onClick = onCommandBarRequested),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.bouncyClickable(onClick = { showEnginePicker = true })) {
                        SearchEngineLogo(engine = searchEngine, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        "Search",
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        modifier = Modifier.weight(1f),
                    )
                    VoiceMicButton(onResult = onVoiceSearch, tint = muted, iconSize = 18.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .border(1.6.dp, onBackground, RoundedCornerShape(8.dp))
                    .bouncyClickable(onClick = onTabSwitcherClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tabCount.coerceAtLeast(0).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = onBackground,
                )
            }
            Icon(
                Icons.Default.Menu,
                contentDescription = "Menu",
                tint = onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .onGloballyPositioned { coords ->
                        menuAnchorBounds = coords.boundsInWindow()
                    }
                    .bouncyClickable(onClick = onMenuButtonClick),
            )
        }
        HomeToolsMenu(
            isExpanded = isMenuOpen,
            onDismiss = onMenuDismiss,
            onDownloadsClick = onDownloadsClick,
            onLibraryClick = onLibraryClick,
            onHistoryClick = onOpenHistoryLibrary,
            onPasswordsClick = onPasswordsClick,
            onSettingsClick = onSettingsClick,
            onNewIncognitoTabClick = onNewIncognitoTabClick,
            onAssistantClick = onAskWormHoleClick,
            anchorBounds = menuAnchorBounds,
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

    if (showAddDialog) {
        AddShortcutDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, url ->
                onAddShortcut(title, url)
                showAddDialog = false
            },
        )
    }
    if (showAllShortcuts) {
        AlertDialog(
            onDismissRequest = { showAllShortcuts = false },
            title = { Text("Shortcuts") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(shortcuts.size) { index ->
                        val shortcut = shortcuts[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable(onClick = {
                                    onShortcutClick(shortcut)
                                    showAllShortcuts = false
                                })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ShortcutGlyph(shortcut)
                            Column(Modifier.weight(1f)) {
                                Text(shortcut.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(shortcut.url, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                "Remove",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.bouncyClickable(onClick = { onShortcutRemove(shortcut) }),
                            )
                        }
                    }
                    if (shortcuts.size < com.wormhole.browser.core.library.ShortcutCatalog.MAX) {
                        item {
                            Text(
                                "Add shortcut",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .bouncyClickable(onClick = {
                                        showAllShortcuts = false
                                        showAddDialog = true
                                    }),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllShortcuts = false }) { Text("Close") }
            },
        )
    }
}


@Composable
private fun homeIsDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.45f

@Composable
private fun homeTileFill(): Color = if (homeIsDark()) Color(0xFF141414) else Color.White

@Composable
private fun homeHairline(): Color =
    if (homeIsDark()) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)

@Composable
private fun TrendingCard(
    terms: List<String>,
    onTermClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = homeTileFill(),
        border = androidx.compose.foundation.BorderStroke(1.dp, homeHairline()),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    Text("Trending searches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            terms.forEachIndexed { index, term ->
                if (index > 0) {
                    androidx.compose.material3.HorizontalDivider(color = homeHairline())
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyClickable(onClick = { onTermClick(term) })
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(term, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    entry: LibraryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = entry.title.ifBlank { entry.url }
    val host = try { URI(entry.url).host?.removePrefix("www.") ?: entry.url } catch (_: Exception) { entry.url }
    val color = ShortcutPalette[(entry.url.hashCode().let { if (it < 0) -it else it }) % ShortcutPalette.size]
    val thumbnail = HistoryThumbnailCache.get(entry.url)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = homeTileFill(),
        border = androidx.compose.foundation.BorderStroke(1.dp, homeHairline()),
        modifier = modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(color.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumbnail != null) {
                        androidx.compose.foundation.Image(
                            bitmap = thumbnail.asImageBitmap(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().background(color = Color.Transparent, shape = RoundedCornerShape(10.dp)),
                        )
                    } else {
                        Text(label.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = color, fontWeight = FontWeight.SemiBold)
                    }
                }
                Column {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ContinueBrowsingCard(
    entry: LibraryEntry,
    onOpen: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(entry.url) {
        FaviconCache.fetchAndCache(entry.url)
    }
    val favicon = FaviconCache.get(entry.url)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (homeIsDark()) Color(0xFF141414) else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (homeIsDark()) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (homeIsDark()) Color(0xFF222222) else Color(0xFFF2F2F2),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (favicon != null && !favicon.isRecycled) {
                    androidx.compose.foundation.Image(
                        bitmap = favicon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Text(
                        entry.title.firstOrNull()?.uppercase() ?: "W",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Continue browsing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    entry.title.ifBlank { entry.url },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "History",
                modifier = Modifier.bouncyClickable(onClick = onOpenHistory),
            )
        }
    }
}

@Composable
private fun ShortcutGlyph(shortcut: ShortcutEntry, modifier: Modifier = Modifier) {
    val favicon = FaviconCache.get(shortcut.url)
    val host = try { URI(shortcut.url).host?.removePrefix("www.")?.lowercase() ?: "" } catch (_: Exception) { "" }
    val tint = Color.White

    when {
        favicon != null -> androidx.compose.foundation.Image(
            bitmap = favicon.asImageBitmap(),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = modifier.size(30.dp),
        )
        "google" in host -> GoogleGGlyph(modifier = modifier.size(24.dp))
        "youtube" in host -> Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color(0xFFFF0000),
            modifier = modifier.size(26.dp),
        )
        host == "x.com" || "twitter" in host -> XGlyph(tint = tint, modifier = modifier.size(20.dp))
        "github" in host -> GitHubGlyph(tint = tint, modifier = modifier.size(24.dp))
        "reddit" in host -> Icon(
            Icons.Filled.Forum,
            contentDescription = null,
            tint = Color(0xFFFF4500),
            modifier = modifier.size(24.dp),
        )
        else -> Text(
            text = shortcut.monogram(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = shortcut.monogramColor(),
            modifier = modifier,
        )
    }
}

/** Simple two-stroke "X" mark matching X (Twitter)'s wordmark shape. */
@Composable
private fun XGlyph(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.16f
        val cap = androidx.compose.ui.graphics.StrokeCap.Round
        val inset = size.minDimension * 0.08f
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(inset, inset),
            end = androidx.compose.ui.geometry.Offset(size.width - inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = cap,
        )
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(size.width - inset, inset),
            end = androidx.compose.ui.geometry.Offset(inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = cap,
        )
    }
}

/** Minimal Octocat-style silhouette for GitHub: a rounded head with two ears. */
@Composable
private fun GitHubGlyph(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val r = size.minDimension * 0.42f
        drawCircle(color = tint, radius = r, center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f))
        val earRadius = size.minDimension * 0.14f
        drawCircle(color = tint, radius = earRadius, center = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.2f))
        drawCircle(color = tint, radius = earRadius, center = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.2f))
    }
}

@Composable
private fun ShortcutTile(
    shortcut: ShortcutEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemoveConfirm by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    androidx.compose.runtime.LaunchedEffect(shortcut.url) {
        FaviconCache.fetchAndCache(shortcut.url)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(58.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(homeTileFill(), CircleShape)
                .border(1.dp, homeHairline(), CircleShape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,

                    onLongClick = { showRemoveConfirm = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            ShortcutGlyph(shortcut = shortcut)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = shortcut.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove shortcut?") },
            text = { Text("\"${shortcut.title}\" will be removed from your home screen.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    onRemove()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AddShortcutTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(58.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(homeTileFill(), CircleShape)
                .border(1.dp, homeHairline(), CircleShape)
                .bouncyClickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add shortcut",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Add",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddShortcutDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    val valid = com.wormhole.browser.core.library.ShortcutCatalog.isRealUrl(url)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add shortcut") },
        text = {
            Column {
                Text(
                    "Enter a real website URL. You can save up to 22 shortcuts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("chatgpt.com") },
                    singleLine = true,
                    isError = url.isNotBlank() && !valid,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (valid) onConfirm(hostLabelFor(url), com.wormhole.browser.core.library.ShortcutCatalog.normalizeUrl(url))
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(hostLabelFor(url), com.wormhole.browser.core.library.ShortcutCatalog.normalizeUrl(url)) },
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

private fun hostLabelFor(input: String): String = try {
    val uri = URI(normalizeUrl(input))
    (uri.host ?: input).removePrefix("www.")
} catch (_: Exception) {
    input.trim()
}

private fun ShortcutEntry.monogram(): String {
    val label = title.ifBlank {
        try { URI(url).host ?: url } catch (_: Exception) { url }
    }
    return label.removePrefix("www.").firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

private val ShortcutPalette = listOf(WormHoleSky, WormHoleViolet, WormHoleMint, WormHoleGold)

private fun ShortcutEntry.monogramColor(): Color =
    ShortcutPalette[(url.hashCode().let { if (it < 0) -it else it }) % ShortcutPalette.size]
