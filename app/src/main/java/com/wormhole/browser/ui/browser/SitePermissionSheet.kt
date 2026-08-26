package com.wormhole.browser.ui.browser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wormhole.browser.ui.theme.WormHoleMotion
import com.wormhole.browser.ui.theme.WormHoleSurface
import kotlinx.coroutines.launch

enum class SitePermissionKind(val icon: ImageVector, val label: String) {
    CAMERA(Icons.Default.Videocam, "camera"),
    MICROPHONE(Icons.Default.Mic, "microphone"),
    LOCATION(Icons.Default.LocationOn, "location"),
}

@Composable
fun SitePermissionSheet(
    origin: String,
    kinds: List<SitePermissionKind>,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    var sheetHeightPx by remember { mutableStateOf(0) }
    val offsetFraction = remember { Animatable(1f) }
    val scrimAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        launch { scrimAlpha.animateTo(1f, animationSpec = tween(180)) }
        offsetFraction.animateTo(0f, animationSpec = WormHoleMotion.bouncy())
    }

    fun dismissAnimated(after: () -> Unit) {
        scope.launch {
            launch { scrimAlpha.animateTo(0f, animationSpec = tween(140)) }
            offsetFraction.animateTo(1f, animationSpec = WormHoleMotion.settled())
            after()
        }
    }

    val label = when (kinds.size) {
        1 -> kinds.first().label
        else -> kinds.dropLast(1).joinToString(", ") { it.label } + " and " + kinds.last().label
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f * scrimAlpha.value))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { dismissAnimated(onDeny) },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { sheetHeightPx = it.size.height }
                .offset { IntOffset(x = 0, y = (offsetFraction.value * sheetHeightPx).toInt()) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(WormHoleSurface.Fill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            kinds.first().icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allow $label access?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = origin,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Text(
                    text = "This site is asking to use your $label. You can change this later in Site settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { dismissAnimated(onDeny) },
                        modifier = Modifier.weight(1f),
                        shape = WormHoleSurface.PillShape,
                    ) {
                        Text("Block", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = { dismissAnimated(onAllow) },
                        modifier = Modifier.weight(1f),
                        shape = WormHoleSurface.PillShape,
                    ) {
                        Text("Allow", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
