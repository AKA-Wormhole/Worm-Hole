package com.wormhole.browser.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wormhole.browser.core.settings.SearchEngine
import com.wormhole.browser.ui.theme.bouncyClickable

@Composable
fun SearchEnginePicker(
    current: SearchEngine,
    onSelected: (SearchEngine) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search engine") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(SearchEngine.entries.toList(), key = { it.id }) { engine ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClickable(onClick = { onSelected(engine); onDismiss() })
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SearchEngineLogo(engine = engine)
                        Text(
                            engine.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (engine == current) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
