package holocore.browser.app.ui.theme

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
 * HoloCore's own dark palette — graphite surfaces, white/black accent.
 * Material You / wallpaper dynamic color is intentionally not used so the
 * entire UI stays on the app's core theme.
 */
private val HoloCoreDarkScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2A2A2A),
    onPrimaryContainer = Color.White,
    secondary = HoloCoreSecondaryDark,
    background = HoloCoreBackgroundDark,
    onBackground = HoloCoreOnBackgroundDark,
    surface = HoloCoreSurfaceDark,
    onSurface = HoloCoreOnSurfaceDark,
    surfaceVariant = HoloCoreSurfaceVariantDark,
    onSurfaceVariant = HoloCoreOnSurfaceVariantDark,
    surfaceContainer = HoloCoreSurfaceContainerDark,
    surfaceContainerHigh = HoloCoreSurfaceContainerHighDark,
    surfaceContainerLowest = HoloCoreBarBackgroundDark,
    surfaceContainerLow = HoloCoreSurfaceContainerDark,
    surfaceContainerHighest = HoloCoreSurfaceContainerHighDark,
    outline = HoloCoreOutlineDark,
    outlineVariant = HoloCoreOutlineDark.copy(alpha = 0.4f),
    error = HoloCoreErrorDark,
    onError = HoloCoreOnErrorDark,
)

private val HoloCoreLightScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E8E8),
    onPrimaryContainer = Color.Black,
    secondary = HoloCoreSecondaryLight,
    background = HoloCoreBackgroundLight,
    onBackground = HoloCoreOnBackgroundLight,
    surface = Color.White,
    onSurface = HoloCoreOnSurfaceLight,
    surfaceVariant = HoloCoreSurfaceVariantLight,
    onSurfaceVariant = HoloCoreOnSurfaceVariantLight,
    surfaceContainer = HoloCoreSurfaceContainerLight,
    surfaceContainerHigh = HoloCoreSurfaceContainerHighLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = HoloCoreSurfaceContainerLight,
    surfaceContainerHighest = HoloCoreSurfaceContainerHighLight,
    outline = HoloCoreOutlineLight,
    outlineVariant = HoloCoreOutlineLight.copy(alpha = 0.5f),
    error = HoloCoreError,
    onError = HoloCoreOnErrorLight,
)

/**
 * App-wide theme. Always uses HoloCore's core color schemes — never Material You
 * wallpaper colors — so every screen, sheet, and control shares one visual language.
 *
 * [dynamicColor] is kept for API compatibility but is ignored; the app owns its look.
 */
@Composable
fun HoloCoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // App core theme only — ignore system Material You / dynamic wallpaper colors.
    val colorScheme = if (darkTheme) HoloCoreDarkScheme else HoloCoreLightScheme

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
        typography = HoloCoreTypography,
        shapes = HoloCoreShapes,
        content = content,
    )
}
