package com.knot.browser.ui.browser

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The 3-dot page-tools menu that sits to the left of the bottom search
 * bar. Groups actions that act on the *current page* -- as opposed to
 * the sidebar, which is about navigating between tabs/Spaces -- so
 * Downloads, desktop-site, Translate, and Assistant all live here rather
 * than being scattered across separate buttons.
 *
 * Back/Forward/Reload also live here (as the first three items, above a
 * divider) rather than as standalone buttons on the bottom bar -- they're
 * used far less often than the search pill, so putting them behind this
 * menu keeps the bar itself uncluttered and gives the pill more room.
 */
@Composable
fun PageToolsMenu(
    isExpanded: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onReloadClick: () -> Unit,
    isDesktopSiteEnabled: Boolean,
    onDismiss: () -> Unit,
    onDownloadsClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onDuplicateTabClick: () -> Unit,
    onReopenClosedTabClick: () -> Unit,
    onRequestDesktopSiteClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onAssistantClick: () -> Unit,
) {
    DropdownMenu(
        expanded = isExpanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(4.dp),
    ) {
        DropdownMenuItem(
            text = { Text("Back") },
            leadingIcon = { Icon(Icons.Default.ArrowBack, contentDescription = null) },
            enabled = canGoBack,
            onClick = onBackClick,
        )
        DropdownMenuItem(
            text = { Text("Forward") },
            leadingIcon = { Icon(Icons.Default.ArrowForward, contentDescription = null) },
            enabled = canGoForward,
            onClick = onForwardClick,
        )
        DropdownMenuItem(
            text = { Text("Reload") },
            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            onClick = onReloadClick,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(
            text = { Text("Downloads") },
            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
            onClick = onDownloadsClick,
        )
        DropdownMenuItem(
            text = { Text("Library") },
            leadingIcon = { Icon(Icons.Default.Bookmarks, null) },
            onClick = onLibraryClick,
        )
        DropdownMenuItem(
            text = { Text("Add bookmark") },
            leadingIcon = { Icon(Icons.Default.BookmarkAdd, null) },
            onClick = onBookmarkClick,
        )
        DropdownMenuItem(
            text = { Text("Duplicate tab") },
            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
            onClick = onDuplicateTabClick,
        )
        DropdownMenuItem(
            text = { Text("Reopen closed tab") },
            leadingIcon = { Icon(Icons.Default.Restore, null) },
            onClick = onReopenClosedTabClick,
        )
        DropdownMenuItem(
            text = {
                Text(if (isDesktopSiteEnabled) "Request mobile site" else "Request desktop site")
            },
            leadingIcon = { Icon(Icons.Default.Computer, contentDescription = null) },
            onClick = onRequestDesktopSiteClick,
        )
        DropdownMenuItem(
            text = { Text("Translate") },
            leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) },
            onClick = onTranslateClick,
        )
        DropdownMenuItem(
            text = { Text("Summarize with Assistant") },
            leadingIcon = {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            onClick = onAssistantClick,
        )
    }
}
