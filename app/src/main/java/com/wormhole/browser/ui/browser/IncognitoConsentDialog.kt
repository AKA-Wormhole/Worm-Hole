package com.wormhole.browser.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IncognitoConsentDialog(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(

        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        onDismissRequest = {  },
        icon = {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("You've turned on Incognito") },
        text = {
            Column {
                Text(
                    "WormHole won't save your browsing history, cookies, site data, or " +
                        "information entered in forms on this device once you close " +
                        "all Incognito tabs.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "This does not make you invisible. Your activity may still be visible to:",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(10.dp))
                ConsentBullet(
                    icon = Icons.Default.Public,
                    text = "Websites you visit, and any accounts you stay signed into",
                )
                ConsentBullet(
                    icon = Icons.Default.Visibility,
                    text = "Your employer or school, if this is a managed device or network",
                )
                ConsentBullet(
                    icon = Icons.Default.Cookie,
                    text = "Your internet service provider",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree) { Text("Agree") }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("Decline") }
        },
    )
}

@Composable
private fun ConsentBullet(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
}
