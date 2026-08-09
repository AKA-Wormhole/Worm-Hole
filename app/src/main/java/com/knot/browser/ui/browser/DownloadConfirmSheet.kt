package com.knot.browser.ui.browser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.knot.browser.ui.theme.KnotMotion
import kotlinx.coroutines.launch

/**
 * Shown before a download actually starts (BrowserScreen intercepts
 * WebViewCallbacks.onDownloadRequested and opens this instead of calling
 * DownloadHandler.enqueue immediately) -- matches the confirm-before-
 * download pattern real browsers use, and gives the person a chance to
 * see the filename/size/source and back out. Confirming here is what
 * actually triggers DownloadHandler.enqueue.
 *
 * The sheet genuinely slides up from off-screen with [KnotMotion.bouncy]
 * (a real Animatable driving its own y-offset, not just Compose's default
 * appear/disappear) and the scrim fades in underneath it -- dismissing
 * via the scrim tap or Cancel plays the same slide back out before the
 * composable actually leaves composition.
 */
@Composable
fun DownloadConfirmSheet(
    fileName: String,
    sourceUrl: String,
    contentLength: Long,
    mimeType: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var sheetHeightPx by remember { mutableStateOf(0) }
    val offsetFraction = remember { Animatable(1f) } // 1f = fully off-screen (below), 0f = resting
    val scrimAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        launch { scrimAlpha.animateTo(1f, animationSpec = tween(180)) }
        offsetFraction.animateTo(0f, animationSpec = KnotMotion.bouncy())
    }

    fun dismissAnimated(after: () -> Unit) {
        scope.launch {
            launch { scrimAlpha.animateTo(0f, animationSpec = tween(140)) }
            offsetFraction.animateTo(1f, animationSpec = KnotMotion.settled())
            after()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f * scrimAlpha.value))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { dismissAnimated(onDismiss) },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { sheetHeightPx = it.size.height }
                .offset { IntOffset(x = 0, y = (offsetFraction.value * sheetHeightPx).toInt()) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (contentLength > 0) formatSize(contentLength) else mimeType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = sourceUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 16.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = { dismissAnimated(onDismiss) }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(onClick = { dismissAnimated(onConfirm) }, modifier = Modifier.weight(1f)) {
                        Text("Download")
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}
