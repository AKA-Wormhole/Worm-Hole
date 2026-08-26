package com.wormhole.browser.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wormhole.browser.core.gecko.FindController
import com.wormhole.browser.ui.theme.WormHoleMotion
import com.wormhole.browser.ui.theme.WormHoleSurface

@Composable
fun FindInPageBar(
    controller: FindController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        controller.start()
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(WormHoleMotion.fadeIn()) +
            slideInVertically(animationSpec = WormHoleMotion.chrome()) { fullHeight -> fullHeight },
        exit = fadeOut(WormHoleMotion.fadeOut()) +
            slideOutVertically(animationSpec = WormHoleMotion.snappy()) { fullHeight -> fullHeight },
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = WormHoleSurface.Fill,
            border = WormHoleSurface.border(),
            shadowElevation = 4.dp,
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
                val accent = MaterialTheme.colorScheme.primary
                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = accent,
                        backgroundColor = accent.copy(alpha = 0.28f),
                    ),
                ) {
                    OutlinedTextField(
                        value = controller.query,
                        onValueChange = { controller.search(it) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        placeholder = { Text("Find in page") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { controller.findNext() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            cursorColor = accent,
                        ),
                    )
                }

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
