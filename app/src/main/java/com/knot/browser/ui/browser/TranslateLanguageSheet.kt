package com.knot.browser.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.knot.browser.core.ai.TranslateLanguage
import com.knot.browser.core.ai.TranslateLanguages
import com.knot.browser.ui.theme.bouncyClickable

/**
 * Target-language picker for Translate. Per product decision, the user
 * picks a language fresh every time rather than Knot remembering or
 * auto-detecting one -- simplest mental model, and translation targets
 * genuinely vary request to request (unlike, say, a fixed UI language).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateLanguageSheet(
    onLanguageSelected: (TranslateLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Translate page to",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        LazyColumn(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            items(TranslateLanguages.ALL) { language ->
                LanguageRow(
                    language = language,
                    onClick = { onLanguageSelected(language) },
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(
    language: TranslateLanguage,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(language.displayName, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
