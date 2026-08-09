package com.knot.browser.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.knot.browser.core.webview.FindInPageController
import com.knot.browser.ui.theme.KnotMotion

/**
 * Bottom pill find-in-page bar (UI_DESIGN_BRIEF.md section 2.5), wired
 * directly to Stage 2's [FindInPageController]. Match count updates are
 * intentionally not animated (per the brief -- only the bar's
 * appearance/disappearance gets spring motion; the number itself should
 * feel instant).
 */
@Composable
fun FindInPageBar(
    controller: FindInPageController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = controller.isActive,
        enter = slideInVertically(animationSpec = KnotMotion.bouncy()) { fullHeight -> fullHeight },
        exit = slideOutVertically(animationSpec = KnotMotion.bouncy()) { fullHeight -> fullHeight },
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = controller.query,
                    onValueChange = { controller.search(it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Find in page") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { controller.findNext() }),
                )

                if (controller.query.isNotEmpty()) {
                    Text(
                        text = if (controller.totalMatches == 0) {
                            "0/0"
                        } else {
                            "${controller.activeMatchIndex + 1}/${controller.totalMatches}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = { controller.findPrevious() }) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                }
                IconButton(onClick = { controller.findNext() }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                }
                IconButton(onClick = {
                    controller.stop()
                    onClose()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close find in page")
                }
            }
        }
    }
}
