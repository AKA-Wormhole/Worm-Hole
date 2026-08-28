@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wormhole.browser.ui.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wormhole.browser.core.library.OmniboxSuggestion
import com.wormhole.browser.core.library.SearchQuerySuggestion
import com.wormhole.browser.core.library.ShortcutEntry
import com.wormhole.browser.core.settings.SearchEngine
import com.wormhole.browser.core.webview.FaviconCache
import com.wormhole.browser.ui.theme.WormHoleMotion
import com.wormhole.browser.ui.theme.WormHoleSurface
import com.wormhole.browser.ui.theme.bouncyClickable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CommandBarMode { SEARCH, AI }

private val DefaultRecentSearches = listOf(
    "Wormhole AI",
    "Space exploration news",
    "Android 16 features",
    "Minimal desk setup",
    "Best cafes in London",
)

private val DefaultTrendingSearches = listOf(
    "iPhone 16 Pro",
    "Tesla Model 3 2024",
    "OpenAI GPT-5",
    "India vs Sri Lanka highlights",
    "WWDC 2024",
    "SpaceX Starship launch",
)

private val DefaultQuickAccess = listOf(
    ShortcutEntry("Google", "https://www.google.com/", 0L),
    ShortcutEntry("YouTube", "https://www.youtube.com/", 0L),
    ShortcutEntry("Reddit", "https://www.reddit.com/", 0L),
    ShortcutEntry("X", "https://x.com/", 0L),
    ShortcutEntry("Wikipedia", "https://www.wikipedia.org/", 0L),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommandBar(
    isOpen: Boolean,
    query: String,
    mode: CommandBarMode,
    onModeChange: (CommandBarMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchOmnibox: suspend (String) -> List<OmniboxSuggestion> = { emptyList() },
    onFetchSearchSuggestions: suspend (String) -> List<SearchQuerySuggestion> = { emptyList() },
    searchEngine: SearchEngine = SearchEngine.DEFAULT,
    recentSearches: List<String> = emptyList(),
    shortcuts: List<ShortcutEntry> = emptyList(),
    onFillQuery: (String) -> Unit = onQueryChange,
    onClearRecentSearches: () -> Unit = {},
    onShortcutClick: (ShortcutEntry) -> Unit = {},
    onAddShortcut: (title: String, url: String) -> Unit = { _, _ -> },
    hasStoredRecentSearches: Boolean = false,
    onEngineSelected: (SearchEngine) -> Unit = {},
    trendingSearches: List<String> = emptyList(),
) {
    var showAddShortcut by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0.94f,
        animationSpec = WormHoleMotion.popup(),
        label = "commandBarScale",
    )
    val slide by animateFloatAsState(
        targetValue = if (isOpen) 0f else -28f,
        animationSpec = WormHoleMotion.popup(),
        label = "commandBarSlide",
    )

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(animationSpec = WormHoleMotion.overlay()) +
            scaleIn(initialScale = 0.96f, animationSpec = WormHoleMotion.popup()) +
            slideInVertically(animationSpec = WormHoleMotion.popup()) { 48 },
        exit = fadeOut(animationSpec = WormHoleMotion.fadeOut()) +
            scaleOut(targetScale = 0.97f, animationSpec = WormHoleMotion.snappy()) +
            slideOutVertically(animationSpec = WormHoleMotion.snappy()) { 32 },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationY = slide
                    },
            ) {
                AnimatedContent(
                    targetState = query.isBlank(),
                    transitionSpec = {
                        (fadeIn(WormHoleMotion.fadeIn()) + slideInVertically(WormHoleMotion.popup()) { 16 }) togetherWith
                            (fadeOut(WormHoleMotion.fadeOut()) + slideOutVertically(WormHoleMotion.snappy()) { -8 })
                    },
                    label = "commandBarBody",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { emptyQuery ->
                    if (emptyQuery) {
                        IdleCommandBody(
                            mode = mode,
                            onModeChange = onModeChange,
                            recentSearches = when {
                                recentSearches.isNotEmpty() -> recentSearches
                                hasStoredRecentSearches -> emptyList()
                                else -> emptyList()
                            },
                            onRecentClick = onSubmit,
                            onFillQuery = onFillQuery,
                            onClearRecentSearches = onClearRecentSearches,
                            trending = trendingSearches,
                            onTrendingClick = onSubmit,
                            shortcuts = shortcuts,
                            onShortcutClick = onShortcutClick,
                            onAddShortcut = { showAddShortcut = true },
                        )
                    } else {
                        SuggestionCommandBody(
                            query = query,
                            mode = mode,
                            onSubmit = onSubmit,
                            onFillQuery = onFillQuery,
                            onSearchOmnibox = onSearchOmnibox,
                            onFetchSearchSuggestions = onFetchSearchSuggestions,
                        )
                    }
                }

                SearchHeader(
                    query = query,
                    mode = mode,
                    searchEngine = searchEngine,
                    onQueryChange = onQueryChange,
                    onSubmit = onSubmit,
                    onDismiss = onDismiss,
                    requestFocus = isOpen,
                    onEngineSelected = onEngineSelected,
                )
            }
        }
    }

    if (showAddShortcut) {
        AddQuickAccessDialog(
            onDismiss = { showAddShortcut = false },
            onConfirm = { title, url ->
                onAddShortcut(title, url)
                showAddShortcut = false
            },
        )
    }
}

