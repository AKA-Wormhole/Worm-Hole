package com.wormhole.browser.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class LicenseEntry(
    val name: String,
    val license: String,
    val url: String,
)

private val LICENSES = listOf(
    LicenseEntry("AndroidX Core / Compose / Material3 / Lifecycle / Navigation / Fragment", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    LicenseEntry("Jetpack Compose", "Apache License 2.0", "https://developer.android.com/jetpack/compose"),
    LicenseEntry("Room", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/room"),
    LicenseEntry("DataStore", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/datastore"),
    LicenseEntry("AndroidX WebKit", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/webkit"),
    LicenseEntry("AndroidX Biometric", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/biometric"),
    LicenseEntry("AndroidX Security Crypto", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/security"),
    LicenseEntry("Kotlin", "Apache License 2.0", "https://github.com/JetBrains/kotlin"),
    LicenseEntry("Kotlinx Coroutines", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    LicenseEntry("Kotlinx Serialization", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.serialization"),
    LicenseEntry("OkHttp", "Apache License 2.0", "https://square.github.io/okhttp/"),
    LicenseEntry("JUnit 4", "Eclipse Public License 1.0", "https://junit.org/junit4/"),
)

@Composable
fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SettingsHeader(title = "Open Source Licenses", onBack = onBack)
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "WormHole is built with the following open-source software. " +
                    "Each library is used under the terms of its own license.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            )
            LICENSES.forEach { entry ->
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    entry.license,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    entry.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
