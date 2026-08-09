package com.knot.browser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.knot.browser.ui.theme.KnotCoral
import com.knot.browser.ui.theme.KnotMotion
import com.knot.browser.ui.theme.KnotSky
import com.knot.browser.ui.theme.KnotViolet

/**
 * Stage 1 leftover: proves out the design tokens (color, type, motion)
 * in isolation from real browser chrome. Not shipped UI -- kept around
 * as a quick way to sanity-check the palette/motion after token changes.
 * Not wired into MainActivity's nav graph by default as of Stage 2;
 * swap it back in manually if you want to view it.
 */
@Composable
fun DesignCalibrationScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(KnotCoral, KnotViolet, KnotSky))
                    )
            )

            Text(
                text = "Knot",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = "Stage 1 · design tokens online",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )

            BouncyPill(modifier = Modifier.padding(top = 40.dp))
        }
    }
}

@Composable
private fun BouncyPill(modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) KnotMotion.PRESS_SCALE else 1f,
        animationSpec = KnotMotion.bouncy(),
        label = "pillScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {}
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Tap to feel the bounce",
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
