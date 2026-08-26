package com.wormhole.browser.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wormhole.browser.core.extensions.BrowserAction
import com.wormhole.browser.core.extensions.CatalogExtension
import com.wormhole.browser.core.extensions.ExtensionCatalog
import com.wormhole.browser.core.extensions.ExtensionDisabledReason
import com.wormhole.browser.core.extensions.ExtensionInstallResult
import com.wormhole.browser.core.extensions.ExtensionManager
import com.wormhole.browser.core.extensions.InstalledExtension
import com.wormhole.browser.ui.theme.WormHoleRow
import com.wormhole.browser.ui.theme.WormHoleSurface
import com.wormhole.browser.ui.theme.WormHoleSwitch
import com.wormhole.browser.ui.theme.bouncyClickable
import kotlinx.coroutines.launch

private enum class ExtensionsTab { INSTALLED, BROWSE }

/**
 * Extension management: what's installed (with per-extension enable/disable
 * and uninstall), a curated "browse" catalog of known Gecko-compatible
 * extensions, and an "Add from URL" flow for any other .xpi. Backed by
 * [ExtensionManager], which talks to GeckoView's real WebExtensionController
 * -- installs here are genuine WebExtensions, not a mock list.
 */
@Composable
fun ExtensionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { ExtensionManager.get(context) }
    val installed by manager.installed.collectAsState()
    val isBusy by manager.isBusy.collectAsState()
    val lastError by manager.lastError.collectAsState()
    val browserActions by manager.browserActions.collectAsState()
    val openPopup by manager.openPopup.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var tab by remember { mutableStateOf(ExtensionsTab.INSTALLED) }
    var showAddFromUrl by remember { mutableStateOf(false) }
    var installingCatalogId by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { manager.refresh() }
    LaunchedEffect(lastError) {
        if (lastError != null) {
            toast = lastError
            manager.clearError()
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2800)
            toast = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SettingsHeader(title = "Extensions", onBack = onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabChip(
                label = "Installed",
                isSelected = tab == ExtensionsTab.INSTALLED,
                onClick = { tab = ExtensionsTab.INSTALLED },
                modifier = Modifier,
            )
            TabChip(
                label = "Browse",
                isSelected = tab == ExtensionsTab.BROWSE,
                onClick = { tab = ExtensionsTab.BROWSE },
                modifier = Modifier,
            )
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when (tab) {
                ExtensionsTab.INSTALLED -> InstalledTab(
                    extensions = installed,
                    isBusy = isBusy,
                    browserActions = browserActions,
                    onToggle = { ext, enabled ->
                        scope.launch { manager.setEnabled(ext.id, enabled) }
                    },
                    onUninstall = { ext ->
                        scope.launch { manager.uninstall(ext.id) }
                    },
                    onOpenAction = { ext -> manager.onBrowserActionClicked(ext.id) },
                    onAddFromUrlClick = { showAddFromUrl = true },
                )
                ExtensionsTab.BROWSE -> BrowseTab(
                    installed = installed,
                    installingId = installingCatalogId,
                    onInstall = { entry ->
                        installingCatalogId = entry.id
                        scope.launch {
                            val result = manager.installFromUrl(entry.xpiUrl)
                            installingCatalogId = null
                            toast = when (result) {
                                is ExtensionInstallResult.Success -> {
                                    tab = ExtensionsTab.INSTALLED
                                    "${result.extension.name} installed"
                                }
                                is ExtensionInstallResult.Failure -> result.message
                            }
                        }
                    },
                    onAddFromUrlClick = { showAddFromUrl = true },
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAddFromUrl) {
        AddFromUrlDialog(
            isBusy = isBusy,
            onDismiss = { showAddFromUrl = false },
            onInstall = { url ->
                scope.launch {
                    val result = manager.installFromUrl(url)
                    toast = when (result) {
                        is ExtensionInstallResult.Success -> {
                            showAddFromUrl = false
                            "${result.extension.name} installed"
                        }
                        is ExtensionInstallResult.Failure -> result.message
                    }
                }
            },
        )
    }

    openPopup?.let { popup ->
        val extensionName = installed.firstOrNull { it.id == popup.extensionId }?.name ?: "Extension"
        ExtensionPopupSheet(
            popup = popup,
            extensionName = extensionName,
            onDismiss = { manager.closePopup() },
        )
    }

    toast?.let { message ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                shape = WormHoleSurface.PillShape,
                color = WormHoleSurface.Fill,
                border = WormHoleSurface.border(),
            ) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun InstalledTab(
    extensions: List<InstalledExtension>,
    isBusy: Boolean,
    browserActions: Map<String, BrowserAction>,
    onToggle: (InstalledExtension, Boolean) -> Unit,
    onUninstall: (InstalledExtension) -> Unit,
    onOpenAction: (InstalledExtension) -> Unit,
    onAddFromUrlClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        WormHoleRow(
            title = "Add extension from URL",
            subtitle = "Install any .xpi extension package by its direct link",
            leadingIcon = Icons.Default.Link,
            onClick = onAddFromUrlClick,
        )

        if (extensions.isEmpty() && !isBusy) {
            EmptyState(
                icon = Icons.Default.Extension,
                title = "No extensions installed",
                subtitle = "Browse the catalog or add one from a URL to get started.",
            )
        }

        extensions.forEach { ext ->
            InstalledExtensionRow(
                extension = ext,
                action = browserActions[ext.id],
                onToggle = { enabled -> onToggle(ext, enabled) },
                onUninstall = { onUninstall(ext) },
                onOpenAction = { onOpenAction(ext) },
            )
        }

        if (isBusy) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun BrowseTab(
    installed: List<InstalledExtension>,
    installingId: String?,
    onInstall: (CatalogExtension) -> Unit,
    onAddFromUrlClick: () -> Unit,
) {
    val available = remember(installed) {
        ExtensionCatalog.entries.filterNot { ExtensionCatalog.isInstalled(it, installed) }
    }
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            "Extensions known to work with Gecko. Already-installed ones are hidden here — " +
                "manage them on the Installed tab. You can also add any Firefox .xpi by URL.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        WormHoleRow(
            title = "Add extension from URL",
            subtitle = "Install any .xpi extension package by its direct link",
            leadingIcon = Icons.Default.Link,
            onClick = onAddFromUrlClick,
        )
        if (available.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Extension,
                title = "All catalog extensions installed",
                subtitle = "Switch to Installed to manage them, or add another from a URL.",
            )
        }
        available.forEach { entry ->
            CatalogRow(
                entry = entry,
                isInstalled = false,
                isInstalling = installingId == entry.id,
                onInstall = { onInstall(entry) },
            )
        }
    }
}

@Composable
private fun InstalledExtensionRow(
    extension: InstalledExtension,
    action: BrowserAction?,
    onToggle: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onOpenAction: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var showPermissions by remember { mutableStateOf(false) }

    // Only a plain user-disable is something the toggle itself should
    // control. Blocklisted/app-controlled states are shown as fixed,
    // explained status rather than an interactive switch that would silently
    // fail to re-enable the extension.
    val toggleIsUserControlled = extension.isEnabled ||
        extension.disabledReason == ExtensionDisabledReason.USER ||
        extension.disabledReason == ExtensionDisabledReason.NONE

    val statusLine = when (extension.disabledReason) {
        ExtensionDisabledReason.BLOCKLISTED -> "Blocked for safety reasons"
        ExtensionDisabledReason.APP_DISABLED -> "Disabled by the browser"
        ExtensionDisabledReason.APP_SUPPORT -> "Needs permissions granted to re-enable"
        ExtensionDisabledReason.UNKNOWN -> "Disabled"
        ExtensionDisabledReason.USER, ExtensionDisabledReason.NONE -> null
    }

    WormHoleRow(
        title = extension.name,
        subtitle = buildString {
            append(extension.description.ifBlank { "v${extension.version}" })
            if (statusLine != null) append("\n$statusLine")
        },
        leadingIcon = if (extension.iconBitmap == null) Icons.Default.Extension else null,
        iconTint = if (extension.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = { showPermissions = true },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                extension.iconBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                }
                if (action?.click != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .bouncyClickable(
                                contentDescription = "Open ${extension.name} action",
                                onClick = onOpenAction,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val actionBitmap = action.icon
                        if (actionBitmap != null) {
                            Image(
                                bitmap = actionBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (!action.badgeText.isNullOrBlank()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(14.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        action.badgeText.take(2),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = MaterialTheme.colorScheme.onError,
                                    )
                                }
                            }
                        }
                    }
                }
                if (extension.disabledReason == ExtensionDisabledReason.BLOCKLISTED) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Blocked",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .bouncyClickable(
                            contentDescription = "Remove ${extension.name}",
                            onClick = { confirmDelete = true },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                WormHoleSwitch(
                    checked = extension.isEnabled,
                    onCheckedChange = onToggle,
                    enabled = toggleIsUserControlled,
                )
            }
        },
    )

    if (confirmDelete) {
        ConfirmRemoveDialog(
            name = extension.name,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onUninstall()
            },
        )
    }

    if (showPermissions) {
        ExtensionDetailDialog(
            extension = extension,
            action = action,
            onDismiss = { showPermissions = false },
            onOpenAction = {
                showPermissions = false
                onOpenAction()
            },
        )
    }
}

