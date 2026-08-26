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
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wormhole.browser.ui.theme.WormHoleMotion
import com.wormhole.browser.ui.theme.WormHoleSurface
import kotlinx.coroutines.launch

@Composable
fun SslWarningSheet(
    url: String,
    primaryErrorCode: Int,
    onGoBack: () -> Unit,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f * scrimAlpha.value))
            .clickable(

                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { dismissAnimated(onGoBack) },
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
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.GppMaybe,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connection is not private",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = sslErrorSummary(primaryErrorCode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 16.dp),
                )

                Text(
                    text = "Attackers might be trying to steal your information from this site (for example, passwords, messages, or credit cards). WormHole recommends going back and not entering any information on this page.",
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
                    Button(
                        onClick = { dismissAnimated(onGoBack) },
                        modifier = Modifier.weight(1f),
                        shape = WormHoleSurface.PillShape,
                    ) {
                        Text("Go back", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

// primaryErrorCode is a WebRequestError code (ERROR_CATEGORY_SECURITY), not
// android.net.http.SslError -- this app runs on GeckoView, not WebView, and
// the two error-code spaces don't overlap. GeckoView doesn't expose a
// public API to distinguish "expired" from "wrong host" from "untrusted
// issuer" the way SslError did, so this stays generic rather than matching
// against constants from the wrong API that would never actually fire.
private fun sslErrorSummary(primaryErrorCode: Int): String =
    "WormHole can't verify this site's security certificate."
