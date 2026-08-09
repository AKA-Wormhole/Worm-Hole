package com.knot.browser.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val KnotDarkScheme = darkColorScheme(
    primary = KnotCoral,
    // Was KnotCharcoal (near-black) on top of the mid-tone accent blue --
    // contrast ratio there is well under WCAG AA for text. White on the
    // accent reads cleanly in both schemes and matches how the light
    // scheme already treats onPrimary.
    onPrimary = KnotCreamElevated,
    secondary = KnotViolet,
    background = KnotCharcoal,
    onBackground = KnotInkDark,
    surface = KnotCharcoalElevated,
    onSurface = KnotInkDark,
    surfaceVariant = KnotCharcoalSunken,
    onSurfaceVariant = KnotInkDarkMuted,
    outline = KnotHairlineDark,
    error = KnotError,
)

private val KnotLightScheme = lightColorScheme(
    primary = KnotCoral,
    onPrimary = KnotCreamElevated,
    secondary = KnotViolet,
    background = KnotCream,
    onBackground = KnotInkLight,
    surface = KnotCreamElevated,
    onSurface = KnotInkLight,
    surfaceVariant = KnotCreamSunken,
    onSurfaceVariant = KnotInkLightMuted,
    outline = KnotHairlineLight,
    error = KnotError,
)

@Composable
fun KnotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color (Material You) deliberately off by default: Knot has
    // its own brand identity and shouldn't be re-tinted by the user's
    // wallpaper the way a stock Material app would.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> KnotDarkScheme
        else -> KnotLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KnotTypography,
        content = content,
    )
}
