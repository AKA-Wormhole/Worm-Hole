package com.knot.browser.ui.downloads

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knot.browser.core.browser.DownloadEntry
import com.knot.browser.core.browser.DownloadHandler
import com.knot.browser.core.browser.DownloadStatus
import com.knot.browser.ui.theme.KnotMotion
import com.knot.browser.ui.theme.bouncyClickable
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * Knot's own in-app downloads list -- reads real rows out of Android's
 * DownloadManager (see DownloadHandler.queryAll) on a 500ms poll while
 * this screen is visible, so progress bars, statuses, and completions
 * are live rather than a one-shot snapshot. This is the primary surface
 * for "Downloads" in the page-tools menu now; DownloadHandler still
 * offers a fallback to the OS-level Downloads app for anyone who wants
 * that instead.
 */
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var downloads by remember { mutableStateOf(DownloadHandler.queryAll(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            downloads = DownloadHandler.queryAll(context)
            delay(500)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Downloads") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
        )

        if (downloads.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(downloads, key = { it.downloadManagerId }) { entry ->
                    DownloadRow(
                        entry = entry,
                        // Rows spring into their new position with
                        // KnotMotion.bouncy() whenever the list reflows
                        // (a cleared download means everything below it
                        // slides up instead of snapping).
                        modifier = Modifier.animateItem(placementSpec = KnotMotion.bouncy()),
                        onClick = {
                            if (entry.status == DownloadStatus.SUCCESSFUL) {
                                DownloadHandler.openFile(context, entry)
                            }
                        },
                        onCancelOrClear = {
                            if (entry.status == DownloadStatus.RUNNING || entry.status == DownloadStatus.PENDING || entry.status == DownloadStatus.PAUSED) {
                                DownloadHandler.cancel(context, entry.downloadManagerId)
                            } else {
                                DownloadHandler.clear(context, entry.downloadManagerId)
                            }
                            downloads = DownloadHandler.queryAll(context)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = "No downloads yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Files you download from the web will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onCancelOrClear: () -> Unit,
) {
    // A one-shot "pop" when this row's status becomes SUCCESSFUL --
    // remembered per downloadManagerId so it fires once per completion,
    // not on every recomposition while the row is already done.
    val completionScale = remember(entry.downloadManagerId) { Animatable(1f) }
    LaunchedEffect(entry.status, entry.downloadManagerId) {
        if (entry.status == DownloadStatus.SUCCESSFUL) {
            completionScale.animateTo(1.06f, animationSpec = KnotMotion.snappy())
            completionScale.animateTo(1f, animationSpec = KnotMotion.bouncy())
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(completionScale.value)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FileTypeIcon(entry)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.fileName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            StatusLine(entry)

            if (entry.status == DownloadStatus.RUNNING || entry.status == DownloadStatus.PENDING) {
                Spacer(modifier = Modifier.height(6.dp))
                val progress = entry.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        IconButton(onClick = onCancelOrClear) {
            Icon(
                Icons.Default.Close,
                contentDescription = if (entry.status == DownloadStatus.RUNNING) "Cancel download" else "Remove from list",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusLine(entry: DownloadEntry) {
    val text = when (entry.status) {
        DownloadStatus.PENDING -> "Waiting to start…"
        DownloadStatus.RUNNING -> {
            val progress = entry.progress
            if (progress != null) {
                "${(progress * 100).roundToInt()}% • ${formatBytes(entry.bytesDownloaded)} of ${formatBytes(entry.bytesTotal)}"
            } else {
                "Downloading • ${formatBytes(entry.bytesDownloaded)}"
            }
        }
        DownloadStatus.PAUSED -> "Paused"
        DownloadStatus.SUCCESSFUL -> "${formatBytes(entry.bytesTotal)} • ${formatDate(entry.lastModifiedMillis)}"
        DownloadStatus.FAILED -> "Failed — tap the × to remove"
    }
    val color = if (entry.status == DownloadStatus.FAILED) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun FileTypeIcon(entry: DownloadEntry) {
    val mime = entry.mimeType.orEmpty()
    val icon = when {
        mime.startsWith("image/") -> Icons.Default.Image
        mime == "application/pdf" -> Icons.Default.PictureAsPdf
        else -> Icons.Default.Description
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (entry.status) {
            DownloadStatus.RUNNING, DownloadStatus.PENDING -> {
                val progress = entry.progress
                if (progress != null) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            DownloadStatus.FAILED -> Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            else -> Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.roundToInt()} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

private fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
