package com.knot.browser.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.knot.browser.ui.theme.KnotMotion

/**
 * The summonable command surface (UI_DESIGN_BRIEF.md section 2.2) --
 * "the single most 'Arc' motion moment in the whole app" per the brief,
 * so the open animation is a real spring overshoot, not a fade or slide.
 *
 * [isOpen] is hoisted (owned by the caller) so opening/closing can be
 * triggered from multiple places (tapping the collapsed title display,
 * a new-tab surface's entry point) without this composable needing to
 * know who's asking.
 */
@Composable
fun CommandBar(
    isOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The overshoot: scale animates from slightly-below-full-size up to
    // 1f (and, because the spring is bouncy, briefly past it) as the bar
    // opens -- this is the actual "bounce" the brief calls for, applied
    // via graphicsLayer so it's cheap once the animation is running.
    val scale by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0.85f,
        animationSpec = KnotMotion.bouncy(),
        label = "commandBarScale",
    )

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(animationSpec = KnotMotion.settled()),
        exit = fadeOut(animationSpec = KnotMotion.settled()),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
                // Tapping anywhere on the dimmed backdrop dismisses, per
                // the brief's "dismiss by tapping outside" rule. The
                // field itself is a separate Surface drawn on top, so a
                // tap there is consumed by the text field instead of
                // reaching this click handler.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // statusBarsPadding() first so the fixed 24dp below is
                    // extra breathing room under the status bar/notch,
                    // rather than the notch eating into a flat 80dp
                    // guess -- the old fixed offset could sit the bar too
                    // high (cramped against a notch) or leave excess dead
                    // space depending on the device.
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                CommandBarField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSubmit = onSubmit,
                    requestFocus = isOpen,
                )
            }
        }
    }
}

@Composable
private fun CommandBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    requestFocus: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        // Consumes taps so they don't fall through to the backdrop's
        // dismiss handler behind it.
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(4.dp),
            placeholder = { Text("Search or enter address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit(query) }),
        )
    }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
}