@Composable
private fun ExtensionDetailDialog(
    extension: InstalledExtension,
    action: BrowserAction?,
    onDismiss: () -> Unit,
    onOpenAction: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.6f))
            .bouncyClickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = WormHoleSurface.CardShape,
            color = WormHoleSurface.Fill,
            border = WormHoleSurface.border(),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .bouncyClickable(onClick = {}),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (extension.iconBitmap != null) {
                        Image(
                            bitmap = extension.iconBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    } else {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            extension.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "v${extension.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                val statusText = when {
                    !extension.isEnabled -> when (extension.disabledReason) {
                        ExtensionDisabledReason.BLOCKLISTED -> "Blocked for safety"
                        ExtensionDisabledReason.APP_DISABLED -> "Disabled by browser"
                        ExtensionDisabledReason.APP_SUPPORT -> "Needs permissions"
                        else -> "Disabled — turn the switch on to run it"
                    }
                    action?.badgeText?.isNotBlank() == true ->
                        "Active · badge: ${action.badgeText} (often blocked/count stats)"
                    action?.click != null -> "Active · toolbar button available"
                    else -> "Enabled · content scripts run on matching sites"
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (extension.isEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 14.dp),
                )

                Text(
                    "How to check it’s working",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "• Enable the toggle on the Installed tab\n" +
                        "• Open a site the extension targets (e.g. YouTube for Return YouTube Dislike)\n" +
                        "• Tap the extension action button if shown (badge may show blocked count)\n" +
                        "• Reload the page after enabling — some only inject on navigation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )

                if (!extension.description.isBlank()) {
                    Text(
                        extension.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }

                if (extension.origins.isNotEmpty()) {
                    PermissionSection(title = "Websites", items = extension.origins.take(12))
                }
                if (extension.permissions.isNotEmpty()) {
                    PermissionSection(title = "Browser features", items = extension.permissions)
                }
                if (extension.permissions.isEmpty() && extension.origins.isEmpty()) {
                    Text(
                        "No special permissions listed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (action?.click != null && extension.isEnabled) {
                        TextButton(onClick = onOpenAction) { Text("Open action") }
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun PermissionSection(title: String, items: List<String>) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        items.forEach { item ->
            Text(
                "• $item",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CatalogRow(
    entry: CatalogExtension,
    isInstalled: Boolean,
    isInstalling: Boolean,
    onInstall: () -> Unit,
) {
    WormHoleRow(
        title = entry.name,
        subtitle = "${entry.summary}\n${entry.author}",
        leadingIcon = Icons.Default.Extension,
        trailing = {
            when {
                isInstalling -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                isInstalled -> Text(
                    "Installed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> Surface(
                    shape = WormHoleSurface.PillShape,
                    color = Color.White,
                    modifier = Modifier.bouncyClickable(onClick = onInstall),
                ) {
                    Text(
                        "Add",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun TabChip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = WormHoleSurface.PillShape,
        color = if (isSelected) Color.White.copy(alpha = 0.16f) else WormHoleSurface.Fill,
        border = WormHoleSurface.border(),
        modifier = modifier.bouncyClickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ConfirmRemoveDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.6f))
            .bouncyClickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = WormHoleSurface.CardShape,
            color = WormHoleSurface.Fill,
            border = WormHoleSurface.border(),
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .bouncyClickable(onClick = {}),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                Text(
                    "Remove $name?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "This uninstalls the extension and its settings from this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = onConfirm) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun AddFromUrlDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onInstall: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.6f))
            .bouncyClickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = WormHoleSurface.CardShape,
            color = WormHoleSurface.Fill,
            border = WormHoleSurface.border(),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .bouncyClickable(onClick = {}),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Text(
                    "Add extension from URL",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "Paste a direct link to a .xpi extension package.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("https://…/extension.xpi") },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = WormHoleSurface.HairlineBorder,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        enabled = url.isNotBlank() && !isBusy,
                        onClick = { onInstall(url.trim()) },
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Install")
                        }
                    }
                }
            }
        }
    }
}
