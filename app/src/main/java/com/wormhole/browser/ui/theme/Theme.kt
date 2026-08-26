package com.wormhole.browser.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

/**
 * WormHole's own dark palette — graphite surfaces, white/black accent.
 * Material You / wallpaper dynamic color is intentionally not used so the
 * entire UI stays on the app's core theme.
 */
private val WormHoleDarkScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2A2A2A),
    onPrimaryContainer = Color.White,
    secondary = WormHoleSecondaryDark,
    background = WormHoleBackgroundDark,
    onBackground = WormHoleOnBackgroundDark,
    surface = WormHoleSurfaceDark,
    onSurface = WormHoleOnSurfaceDark,
    surfaceVariant = WormHoleSurfaceVariantDark,
    onSurfaceVariant = WormHoleOnSurfaceVariantDark,
    surfaceContainer = WormHoleSurfaceContainerDark,
    surfaceContainerHigh = WormHoleSurfaceContainerHighDark,
    surfaceContainerLowest = WormHoleBarBackgroundDark,
    surfaceContainerLow = WormHoleSurfaceContainerDark,
    surfaceContainerHighest = WormHoleSurfaceContainerHighDark,
    outline = WormHoleOutlineDark,
    outlineVariant = WormHoleOutlineDark.copy(alpha = 0.4f),
    error = WormHoleErrorDark,
    onError = WormHoleOnErrorDark,
)

private val WormHoleLightScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E8E8),
    onPrimaryContainer = Color.Black,
    secondary = WormHoleSecondaryLight,
    background = WormHoleBackgroundLight,
    onBackground = WormHoleOnBackgroundLight,
    surface = Color.White,
    onSurface = WormHoleOnSurfaceLight,
    surfaceVariant = WormHoleSurfaceVariantLight,
    onSurfaceVariant = WormHoleOnSurfaceVariantLight,
    surfaceContainer = WormHoleSurfaceContainerLight,
    surfaceContainerHigh = WormHoleSurfaceContainerHighLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = WormHoleSurfaceContainerLight,
    surfaceContainerHighest = WormHoleSurfaceContainerHighLight,
    outline = WormHoleOutlineLight,
    outlineVariant = WormHoleOutlineLight.copy(alpha = 0.5f),
    error = WormHoleError,
    onError = WormHoleOnErrorLight,
)

/**
 * App-wide theme. Always uses WormHole's core color schemes — never Material You
 * wallpaper colors — so every screen, sheet, and control shares one visual language.
 *
 * [dynamicColor] is kept for API compatibility but is ignored; the app owns its look.
 */
@Composable
fun WormHoleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // App core theme only — ignore system Material You / dynamic wallpaper colors.
    val colorScheme = if (darkTheme) WormHoleDarkScheme else WormHoleLightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val backgroundArgb = colorScheme.background.toArgb()
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(backgroundArgb))
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WormHoleTypography,
        shapes = WormHoleShapes,
        content = content,
    )
}
