package com.wormhole.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wormhole.browser.core.library.LibraryEntry
import com.wormhole.browser.core.search.DiscoverClient
import com.wormhole.browser.core.webview.FaviconCache
import com.wormhole.browser.core.webview.HistoryThumbnailCache
import com.wormhole.browser.core.webview.RemoteImageCache
import com.wormhole.browser.ui.theme.bouncyClickable
import kotlinx.coroutines.launch

@Composable
internal fun ContinueBrowsingRow(
    history: List<LibraryEntry>,
    onOpen: (LibraryEntry) -> Unit,
    onOpenHistory: () -> Unit,
) {
    val items = remember(history) { history.distinctBy { it.url }.take(8) }
    if (items.isEmpty()) return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = ((screenWidth - 40.dp - 20.dp) / 3).coerceAtLeast(132.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Continue browsing",
            action = "Show all  >",
            onAction = onOpenHistory,
            muted = muted,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            items(items, key = { it.url }) { entry ->
                ContinueBrowsingPreviewCard(
                    entry = entry,
                    width = cardWidth,
                    onOpen = { onOpen(entry) },
                )
            }
        }
    }
}

@Composable
private fun ContinueBrowsingPreviewCard(
    entry: LibraryEntry,
    width: androidx.compose.ui.unit.Dp,
    onOpen: () -> Unit,
) {
    LaunchedEffect(entry.url) { FaviconCache.fetchAndCache(entry.url) }
    val favicon = FaviconCache.get(entry.url)
    val thumb = HistoryThumbnailCache.get(entry.url)
    val dark = homeIsDark()
    val host = try {
        java.net.URI(entry.url).host?.removePrefix("www.") ?: entry.url
    } catch (_: Exception) {
        entry.url
    }
    val cardShape = RoundedCornerShape(16.dp)
    Surface(
        shape = cardShape,
        color = continueCardFill(),
        border = androidx.compose.foundation.BorderStroke(1.dp, continueCardStroke()),
        modifier = Modifier
            .width(width)
            .clip(cardShape)
            .bouncyClickable(onClick = onOpen),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(continueThumbFill()),
                contentAlignment = Alignment.Center,
            ) {
                if (thumb != null && !thumb.isRecycled) {
                    androidx.compose.foundation.Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (favicon != null && !favicon.isRecycled) {
                    androidx.compose.foundation.Image(
                        bitmap = favicon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                } else {
                    Text(
                        entry.title.firstOrNull()?.uppercase() ?: "W",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (dark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.4f),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    entry.title.ifBlank { host },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (dark) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (favicon != null && !favicon.isRecycled) {
                        androidx.compose.foundation.Image(
                            bitmap = favicon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)),
                        )
                    }
                    Text(
                        host,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dark) Color.White.copy(alpha = 0.62f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DiscoverSection(
    bookmarkedUrls: Set<String>,
    onOpen: (DiscoverClient.Story) -> Unit,
    onToggleBookmark: (DiscoverClient.Story) -> Unit,
) {
    var stories by remember { mutableStateOf<List<DiscoverClient.Story>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = ((screenWidth - 40.dp - 20.dp) / 3).coerceAtLeast(132.dp)

    LaunchedEffect(Unit) {
        stories = DiscoverClient.load()
        loading = false
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = "Discover",
            action = if (loading) "Loading" else "Refresh",
            onAction = {
                scope.launch {
                    loading = true
                    stories = DiscoverClient.load(force = true)
                    loading = false
                }
            },
            muted = muted,
        )
        Spacer(Modifier.height(10.dp))
        when {
            stories.isEmpty() && loading -> {
                Text("Finding stories…", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            stories.isEmpty() -> {
                Text("Nothing new right now", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            else -> {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 4.dp),
                ) {
                    items(stories.take(5), key = { it.url }) { story ->
                        DiscoverHomeCard(
                            story = story,
                            saved = story.url in bookmarkedUrls,
                            width = cardWidth,
                            onOpen = { onOpen(story) },
                            onToggleBookmark = { onToggleBookmark(story) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    action: String,
    onAction: () -> Unit,
    muted: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = muted)
        Text(
            action,
            style = MaterialTheme.typography.labelLarge,
            color = muted,
            modifier = Modifier.bouncyClickable(onClick = onAction),
        )
    }
}

@Composable
private fun DiscoverHomeCard(
    story: DiscoverClient.Story,
    saved: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onOpen: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    LaunchedEffect(story.url, story.imageUrl) {
        FaviconCache.fetchAndCache(story.url)
        story.imageUrl?.let { RemoteImageCache.fetch(it) }
    }
    val favicon = FaviconCache.get(story.url)
    val remote = story.imageUrl?.let { RemoteImageCache.get(it) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val cardShape = RoundedCornerShape(16.dp)
    Surface(
        shape = cardShape,
        color = continueCardFill(),
        border = androidx.compose.foundation.BorderStroke(1.dp, continueCardStroke()),
        modifier = Modifier
            .width(width)
            .clip(cardShape)
            .bouncyClickable(onClick = onOpen),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(continueThumbFill()),
                contentAlignment = Alignment.Center,
            ) {
                if (remote != null && !remote.isRecycled) {
                    androidx.compose.foundation.Image(
                        bitmap = remote.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (favicon != null && !favicon.isRecycled) {
                    androidx.compose.foundation.Image(
                        bitmap = favicon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Text(
                        story.title.firstOrNull()?.uppercase() ?: "D",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (dark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.4f),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    story.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (dark) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${story.source}  ·  ${story.readMinutes}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) Color.White.copy(alpha = 0.62f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (saved) "Saved" else "Save",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (dark) Color.White.copy(alpha = 0.62f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.bouncyClickable(onClick = onToggleBookmark),
                )
            }
        }
    }
}

@Composable
private fun HomePreviewCard(
    title: String,
    subtitle: String,
    pageUrl: String,
    imageUrl: String?,
    fallbackLetter: String,
    historyThumb: android.graphics.Bitmap?,
    onClick: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    LaunchedEffect(pageUrl, imageUrl) {
        FaviconCache.fetchAndCache(pageUrl)
        imageUrl?.let { RemoteImageCache.fetch(it) }
    }
    val favicon = FaviconCache.get(pageUrl)
    val remote = imageUrl?.let { RemoteImageCache.get(it) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val cardShape = RoundedCornerShape(16.dp)
    Surface(
        shape = cardShape,
        color = continueCardFill(),
        border = androidx.compose.foundation.BorderStroke(1.dp, continueCardStroke()),
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .bouncyClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 108.dp, height = 84.dp)
                    .background(continueThumbFill()),
                contentAlignment = Alignment.Center,
            ) {
                val thumb = when {
                    historyThumb != null && !historyThumb.isRecycled -> historyThumb
                    remote != null && !remote.isRecycled -> remote
                    else -> null
                }
                if (thumb != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (favicon != null && !favicon.isRecycled) {
                    androidx.compose.foundation.Image(
                        bitmap = favicon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                } else {
                    Text(
                        fallbackLetter,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (dark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.4f),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (dark) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (favicon != null && !favicon.isRecycled) {
                        androidx.compose.foundation.Image(
                            bitmap = favicon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dark) Color.White.copy(alpha = 0.62f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (actionLabel != null && onAction != null) {
                    Text(
                        actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (dark) Color.White.copy(alpha = 0.62f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.bouncyClickable(onClick = onAction),
                    )
                }
            }
        }
    }
}
