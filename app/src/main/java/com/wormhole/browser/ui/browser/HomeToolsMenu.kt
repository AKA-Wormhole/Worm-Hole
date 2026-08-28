package com.wormhole.browser.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.wormhole.browser.ui.theme.WormHoleMotion
import com.wormhole.browser.ui.theme.WormHoleSurface
import com.wormhole.browser.ui.theme.bouncyClickable

@Composable
fun HomeToolsMenu(
    isExpanded: Boolean,
    onDismiss: () -> Unit,
    onDownloadsClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onHistoryClick: () -> Unit = onLibraryClick,
    onPasswordsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewIncognitoTabClick: () -> Unit,
    onAssistantClick: () -> Unit = {},
    // Bounds (in root/window coordinates) of the menu button that opened
    // this, captured via Modifier.onGloballyPositioned at the call site.
    // Without this, the Popup has no real anchor -- since HomeToolsMenu is
    // placed as a plain sibling composable rather than nested inside the
    // menu icon itself, Compose's default anchorBounds resolves to the
    // bounds of whatever large parent layout it happens to sit in (e.g. the
    // whole screen), not the small icon, which is why the menu used to pop
    // up in the wrong place (top-left of the screen) instead of anchored
    // above the menu button that was actually tapped.
    anchorBounds: Rect? = null,
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
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        HomeQuickAccessIcon(icon = Icons.Default.Bookmarks, label = "Bookmarks", onClick = { onLibraryClick(); onDismiss() })
                        HomeQuickAccessIcon(icon = Icons.Default.History, label = "History", onClick = { onHistoryClick(); onDismiss() })
                        HomeQuickAccessIcon(icon = Icons.Default.Download, label = "Downloads", onClick = { onDownloadsClick(); onDismiss() })
                        HomeQuickAccessIcon(icon = Icons.Default.Password, label = "Passkeys", onClick = { onPasswordsClick(); onDismiss() })
                    }

                    HomeMenuDivider()

                    HomeMenuItem(text = "New incognito tab", icon = Icons.Default.Shield, onClick = { onNewIncognitoTabClick(); onDismiss() })
                    HomeMenuItem(text = "Settings", icon = Icons.Default.Settings, onClick = { onSettingsClick(); onDismiss() })
                }
    }
}

@Composable
private fun HomeMenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    )
}

@Composable
private fun HomeMenuItem(
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

@Composable
private fun HomeQuickAccessIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(WormHoleSurface.FillRaised, CircleShape)
                .bouncyClickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
