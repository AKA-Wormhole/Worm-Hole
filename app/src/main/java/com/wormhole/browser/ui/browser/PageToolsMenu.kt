package com.wormhole.browser.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.wormhole.browser.ui.theme.WormHoleMotion
import com.wormhole.browser.ui.theme.bouncyClickable

@Composable
fun PageToolsMenu(
    isExpanded: Boolean,
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    onBackClick: () -> Unit = {},
    onForwardClick: () -> Unit = {},
    onReloadClick: () -> Unit,
    isDesktopSiteEnabled: Boolean,
    onDismiss: () -> Unit,
    onDownloadsClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onHistoryClick: () -> Unit = onLibraryClick,
    onPasswordsClick: () -> Unit = {},
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
    onShareClick: () -> Unit = {},
    onCopyLinkClick: () -> Unit = {},
) {
    if (!isExpanded) return

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SheetTile(Modifier.weight(1f), Icons.Default.History, "History") { onHistoryClick(); onDismiss() }
                        SheetTile(Modifier.weight(1f), Icons.Default.Bookmarks, "Bookmarks") { onLibraryClick(); onDismiss() }
                        SheetTile(Modifier.weight(1f), Icons.Default.Download, "Downloads") { onDownloadsClick(); onDismiss() }
                        SheetTile(Modifier.weight(1f), Icons.Default.Password, "Passwords") { onPasswordsClick(); onDismiss() }
                    }

                    MenuDivider()

                    MenuItem(text = "Back", icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = { onBackClick(); onDismiss() })
                    MenuItem(text = "Forward", icon = Icons.AutoMirrored.Filled.ArrowForward, onClick = { onForwardClick(); onDismiss() })
                    MenuItem(text = "Reload", icon = Icons.Default.Refresh, onClick = { onReloadClick(); onDismiss() })

                    MenuDivider()

                    MenuItem(text = "Share", icon = Icons.Default.Share, onClick = { onShareClick(); onDismiss() })
                    MenuItem(text = "Copy link", icon = Icons.Default.ContentCopy, onClick = { onCopyLinkClick(); onDismiss() })
                    MenuItem(
                        text = if (isDesktopSiteEnabled) "Request mobile site" else "Request desktop site",
                        icon = Icons.Default.Computer,
                        onClick = { onRequestDesktopSiteClick(); onDismiss() },
                    )
                    MenuItem(text = "Translate", icon = Icons.Default.Translate, onClick = { onTranslateClick(); onDismiss() })
                    MenuItem(text = "Find in page", icon = Icons.Default.Search, onClick = { onFindInPageClick(); onDismiss() })

                    MenuDivider()

                    MenuItem(text = "Add bookmark", icon = Icons.Default.BookmarkAdd, onClick = { onBookmarkClick(); onDismiss() })
                    MenuItem(text = "Add to Shortcuts", icon = Icons.AutoMirrored.Filled.AddToHomeScreen, onClick = { onAddShortcutClick(); onDismiss() })
                    MenuItem(text = "Duplicate tab", icon = Icons.Default.FileCopy, onClick = { onDuplicateTabClick(); onDismiss() })
                    MenuItem(text = "Reopen closed tab", icon = Icons.Default.Restore, onClick = { onReopenClosedTabClick(); onDismiss() })
                    MenuItem(text = "New incognito tab", icon = Icons.Default.Shield, onClick = { onNewIncognitoTabClick(); onDismiss() })

                    MenuDivider()

                    MenuItem(text = "Settings", icon = Icons.Default.Settings, onClick = { onSettingsClick(); onDismiss() })
                }
    }
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    )
}

@Composable
private fun MenuItem(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            icon != null -> Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            iconPainter != null -> Icon(painter = iconPainter, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

