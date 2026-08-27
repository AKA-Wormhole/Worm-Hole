package com.wormhole.browser.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wormhole.browser.core.settings.SearchEngine
import com.wormhole.browser.core.settings.ThemeMode
import com.wormhole.browser.ui.theme.WormHoleRow
import com.wormhole.browser.ui.theme.WormHoleSurface
import com.wormhole.browser.ui.theme.WormHoleSwitch
import com.wormhole.browser.ui.theme.bouncyClickable

@Composable
fun SettingsScreen(
    currentEngine: SearchEngine,
    onEngineSelected: (SearchEngine) -> Unit,
    geminiApiKey: String,
    onGeminiApiKeyChanged: (String) -> Unit,
    trackerBlockingEnabled: Boolean,
    onTrackerBlockingChanged: (Boolean) -> Unit,
    adBlockingEnabled: Boolean,
    onAdBlockingChanged: (Boolean) -> Unit,
    popupBlockingEnabled: Boolean,
    onPopupBlockingChanged: (Boolean) -> Unit,
    webDarkModeEnabled: Boolean,
    onWebDarkModeChanged: (Boolean) -> Unit,
    dynamicBackgroundEnabled: Boolean = true,
    onDynamicBackgroundChanged: (Boolean) -> Unit = {},
    onSetDefaultBrowserClick: () -> Unit = {},
    appVersionName: String = "",
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    homepageUrl: String = "",
    onHomepageUrlChanged: (String) -> Unit = {},
    onFeedbackClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onLogsClick: () -> Unit = {},
    onPasskeysClick: () -> Unit,
    onClearBrowsingData: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onOpenSourceLicensesClick: () -> Unit,
    onBack: () -> Unit,
    hasDiagnosticReport: Boolean = false,
    onShareDiagnosticReport: () -> Unit = {},
) {
    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showHomepageEditor by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SettingsHeader(title = "Settings", onBack = onBack)

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup(title = "Appearance") {
                ThemeMode.entries.forEach { mode ->
                    SelectableRow(
                        title = mode.displayName,
                        subtitle = when (mode) {
                            ThemeMode.SYSTEM -> "Follow the phone light/dark setting"
                            ThemeMode.LIGHT -> "Light chrome, sheets, and menus"
                            ThemeMode.DARK -> "Dark chrome, sheets, and menus (default)"
                        },
                        isSelected = mode == themeMode,
                        onClick = { onThemeModeSelected(mode) },
                    )
                }
                ToggleRow(
                    title = "Websites match app theme",
                    subtitle = "Light pages in light mode, dark pages in dark mode",
                    icon = Icons.Default.DarkMode,
                    checked = webDarkModeEnabled,
                    onCheckedChange = onWebDarkModeChanged,
                )
                ToggleRow(
                    title = "Living home background",
                    subtitle = "Weather-tinted wallpaper on the new-tab page",
                    icon = Icons.Default.Wallpaper,
                    checked = dynamicBackgroundEnabled,
                    onCheckedChange = onDynamicBackgroundChanged,
                )
            }

            SettingsGroup(title = "Privacy & security") {
                ToggleRow(
                    title = "Tracker blocker",
                    subtitle = "Block analytics and fingerprinting",
                    icon = Icons.Default.Shield,
                    checked = trackerBlockingEnabled,
                    onCheckedChange = onTrackerBlockingChanged,
                )
                ToggleRow(
                    title = "Ad blocker",
                    subtitle = "Hide known ad-serving hosts",
                    icon = Icons.Default.Block,
                    checked = adBlockingEnabled,
                    onCheckedChange = onAdBlockingChanged,
                )
                ToggleRow(
                    title = "Pop-up blocker",
                    subtitle = "Stop pages from opening extra windows",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    checked = popupBlockingEnabled,
                    onCheckedChange = onPopupBlockingChanged,
                )
                WormHoleRow(
                    title = "Passkeys",
                    subtitle = "Saved on this device",
                    leadingIcon = Icons.Default.Fingerprint,
                    onClick = onPasskeysClick,
                    trailing = { ChevronGlyph() },
                )
                WormHoleRow(
                    title = "Clear browsing data",
                    subtitle = "History, cookies, and site data",
                    leadingIcon = Icons.Default.DeleteSweep,
                    onClick = { showClearDataConfirm = true },
                    trailing = { ChevronGlyph() },
                )
            }

            SettingsGroup(title = "Search engine") {
                SearchEngine.entries.forEach { engine ->
                    SelectableRow(
                        title = engine.displayName,
                        subtitle = if (engine == SearchEngine.DEFAULT) "Default" else null,
                        isSelected = engine == currentEngine,
                        onClick = { onEngineSelected(engine) },
                    )
                }
            }

            SettingsGroup(title = "Assistant") {
                GeminiApiKeyField(
                    value = geminiApiKey,
                    onValueChange = onGeminiApiKeyChanged,
                )
            }

            if (hasDiagnosticReport) {
                SettingsGroup(title = "Diagnostics") {
                    WormHoleRow(
                        title = "Share latest error report",
                        leadingIcon = Icons.Default.Code,
                        onClick = onShareDiagnosticReport,
                        trailing = { ChevronGlyph() },
                    )
                }
            }

            SettingsGroup(title = "Browser") {
                WormHoleRow(
                    title = "Set as default browser",
                    subtitle = "Open links from other apps in WormHole",
                    leadingIcon = Icons.Default.Public,
                    onClick = onSetDefaultBrowserClick,
                    trailing = { ChevronGlyph() },
                )
                WormHoleRow(
                    title = "Homepage",
                    subtitle = if (homepageUrl.isBlank()) "New Tab page" else homepageUrl,
                    leadingIcon = Icons.Default.Home,
                    onClick = { showHomepageEditor = true },
                    trailing = { ChevronGlyph() },
                )
                WormHoleRow(
                    title = "Send feedback",
                    subtitle = "Open the public issue tracker",
                    leadingIcon = Icons.Default.Feedback,
                    onClick = onFeedbackClick,
                    trailing = { ChevronGlyph() },
                )
                WormHoleRow(
                    title = "App logs",
                    subtitle = "Copy crashes and in-app traces",
                    leadingIcon = Icons.Default.BugReport,
                    onClick = onLogsClick,
                    trailing = { ChevronGlyph() },
                )
                WormHoleRow(
                    title = "About WormHole",
                    subtitle = if (appVersionName.isNotBlank()) "Version $appVersionName" else "Version and engine",
                    leadingIcon = Icons.Default.Info,
                    onClick = onAboutClick,
                    trailing = { ChevronGlyph() },
                )
            }

            SettingsGroup(title = "Legal") {
                WormHoleRow(
                    title = "Privacy Policy",
                    leadingIcon = Icons.Default.PrivacyTip,
                    onClick = onPrivacyPolicyClick,
                    trailing = { ChevronGlyph() },
                )
                WormHoleRow(
                    title = "Terms of Service",
                    leadingIcon = Icons.Default.Description,
                    onClick = onTermsClick,
                    trailing = { ChevronGlyph() },
                )
                WormHoleRow(
                    title = "Open source licenses",
                    leadingIcon = Icons.Default.Code,
                    onClick = onOpenSourceLicensesClick,
                    trailing = { ChevronGlyph() },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }


    if (showHomepageEditor) {
        var draft by remember { mutableStateOf(homepageUrl) }
        AlertDialog(
            onDismissRequest = { showHomepageEditor = false },
            title = { Text("Homepage") },
            text = {
                Column {
                    Text(
                        "Leave empty to open the New Tab page. Or paste a full https address.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        placeholder = { Text("https://") },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onHomepageUrlChanged(draft.trim())
                    showHomepageEditor = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showHomepageEditor = false }) { Text("Cancel") }
            },
        )
    }

    if (showClearDataConfirm) {
        ClearDataDialog(
            onDismiss = { showClearDataConfirm = false },
            onConfirm = { showClearDataConfirm = false; onClearBrowsingData() },
        )
    }
}

@Composable
internal fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(WormHoleSurface.Fill)
                .bouncyClickable(onClick = onBack, contentDescription = "Back"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun ChevronGlyph() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ClearDataDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = WormHoleSurface.CardShape,
            color = WormHoleSurface.Fill,
            border = WormHoleSurface.border(),
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    "Clear browsing data?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "This erases your browsing history, cookies, and site data (like " +
                        "saved sign-ins) from this device. Bookmarks, downloads, and " +
                        "passkeys are not affected. This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = onConfirm) {
                        Text("Clear data", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 22.dp, bottom = 4.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        )
        Surface(
            shape = WormHoleSurface.CardShape,
            color = WormHoleSurface.Fill,
            border = WormHoleSurface.border(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SelectableRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
) {
    WormHoleRow(
        title = title,
        subtitle = subtitle,
        leadingIcon = leadingIcon,
        selected = isSelected,
        onClick = onClick,
        trailing = {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    WormHoleRow(
        title = title,
        subtitle = subtitle,
        leadingIcon = icon,
        iconTint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = { onCheckedChange(!checked) },
        trailing = { WormHoleSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
internal fun NavigationRow(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    WormHoleRow(
        title = title,
        subtitle = subtitle,
        leadingIcon = leadingIcon,
        onClick = onClick,
        trailing = { ChevronGlyph() },
    )
}

@Composable
private fun GeminiApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }

    var isUnlocked by remember { mutableStateOf(false) }
    // Auto-lock the key field shortly after unlock so the secret is not left visible.
    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            kotlinx.coroutines.delay(60_000)
            isUnlocked = false
            isVisible = false
        }
    }
    val accent = MaterialTheme.colorScheme.primary
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity

    fun requestUnlock(onUnlocked: () -> Unit) {
        if (activity == null || !com.wormhole.browser.core.security.BiometricAuthenticator.isAvailable(activity)) {

            onUnlocked()
            return
        }
        com.wormhole.browser.core.security.BiometricAuthenticator.authenticate(
            activity = activity,
            title = "Unlock Gemini API key",
            subtitle = "Verify it's you to view or change your API key",
            onSuccess = onUnlocked,
            onFailure = {},
        )
    }

    Surface(
        shape = WormHoleSurface.PillShape,
        color = WormHoleSurface.Fill,
        border = WormHoleSurface.border(),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = accent,
                backgroundColor = accent.copy(alpha = 0.28f),
            ),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->

                    if (isUnlocked || value.isEmpty()) {
                        isUnlocked = true
                        onValueChange(newValue)
                    } else {
                        requestUnlock {
                            isUnlocked = true
                            onValueChange(newValue)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste your Gemini API key", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                readOnly = !isUnlocked && value.isNotEmpty(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    cursorColor = accent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
                trailingIcon = {
                    Icon(
                        if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isVisible) "Hide key" else "Show key",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.bouncyClickable(onClick = {
                            if (isVisible) {
                                isVisible = false
                            } else if (isUnlocked) {
                                isVisible = true
                            } else {
                                requestUnlock {
                                    isUnlocked = true
                                    isVisible = true
                                }
                            }
                        }),
                    )
                },
            )
        }
    }
}
