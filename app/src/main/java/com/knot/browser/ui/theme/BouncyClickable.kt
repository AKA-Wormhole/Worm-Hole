package com.knot.browser.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch

/**
 * The "squish" press interaction referenced throughout UI_DESIGN_BRIEF.md
 * section 3: scales down to [KnotMotion.PRESS_SCALE] on press, springs
 * back with [KnotMotion.snappy] on release. Every tappable element in
 * Knot should use this instead of hand-rolling its own
 * MutableInteractionSource + animateFloatAsState pair, so the feel stays
 * identical everywhere (and so a future tuning change to the press curve
 * only has to happen in one place).
 *
 * Deliberately omits the ripple/indication so the scale itself reads as
 * the feedback -- a ripple on top of a scale looks busy. If a specific
 * component wants a ripple too, compose `.clickable(indication = ...)`
 * separately instead of using this modifier.
 */
@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) KnotMotion.PRESS_SCALE else 1f,
        animationSpec = KnotMotion.snappy(),
        label = "bouncyClickableScale",
    )

    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/**
 * A playful variant of [bouncyClickable] for icon-only actions that
 * benefit from a little extra flourish on tap -- the "+" new-tab buttons
 * (sidebar footer and bottom bar) use this. On every tap the element
 * does a quick overshoot spin (0 -> [spinDegrees] -> 0) at the same time
 * as the usual squish-and-bounce scale, so creating a tab reads as a
 * small celebratory gesture rather than a flat click. Kept separate from
 * [bouncyClickable] rather than adding a flag to it, since most tappable
 * elements (cards, list rows, pills) should *not* spin -- this is meant
 * to be reached for deliberately, not the default.
 */
@Composable
fun Modifier.spinBounceClickable(
    enabled: Boolean = true,
    role: Role? = null,
    spinDegrees: Float = 90f,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) KnotMotion.PRESS_SCALE else 1f,
        animationSpec = KnotMotion.snappy(),
        label = "spinBounceScale",
    )
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    return this
        .scale(scale)
        .rotate(rotation.value)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = {
                scope.launch {
                    rotation.animateTo(spinDegrees, animationSpec = KnotMotion.snappy())
                    rotation.animateTo(0f, animationSpec = KnotMotion.snappy())
                }
                onClick()
            },
        )
}
