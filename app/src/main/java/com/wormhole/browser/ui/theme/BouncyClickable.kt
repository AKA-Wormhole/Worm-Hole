package com.wormhole.browser.ui.theme

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    role: Role? = null,
    contentDescription: String? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) WormHoleMotion.PRESS_SCALE else 1f,
        animationSpec = WormHoleMotion.snappy(),
        label = "bouncyClickableScale",
    )

    return this
        .scale(scale)
        .then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            },
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

@Composable
fun Modifier.spinBounceClickable(
    enabled: Boolean = true,
    role: Role? = null,
    spinDegrees: Float = 90f,
    contentDescription: String? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) WormHoleMotion.PRESS_SCALE else 1f,
        animationSpec = WormHoleMotion.snappy(),
        label = "spinBounceScale",
    )
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    return this
        .scale(scale)
        .rotate(rotation.value)
        .then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            },
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = {
                scope.launch {
                    rotation.animateTo(spinDegrees, animationSpec = WormHoleMotion.snappy())
                    rotation.animateTo(0f, animationSpec = WormHoleMotion.snappy())
                }
                onClick()
            },
        )
}
