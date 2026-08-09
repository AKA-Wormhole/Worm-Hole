package com.knot.browser.ui.sidebar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knot.browser.core.browser.Space
import com.knot.browser.core.browser.Tab
import com.knot.browser.ui.theme.KnotMotion
import com.knot.browser.ui.theme.bouncyClickable
import com.knot.browser.ui.theme.spinBounceClickable

// Adaptive minimum column width instead of a fixed 2-column grid -- on a
// narrow phone this settles at 2 columns same as before, but a wider
// phone or a tablet naturally gets 3+ without any code change, and
// cards never get cramped below this width.
private val TAB_GRID_MIN_COLUMN_WIDTH = 140.dp

/**
 * The tab switcher, shown as a full-screen overlay rather than a side
 * panel (UI_DESIGN_BRIEF.md section 2.1, revised): Space switcher up
 * top, a close button to dismiss, the active Space's tab grid filling
 * the middle, settings + new-tab actions pinned to the bottom. Fading
 * + scaling in as a full screen reads much better on phones than the
 * old fixed 280dp side column, which was cramped for a 2-column grid.
 *
 * This composable owns no state itself -- everything is hoisted, matching
 * the pattern the rest of Knot's UI already follows (BrowserScreen,
 * AddressBar).
 */
@Composable
fun KnotSidebar(
    isExpanded: Boolean,
    spaces: List<Space>,
    activeSpaceId: String,
    tabsInActiveSpace: List<Tab>,
    activeTabId: String?,
    onSpaceSelected: (String) -> Unit,
    onAddSpace: () -> Unit,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTab: () -> Unit,
    onSettingsClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeSpace = spaces.firstOrNull { it.id == activeSpaceId }

    AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.96f, animationSpec = KnotMotion.bouncy()),
        exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.96f, animationSpec = tween(150)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpaceSwitcher(
                        spaces = spaces,
                        activeSpaceId = activeSpaceId,
                        onSpaceSelected = onSpaceSelected,
                        onAddSpace = onAddSpace,
                        modifier = Modifier.weight(1f),
                    )

                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .bouncyClickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close tabs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Text(
                    text = activeSpace?.name.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = TAB_GRID_MIN_COLUMN_WIDTH),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(tabsInActiveSpace, key = { it.id }) { tab ->
                        TabGridCard(
                            tab = tab,
                            isActive = tab.id == activeTabId,
                            spaceAccent = activeSpace,
                            onClick = {
                                onTabSelected(tab.id)
                                onClose()
                            },
                            onClose = { onTabClosed(tab.id) },
                            // animateItem() gives every card a spring-driven
                            // slide/resize whenever the grid reflows (a card
                            // closing means every card after it shifts into
                            // its new slot instead of snapping there), and
                            // AnimatedTabGridCard below layers the pop-in
                            // entrance on top for cards that are brand new.
                            modifier = Modifier.animateItem(
                                placementSpec = KnotMotion.bouncy(),
                            ),
                        )
                    }
                }

                SidebarFooter(onNewTab = onNewTab, onSettingsClick = onSettingsClick)
            }
        }
    }
}

@Composable
private fun SidebarFooter(onNewTab: () -> Unit, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .bouncyClickable(onClick = onSettingsClick)
                .padding(8.dp),
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .spinBounceClickable(onClick = onNewTab)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(
                    "New Tab",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
