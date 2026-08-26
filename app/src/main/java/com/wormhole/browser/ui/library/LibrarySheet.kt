package com.wormhole.browser.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wormhole.browser.core.library.LibraryEntry
import com.wormhole.browser.ui.theme.WormHoleSurface
import com.wormhole.browser.ui.theme.bouncyClickable

@Composable
fun LibrarySheet(
    bookmarks: List<LibraryEntry>,
    history: List<LibraryEntry>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onClearHistory: () -> Unit,
    /** 0 = Bookmarks, 1 = History */
    initialTab: Int = 0,
) {
    var selected by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)

            .statusBarsPadding(),
    ) {
        com.wormhole.browser.ui.settings.SettingsHeader(title = "Library", onBack = onDismiss)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryTabPill(
                label = "Bookmarks",
                icon = Icons.Default.Bookmark,
                selected = selected == 0,
                onClick = { selected = 0 },
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryTabPill(
                    label = "History",
                    icon = Icons.Default.History,
                    selected = selected == 1,
                    onClick = { selected = 1 },
                    modifier = Modifier.weight(1f),
                )
                if (selected == 1 && history.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(WormHoleSurface.Fill, CircleShape)
                            .bouncyClickable(onClick = onClearHistory),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear history",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        val entries = if (selected == 0) bookmarks else history
        AnimatedContent(
            targetState = selected,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "library-tab",
        ) { tab ->
            val tabEntries = if (tab == 0) bookmarks else history
            if (tabEntries.isEmpty()) {
                LibraryEmptyState(isBookmarks = tab == 0)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tabEntries, key = { "${it.url}-${it.createdAt}" }) { entry ->
                        LibraryRow(entry, tab == 0, onOpen, onRemoveBookmark)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = WormHoleSurface.PillShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else WormHoleSurface.Fill,
        border = WormHoleSurface.border(),
        modifier = modifier.bouncyClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryEmptyState(isBookmarks: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(WormHoleSurface.Fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isBookmarks) Icons.Default.BookmarkBorder else Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = if (isBookmarks) "No bookmarks yet" else "No history yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = if (isBookmarks) "Pages you bookmark will show up here." else "Pages you visit will show up here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun LibraryRow(
    entry: LibraryEntry,
    bookmark: Boolean,
    onOpen: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
) {
    Surface(
        shape = WormHoleSurface.CardShape,
        color = WormHoleSurface.Fill,
        border = WormHoleSurface.border(),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = { onOpen(entry.url) }),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(WormHoleSurface.FillRaised, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.url,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (bookmark) {
                IconButton(onClick = { onRemoveBookmark(entry.url) }) {
                    Icon(Icons.Default.Bookmark, contentDescription = "Remove bookmark")
                }
            }
        }
    }
}
