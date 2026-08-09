package com.knot.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knot.browser.core.browser.Space
import com.knot.browser.ui.theme.bouncyClickable

/**
 * The blank-tab / home surface (UI_DESIGN_BRIEF.md section 2.3): the
 * active Space's accent rendered as a soft gradient wallpaper, with the
 * "Knot" wordmark + tagline and a centered search pill that opens the
 * real CommandBar. This pill is the primary (and, on this screen, only)
 * search entry point -- BrowserScreen's BottomBar hides its own search
 * pill while a blank/home tab is active so the two never show at once,
 * and brings its pill back once a page is loaded. Shortcut tiles
 * (frequently visited sites) are listed as a non-goal until history/
 * bookmarks exist, per the brief section 7.
 */
@Composable
fun NewTabSurface(
    activeSpace: Space?,
    onCommandBarRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = activeSpace?.accent?.color ?: MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
.background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Knot",
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                letterSpacing = (-2.0).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "A faster way around the web.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
            )

            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClickable(onClick = onCommandBarRequested),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Search or enter address",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
