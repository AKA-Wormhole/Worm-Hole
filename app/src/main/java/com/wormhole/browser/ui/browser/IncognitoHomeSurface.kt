package com.wormhole.browser.ui.browser

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wormhole.browser.ui.theme.bouncyClickable

/**
 * Blank-tab home for private/incognito browsing.
 * Gray/white only, no gradients or flashy motion — calm private mode.
 */
@Composable
fun IncognitoHomeSurface(
    tabCount: Int,
    onSearchClick: () -> Unit,
    onVoiceSearch: (String) -> Unit = {},
    onTabSwitcherClick: () -> Unit,
    onMenuClick: () -> Unit,
    isMenuOpen: Boolean = false,
    onMenuDismiss: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onLibraryClick: () -> Unit = {},
    onHistoryClick: () -> Unit = onLibraryClick,
    onPasswordsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNewIncognitoTabClick: () -> Unit = {},
    onAssistantClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ink = Color.White.copy(alpha = 0.92f)
    val muted = Color.White.copy(alpha = 0.55f)
    val pill = Color(0xFF162544)
    var menuAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var showActivityInfo by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1A3A))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            IncognitoHatGlassesIcon(
                modifier = Modifier.size(88.dp),
                color = ink,
            )

            Text(
                "Browse privately",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                ),
                color = ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                "Your activity won’t be visible on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 320.dp)
                    .bouncyClickable(onClick = { showActivityInfo = true }),
            )
            Text(
                "Learn more",
                style = MaterialTheme.typography.labelLarge,
                color = ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .bouncyClickable(onClick = { showActivityInfo = true }),
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bottom bar — subtle, gray/white only
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, top = 8.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = pill,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .bouncyClickable(onClick = onSearchClick),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Shield, null, tint = muted, modifier = Modifier.size(18.dp))
                        Text("Search", color = muted, modifier = Modifier.weight(1f))
                        VoiceMicButton(onResult = onVoiceSearch, tint = muted, iconSize = 18.dp)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.5.dp, ink.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .bouncyClickable(onClick = onTabSwitcherClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tabCount.coerceAtLeast(0).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = ink,
                    )
                }
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New incognito tab",
                    tint = ink,
                    modifier = Modifier.size(22.dp).bouncyClickable(onClick = onNewIncognitoTabClick),
                )
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = ink.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(24.dp)
                        .onGloballyPositioned { coords ->
                            menuAnchorBounds = coords.boundsInWindow()
                        }
                        .bouncyClickable(onClick = onMenuClick),
                )
            }

            HomeToolsMenu(
                isExpanded = isMenuOpen,
                onDismiss = onMenuDismiss,
                onDownloadsClick = onDownloadsClick,
                onLibraryClick = onLibraryClick,
                onHistoryClick = onHistoryClick,
                onPasswordsClick = onPasswordsClick,
                onSettingsClick = onSettingsClick,
                onNewIncognitoTabClick = onNewIncognitoTabClick,
                onAssistantClick = onAssistantClick,
                anchorBounds = menuAnchorBounds,
            )
        }
    }

    if (showActivityInfo) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showActivityInfo = false },
            title = { Text("Who can see your activity") },
            text = {
                Text(
                    "Wormhole does not save private-tab history, cookies, or form data on this device after you close those tabs.\n\n" +
                        "Websites you visit can still see that visit. Your school, workplace, or internet provider can still see the sites you open. " +
                        "Signed-in accounts on a site can still show that activity.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showActivityInfo = false }) {
                    Text("Got it")
                }
            },
        )
    }
}

/** Simple hat + glasses mark, drawn with Canvas (no external asset). */
@Composable
fun IncognitoHatGlassesIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.055f, cap = StrokeCap.Round)

        // Hat brim
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.38f),
            size = Size(w * 0.76f, h * 0.08f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
        )
        // Hat crown
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.28f, h * 0.12f),
            size = Size(w * 0.44f, h * 0.30f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
        )

        // Glasses — left lens
        drawCircle(
            color = color,
            radius = w * 0.13f,
            center = Offset(w * 0.32f, h * 0.68f),
            style = stroke,
        )
        // Glasses — right lens
        drawCircle(
            color = color,
            radius = w * 0.13f,
            center = Offset(w * 0.68f, h * 0.68f),
            style = stroke,
        )
        // Bridge
        drawLine(
            color = color,
            start = Offset(w * 0.45f, h * 0.68f),
            end = Offset(w * 0.55f, h * 0.68f),
            strokeWidth = w * 0.04f,
            cap = StrokeCap.Round,
        )
    }
}
