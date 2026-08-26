package com.wormhole.browser.ui.downloads

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Apps
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wormhole.browser.core.downloads.DownloadCategory
import com.wormhole.browser.core.downloads.DownloadRecord
import com.wormhole.browser.core.downloads.DownloadRepository
import com.wormhole.browser.core.downloads.WormHoleDownloadStatus
import com.wormhole.browser.ui.theme.WormHoleMotion
import com.wormhole.browser.ui.theme.WormHoleSurface
import com.wormhole.browser.ui.theme.bouncyClickable
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads by remember { DownloadRepository.observeAll(context) }
        .collectAsState(initial = emptyList())

    var selectedCategory by remember { mutableStateOf<DownloadCategory?>(null) }
    var selectedStatus by remember { mutableStateOf(DownloadStatusFilter.ALL) }
    val filtered = remember(downloads, selectedCategory, selectedStatus) {
        downloads.filter { record ->
            val categoryOk = selectedCategory == null || record.category == selectedCategory!!.name
            categoryOk && selectedStatus.matches(record.status)
        }
    }
    val categoryCounts = remember(downloads) {
        DownloadCategory.entries.associateWith { category ->
            downloads.count { it.category == category.name }
        }
    }
    val statusCounts = remember(downloads) {
        DownloadStatusFilter.entries.associateWith { filter ->
            if (filter == DownloadStatusFilter.ALL) downloads.size
            else downloads.count { filter.matches(it.status) }
        }
    }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var renameTarget by remember { mutableStateOf<DownloadRecord?>(null) }
    var bulkDeleteConfirm by remember { mutableStateOf(false) }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    LaunchedEffect(downloads) {
        if (selectedIds.isNotEmpty()) {
            val liveIds = downloads.map { it.id }.toSet()
            selectedIds = selectedIds.intersect(liveIds)
            if (selectedIds.isEmpty()) selectionMode = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (selectionMode) exitSelection() else onDismiss() },
                ),
        )

        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = WormHoleSurface.SheetShape,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(
                        Modifier
                            .padding(top = 10.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                    )
                }

                if (selectionMode) {
                    SelectionTopBar(
                        selectedCount = selectedIds.size,
                        allSelected = selectedIds.containsAll(filtered.map { it.id }) && filtered.isNotEmpty(),
                        onSelectAll = { selectedIds = filtered.map { it.id }.toSet() },
                        onClearSelection = { selectedIds = emptySet() },
                        onDeleteSelected = { bulkDeleteConfirm = true },
                        onClose = { exitSelection() },
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                    }
                }

                DownloadChipRows(
                    selectedCategory = selectedCategory,
                    categoryCounts = categoryCounts,
                    onSelectCategory = { selectedCategory = it },
                    selectedStatus = selectedStatus,
                    statusCounts = statusCounts,
                    onSelectStatus = { selectedStatus = it },
                )

                if (filtered.isEmpty()) {
                    EmptyState(hasAnyDownloads = downloads.isNotEmpty())
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                    ) {
                        items(filtered, key = { it.id }) { entry ->
                            DownloadRow(
                                entry = entry,
                                modifier = Modifier.animateItem(placementSpec = WormHoleMotion.bouncy()),
                                selectionMode = selectionMode,
                                selected = entry.id in selectedIds,
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds = if (entry.id in selectedIds) {
                                            selectedIds - entry.id
                                        } else {
                                            selectedIds + entry.id
                                        }
                                        if (selectedIds.isEmpty()) selectionMode = false
                                    } else if (entry.status == WormHoleDownloadStatus.SUCCESSFUL.name) {
                                        DownloadRepository.openFile(context, entry)
                                    }
                                },
                                onLongPress = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedIds = setOf(entry.id)
                                    }
                                },
                                onRetry = {
                                    scope.launch { DownloadRepository.retry(context, entry, userAgent = null) }
                                },
                                onOpen = { DownloadRepository.openFile(context, entry) },
                                onShare = { DownloadRepository.shareFile(context, entry) },
                                onRename = { renameTarget = entry },
                                onTogglePin = {
                                    scope.launch { DownloadRepository.setPinned(context, entry.id, !entry.pinned) }
                                },
                                onDelete = {
                                    scope.launch {
                                        val running = entry.status == WormHoleDownloadStatus.RUNNING.name ||
                                            entry.status == WormHoleDownloadStatus.PENDING.name
                                        if (running) {
                                            DownloadRepository.cancel(context, entry.id)
                                        } else {
                                            DownloadRepository.clear(context, entry.id)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            currentName = target.fileName,
            onConfirm = { newName ->
                scope.launch { DownloadRepository.rename(context, target.id, newName) }
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    if (bulkDeleteConfirm) {
        val pinnedInSelection = downloads.count { it.id in selectedIds && it.pinned }
        AlertDialog(
            onDismissRequest = { bulkDeleteConfirm = false },
            title = { Text("Delete ${selectedIds.size} download${if (selectedIds.size == 1) "" else "s"}?") },
            text = {
                Text(
                    if (pinnedInSelection > 0) {
                        "$pinnedInSelection pinned item${if (pinnedInSelection == 1) "" else "s"} will be kept. Unpin first if you want to remove them too."
                    } else {
                        "This removes them from the list. Any in-progress download in the selection will be cancelled."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedIds.toList()
                    scope.launch {

                        ids.forEach { id ->
                            val record = downloads.find { it.id == id }
                            val running = record?.status == WormHoleDownloadStatus.RUNNING.name ||
                                record?.status == WormHoleDownloadStatus.PENDING.name
                            if (running) DownloadRepository.cancel(context, id)
                        }
                        DownloadRepository.deleteMany(context, ids)
                        exitSelection()
                    }
                    bulkDeleteConfirm = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { bulkDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cancel selection") }
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (allSelected) onClearSelection() else onSelectAll() }) {
                Icon(
                    Icons.Default.SelectAll,
                    contentDescription = if (allSelected) "Clear selection" else "Select all",
                    tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDeleteSelected, enabled = selectedCount > 0) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete selected",
                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private enum class DownloadStatusFilter(val label: String) {
    ALL("All"),
    ACTIVE("In progress"),
    DONE("Completed"),
    FAILED("Failed");

    fun matches(status: String): Boolean = when (this) {
        ALL -> true
        ACTIVE -> status == WormHoleDownloadStatus.RUNNING.name ||
            status == WormHoleDownloadStatus.PENDING.name ||
            status == WormHoleDownloadStatus.PAUSED.name
        DONE -> status == WormHoleDownloadStatus.SUCCESSFUL.name
        FAILED -> status == WormHoleDownloadStatus.FAILED.name ||
            status == WormHoleDownloadStatus.CANCELLED.name
    }
}

@Composable
private fun DownloadChipRows(
    selectedCategory: DownloadCategory?,
    categoryCounts: Map<DownloadCategory, Int>,
    onSelectCategory: (DownloadCategory?) -> Unit,
    selectedStatus: DownloadStatusFilter,
    statusCounts: Map<DownloadStatusFilter, Int>,
    onSelectStatus: (DownloadStatusFilter) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        ) {
            items(DownloadStatusFilter.entries.toList(), key = { it.name }) { filter ->
                DownloadFilterPill(
                    label = filter.label,
                    count = statusCounts[filter] ?: 0,
                    isSelected = selectedStatus == filter,
                    onClick = { onSelectStatus(filter) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        ) {
            item {
                DownloadFilterPill(
                    label = "All types",
                    count = categoryCounts.values.sum(),
                    isSelected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                )
            }
            items(DownloadCategory.entries.toList(), key = { it.name }) { category ->
                DownloadFilterPill(
                    label = category.label,
                    count = categoryCounts[category] ?: 0,
                    icon = categoryIcon(category),
                    isSelected = selectedCategory == category,
                    onClick = {
                        onSelectCategory(if (selectedCategory == category) null else category)
                    },
                )
            }
        }
    }
}

private fun categoryIcon(category: DownloadCategory): ImageVector = when (category) {
    DownloadCategory.IMAGES -> Icons.Default.Image
    DownloadCategory.VIDEOS -> Icons.Default.Videocam
    DownloadCategory.AUDIO -> Icons.Default.AudioFile
    DownloadCategory.DOCUMENTS -> Icons.Default.Description
    DownloadCategory.ARCHIVES -> Icons.Default.Archive
    DownloadCategory.APPS -> Icons.Default.Apps
    DownloadCategory.OTHER -> Icons.Default.InsertDriveFile
}

@Composable
private fun DownloadFilterPill(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    Surface(
        shape = WormHoleSurface.PillShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else WormHoleSurface.Fill,
        border = WormHoleSurface.border(),
        modifier = Modifier.bouncyClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (count > 0) "$label $count" else label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(hasAnyDownloads: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
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
                Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = if (hasAnyDownloads) "Nothing in this filter" else "No downloads yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = if (hasAnyDownloads) "Try another category or status chip." else "Files you download from the web will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadRow(
    entry: DownloadRecord,
    modifier: Modifier = Modifier,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    val completionScale = remember(entry.id) { Animatable(1f) }
    LaunchedEffect(entry.status, entry.id) {
        if (entry.status == WormHoleDownloadStatus.SUCCESSFUL.name) {
            completionScale.animateTo(1.06f, animationSpec = WormHoleMotion.snappy())
            completionScale.animateTo(1f, animationSpec = WormHoleMotion.bouncy())
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    val isCompleted = entry.status == WormHoleDownloadStatus.SUCCESSFUL.name

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(completionScale.value)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selectionMode) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        } else {
            FileTypeIcon(entry)
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.pinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp).padding(end = 4.dp),
                    )
                }
                Text(
                    text = entry.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            StatusLine(entry)

            if (entry.status == WormHoleDownloadStatus.RUNNING.name || entry.status == WormHoleDownloadStatus.PENDING.name) {
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

        if (!selectionMode) {
            if (entry.status == WormHoleDownloadStatus.FAILED.name) {
                IconButton(onClick = onRetry) {
                    Icon(
                        Icons.Default.Replay,
                        contentDescription = "Retry download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (isCompleted) {
                        DropdownMenuItem(
                            text = { Text("Open") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                            onClick = { menuOpen = false; onOpen() },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { menuOpen = false; onShare() },
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                            onClick = { menuOpen = false; onRename() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (entry.pinned) "Unpin" else "Pin") },
                        leadingIcon = { Icon(if (entry.pinned) Icons.Default.Pin else Icons.Default.PushPin, null) },
                        onClick = { menuOpen = false; onTogglePin() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (entry.pinned) "Delete (unpin first)" else "Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = if (entry.pinned) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error) },
                        enabled = !entry.pinned,
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLine(entry: DownloadRecord) {
    val text = when (entry.status) {
        WormHoleDownloadStatus.PENDING.name -> "Waiting to start…"
        WormHoleDownloadStatus.RUNNING.name -> {
            val progress = entry.progress
            if (progress != null) {
                "${(progress * 100).roundToInt()}% • ${formatBytes(entry.bytesDownloaded)} of ${formatBytes(entry.bytesTotal)}"
            } else {
                "Downloading • ${formatBytes(entry.bytesDownloaded)}"
            }
        }
        WormHoleDownloadStatus.PAUSED.name -> "Paused"
        WormHoleDownloadStatus.SUCCESSFUL.name -> "${formatBytes(entry.bytesTotal)} • ${formatDate(entry.updatedAt)}"
        WormHoleDownloadStatus.CANCELLED.name -> "Cancelled — tap ⋮ to remove"
        WormHoleDownloadStatus.FAILED.name -> (entry.errorMessage?.let { "Failed: $it" } ?: "Failed") + " — tap ↻ to retry"
        else -> ""
    }
    val color = if (entry.status == WormHoleDownloadStatus.FAILED.name) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun FileTypeIcon(entry: DownloadRecord) {
    val icon = when (entry.category) {
        DownloadCategory.IMAGES.name -> Icons.Default.Image
        DownloadCategory.VIDEOS.name -> Icons.Default.Videocam
        DownloadCategory.AUDIO.name -> Icons.Default.AudioFile
        DownloadCategory.ARCHIVES.name -> Icons.Default.Archive
        DownloadCategory.APPS.name -> Icons.Default.GetApp
        else -> if (entry.mimeType == "application/pdf") Icons.Default.PictureAsPdf else Icons.Default.Description
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .background(WormHoleSurface.Fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (entry.status) {
            WormHoleDownloadStatus.RUNNING.name, WormHoleDownloadStatus.PENDING.name -> {
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
            WormHoleDownloadStatus.FAILED.name -> Icon(
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