@Composable
private fun SearchHeader(
    query: String,
    mode: CommandBarMode,
    searchEngine: SearchEngine,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    requestFocus: Boolean,
    onEngineSelected: (SearchEngine) -> Unit = {},
    trendingSearches: List<String> = emptyList(),
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val accent = MaterialTheme.colorScheme.primary
    var showEnginePicker by remember { mutableStateOf(false) }

    var fieldValue by remember(requestFocus) {
        mutableStateOf(TextFieldValue(text = query, selection = TextRange(0, query.length)))
    }
    LaunchedEffect(query) {
        if (query != fieldValue.text) {
            fieldValue = TextFieldValue(query, selection = TextRange(query.length))
        }
    }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            kotlinx.coroutines.delay(16)
            keyboardController?.show()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surface,
            border = WormHoleSurface.border(),
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.bouncyClickable(onClick = { showEnginePicker = true }),
                ) {
                    SearchEngineLogo(engine = searchEngine, modifier = Modifier.size(20.dp))
                }
                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = accent,
                        backgroundColor = accent.copy(alpha = 0.28f),
                    ),
                ) {
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { newValue ->
                            fieldValue = newValue
                            if (newValue.text != query) onQueryChange(newValue.text)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            keyboardController?.hide()
                            onSubmit(fieldValue.text)
                        }),
                        decorationBox = { inner ->
                            if (fieldValue.text.isEmpty()) {
                                Text(
                                    "Search or type URL",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                }
                if (fieldValue.text.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .bouncyClickable {
                                fieldValue = TextFieldValue()
                                onQueryChange("")
                            },
                    )
                }
                VoiceMicButton(
                    onResult = { spoken ->
                        fieldValue = TextFieldValue(spoken, selection = TextRange(spoken.length))
                        onQueryChange(spoken)
                        onSubmit(spoken)
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconSize = 20.dp,
                )
            }
        }
        Text(
            "Cancel",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.bouncyClickable(onClick = {
                keyboardController?.hide()
                onDismiss()
            }),
        )
    }
    if (showEnginePicker) {
        SearchEnginePicker(
            current = searchEngine,
            onSelected = onEngineSelected,
            onDismiss = { showEnginePicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleCommandBody(
    mode: CommandBarMode,
    onModeChange: (CommandBarMode) -> Unit,
    recentSearches: List<String>,
    onRecentClick: (String) -> Unit,
    onFillQuery: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    trending: List<String>,
    onTrendingClick: (String) -> Unit,
    shortcuts: List<ShortcutEntry>,
    onShortcutClick: (ShortcutEntry) -> Unit,
    onAddShortcut: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        SectionHeader(
            title = "Recent searches",
            action = "Clear all",
            onAction = onClearRecentSearches,
        )
        if (recentSearches.isEmpty()) {
            Text(
                "No recent searches",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        } else {
            recentSearches.take(6).forEachIndexed { index, term ->
                if (index > 0) {
                    HorizontalDivider(
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                RecentSearchRow(
                    term = term,
                    onClick = { onRecentClick(term) },
                    onFill = { onFillQuery(term) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        // trending removed
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2,
        ) {
            trending.forEach { term ->
                TrendingChip(
                    term = term,
                    onClick = { onTrendingClick(term) },
                    modifier = Modifier.fillMaxWidth(0.48f),
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionHeader(title = "Quick access", action = "Edit", onAction = onAddShortcut)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            shortcuts.take(5).forEach { shortcut ->
                QuickAccessTile(
                    shortcut = shortcut,
                    onClick = { onShortcutClick(shortcut) },
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.bouncyClickable(onClick = onAddShortcut),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(WormHoleSurface.FillRaised)
                        .border(1.dp, WormHoleSurface.HairlineBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = onSurface, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text("Add", style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SuggestionCommandBody(
    query: String,
    mode: CommandBarMode,
    onSubmit: (String) -> Unit,
    onFillQuery: (String) -> Unit,
    onSearchOmnibox: suspend (String) -> List<OmniboxSuggestion>,
    onFetchSearchSuggestions: suspend (String) -> List<SearchQuerySuggestion>,
) {
    var suggestions by remember { mutableStateOf<List<OmniboxSuggestion>>(emptyList()) }
    var searchSuggestions by remember { mutableStateOf<List<SearchQuerySuggestion>>(emptyList()) }

    LaunchedEffect(query, mode) {
        if (mode != CommandBarMode.SEARCH || query.isBlank()) {
            suggestions = emptyList()
            searchSuggestions = emptyList()
            return@LaunchedEffect
        }
        delay(120)
        coroutineScope {
            launch { suggestions = onSearchOmnibox(query) }
            launch { searchSuggestions = onFetchSearchSuggestions(query) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(suggestions, key = { "bm-${it.url}" }) { suggestion ->
            OmniboxSuggestionRow(suggestion = suggestion, onClick = { onSubmit(suggestion.url) })
        }
        items(searchSuggestions, key = { "sq-${it.text}" }) { suggestion ->
            SearchQuerySuggestionRow(
                suggestion = suggestion,
                onClick = { onSubmit(suggestion.text) },
                onFill = { onFillQuery(suggestion.text) },
            )
        }
        if (suggestions.isEmpty() && searchSuggestions.isEmpty() && query.isNotBlank()) {
            item("typed") {
                SearchQuerySuggestionRow(
                    suggestion = SearchQuerySuggestion(query),
                    onClick = { onSubmit(query) },
                    onFill = {},
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.bouncyClickable(onClick = onAction),
            )
        }
    }
}

@Composable
private fun RecentSearchRow(
    term: String,
    onClick: () -> Unit,
    onFill: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            term,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.NorthWest,
            contentDescription = "Fill search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .bouncyClickable(onClick = onFill),
        )
    }
}

@Composable
private fun TrendingChip(
    term: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(WormHoleSurface.FillRaised)
            .border(1.dp, WormHoleSurface.HairlineBorder, RoundedCornerShape(12.dp))
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.TrendingUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            term,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun QuickAccessTile(
    shortcut: ShortcutEntry,
    onClick: () -> Unit,
) {
    LaunchedEffect(shortcut.url) {
        FaviconCache.fetchAndCache(shortcut.url)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .bouncyClickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(WormHoleSurface.FillRaised)
                .border(1.dp, WormHoleSurface.HairlineBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val favicon = FaviconCache.get(shortcut.url)
            if (favicon != null && !favicon.isRecycled) {
                androidx.compose.foundation.Image(
                    bitmap = favicon.asImageBitmap(),
                    contentDescription = shortcut.title,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Text(
                    text = shortcut.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            shortcut.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ModeSwitcherPill(
    mode: CommandBarMode,
    onModeChange: (CommandBarMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = WormHoleSurface.PillShape,
        color = WormHoleSurface.Fill,
        border = WormHoleSurface.border(),
        modifier = modifier
            .wrapContentWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeSwitcherSegment(
                label = "Search",
                selected = mode == CommandBarMode.SEARCH,
                onClick = { onModeChange(CommandBarMode.SEARCH) },
            )
            ModeSwitcherSegment(
                label = "AI",
                selected = mode == CommandBarMode.AI,
                onClick = { onModeChange(CommandBarMode.AI) },
            )
        }
    }
}

@Composable
private fun ModeSwitcherSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier
            .height(34.dp)
            .bouncyClickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(72.dp)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun SearchQuerySuggestionRow(
    suggestion: SearchQuerySuggestion,
    onClick: () -> Unit,
    onFill: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search suggestion",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = suggestion.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.NorthWest,
            contentDescription = "Fill search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(16.dp)
                .bouncyClickable(onClick = onFill),
        )
    }
}

@Composable
private fun OmniboxSuggestionRow(
    suggestion: OmniboxSuggestion,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (suggestion.isBookmark) Icons.Default.Star else Icons.Default.History,
            contentDescription = if (suggestion.isBookmark) "Bookmark" else "History",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.title.ifBlank { suggestion.url },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = suggestion.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AddQuickAccessDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String) -> Unit,
) {
    var raw by remember { mutableStateOf("") }
    val valid = com.wormhole.browser.core.library.ShortcutCatalog.isRealUrl(raw)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add shortcut") },
        text = {
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                placeholder = { Text("example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (valid) onConfirm(labelForUrl(raw), com.wormhole.browser.core.library.ShortcutCatalog.normalizeUrl(raw))
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(labelForUrl(raw), com.wormhole.browser.core.library.ShortcutCatalog.normalizeUrl(raw)) },
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun normalizeHttpUrl(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}

private fun labelForUrl(input: String): String = try {
    val host = java.net.URI(normalizeHttpUrl(input)).host ?: input
    host.removePrefix("www.")
} catch (_: Exception) {
    input.trim()
}
