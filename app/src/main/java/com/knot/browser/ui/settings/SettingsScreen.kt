package com.knot.browser.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.knot.browser.core.settings.SearchEngine
import com.knot.browser.core.settings.ThemeMode

/**
 * Full settings surface: appearance (theme), search engine, and the
 * Gemini API key used by Assistant/Translate. Supersedes the Stage 2.1
 * placeholder that only had a search-engine picker -- that section's
 * logic is unchanged here, just joined by two more.
 */
@Composable
fun SettingsScreen(
    currentEngine: SearchEngine,
    onEngineSelected: (SearchEngine) -> Unit,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    geminiApiKey: String,
    onGeminiApiKeyChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SettingsSection(
                title = "Appearance",
                subtitle = "Choose how Knot looks. Dark mode dims every surface in the app, not just the page you're viewing.",
                icon = Icons.Default.Palette,
            ) {
                ThemeMode.entries.forEach { mode ->
                    SelectableRow(
                        title = mode.displayName,
                        leadingIcon = when (mode) {
                            ThemeMode.SYSTEM -> Icons.Default.PhoneAndroid
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.DARK -> Icons.Default.DarkMode
                        },
                        isSelected = mode == themeMode,
                        onClick = { onThemeModeSelected(mode) },
                    )
                }
            }

            SettingsSection(
                title = "Search engine",
                subtitle = "Used when you type a search query instead of a web address.",
                icon = Icons.Default.Search,
            ) {
                SearchEngine.entries.forEach { engine ->
                    SelectableRow(
                        title = engine.displayName,
                        subtitle = if (engine == SearchEngine.DEFAULT) "Default" else null,
                        isSelected = engine == currentEngine,
                        onClick = { onEngineSelected(engine) },
                    )
                }
            }

            SettingsSection(
                title = "Gemini API key",
                subtitle = "Required for the Assistant (summarize) and Translate tools. " +
                    "Your key is stored only on this device.",
                icon = Icons.Default.Key,
            ) {
                GeminiApiKeyField(
                    value = geminiApiKey,
                    onValueChange = onGeminiApiKeyChanged,
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp, start = 32.dp),
        )
        content()
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun GeminiApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        placeholder = { Text("Paste your Gemini API key") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrect = false),
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isVisible) "Hide key" else "Show key",
                )
            }
        },
    )
}
