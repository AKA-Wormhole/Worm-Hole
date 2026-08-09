package com.knot.browser.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knot.browser.core.library.LibraryEntry

@Composable
fun LibrarySheet(
    bookmarks: List<LibraryEntry>,
    history: List<LibraryEntry>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Library", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        TabRow(selectedTabIndex = selected) {
            Tab(selected == 0, { selected = 0 }, text = { Text("Bookmarks") }, icon = { Icon(Icons.Default.Bookmark, null) })
            Tab(selected == 1, { selected = 1 }, text = { Text("History") }, icon = { Icon(Icons.Default.History, null) })
        }
        if (selected == 1) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClearHistory) { Icon(Icons.Default.DeleteSweep, "Clear history") }
            }
        }
        val entries = if (selected == 0) bookmarks else history
        if (entries.isEmpty()) {
            Text("Nothing here yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(28.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(entries, key = { "${it.url}-${it.createdAt}" }) { entry ->
                    LibraryRow(entry, selected == 0, onOpen, onRemoveBookmark)
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    entry: LibraryEntry,
    bookmark: Boolean,
    onOpen: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(entry.url) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.url, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (bookmark) IconButton(onClick = { onRemoveBookmark(entry.url) }) { Icon(Icons.Default.Bookmark, "Remove bookmark") }
    }
}
