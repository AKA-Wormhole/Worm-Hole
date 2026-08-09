package com.knot.browser.ui.sidebar

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knot.browser.core.browser.Space
import com.knot.browser.core.browser.Tab
import com.knot.browser.core.webview.TabThumbnailCache
import com.knot.browser.ui.theme.KnotMotion
import com.knot.browser.ui.theme.bouncyClickable

/**
 * A single card in the full-screen tab grid -- replaces the old plain
 * TabListRow list per the switch to a Comet-style thumbnail grid. The
 * grid now spans the full screen width with an adaptive column count
 * (see TAB_GRID_MIN_COLUMN_WIDTH in KnotSidebar.kt), so this card stays
 * compact regardless of how many columns fit: a live WebView thumbnail
 * as the card body, a favicon + close button on top, and a title
 * underneath the card rather than crammed inside it.
 *
 * Uses a 3:4 portrait aspect ratio, matching the shape of an actual
 * webpage viewport rather than a square. At typical column widths a
 * 1:1 card reads as a stubby placeholder even once a real WebView
 * thumbnail fills it in -- there's too little height for a page
 * snapshot to look like a page. 3:4 gives the thumbnail enough
 * vertical room to read as "a website" at a glance, without the card
 * growing so tall that only one row is visible on screen at a time.
 *
 * Every fresh instance of this composable (i.e. every new tab) pops in
 * from 0.72x scale with [KnotMotion.bouncy] rather than appearing at
 * full size instantly -- paired with the LazyVerticalGrid's
 * `animateItem()` in KnotSidebar, this is what makes creating a tab feel
 * like something landed in the grid instead of the grid just relaying
 * out.
 */
@Composable
fun TabGridCard(
    tab: Tab,
    isActive: Boolean,
    spaceAccent: Space?,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entranceScale = remember { Animatable(0.72f) }
    LaunchedEffect(Unit) {
        entranceScale.animateTo(1f, animationSpec = KnotMotion.bouncy())
    }

    Column(modifier = modifier.scale(entranceScale.value)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = if (isActive) {
                BorderStroke(2.dp, spaceAccent?.accent?.color ?: MaterialTheme.colorScheme.primary)
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .bouncyClickable(role = Role.Tab, onClick = onClick),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val thumbnail = TabThumbnailCache.get(tab.id)
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                        .bouncyClickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close tab",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 2.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FaviconDot(accentColor = spaceAccent?.accent?.color)
            Text(
                text = tab.title.ifBlank { "New Tab" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun FaviconDot(accentColor: Color?) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(accentColor?.copy(alpha = 0.15f) ?: MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Language,
            contentDescription = null,
            tint = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(9.dp),
        )
    }
}
