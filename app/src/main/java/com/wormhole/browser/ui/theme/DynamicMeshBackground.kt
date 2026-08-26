package com.wormhole.browser.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.wormhole.browser.core.weather.LastKnownLocation
import com.wormhole.browser.core.weather.WeatherClient
import com.wormhole.browser.core.weather.WeatherCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import kotlin.math.roundToInt

@Composable
fun DynamicMeshBackground(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isDark = isSystemInDarkTheme()

    if (!enabled) {

        androidx.compose.foundation.layout.Box(
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val context = LocalContext.current
    val stops = remember(isDark) { timeOfDayStops(isDark) }

    var weatherCondition by remember { mutableStateOf<WeatherCondition?>(null) }

    LaunchedEffect(Unit) {
        val location = withContext(Dispatchers.IO) { LastKnownLocation.get(context) } ?: return@LaunchedEffect
        val snapshot = withContext(Dispatchers.IO) {
            runCatching { WeatherClient().fetchCurrent(location.latitude, location.longitude) }.getOrNull()
        }
        weatherCondition = snapshot?.condition
    }

    val weatherTint = weatherTintFor(weatherCondition)

    val topColor: Color by animateColorAsState(
        targetValue = weatherTint?.let { lerp(stops.top, it, 0.35f) } ?: stops.top,
        animationSpec = tween(durationMillis = 1200),
        label = "meshTop",
    )
    val midColor: Color by animateColorAsState(
        targetValue = weatherTint?.let { lerp(stops.mid, it, 0.22f) } ?: stops.mid,
        animationSpec = tween(durationMillis = 1200),
        label = "meshMid",
    )
    val bottomColor: Color by animateColorAsState(
        targetValue = stops.bottom,
        animationSpec = tween(durationMillis = 1200),
        label = "meshBottom",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(topColor, midColor, bottomColor),
                startY = 0f,
                endY = height,
            ),
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(topColor.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(width * 0.85f, height * 0.05f),
                radius = width * 0.9f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(midColor.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(width * 0.1f, height * 0.85f),
                radius = width * 1.0f,
            ),
        )
    }
}

private data class MeshStops(val top: Color, val mid: Color, val bottom: Color)

private fun timeOfDayStops(isDark: Boolean): MeshStops {
    val now = LocalTime.now()
    val hour = now.hour + now.minute / 60f

    val keyframes = listOf(
        5f to MeshDawn,
        8f to MeshMorning,
        12f to MeshMidday,
        15f to MeshAfternoon,
        18f to MeshEvening,
        20f to MeshDusk,
        22f to MeshNight,
        26f to MeshLateNight,
    )

    val wrappedHour = if (hour < 5f) hour + 24f else hour
    var lower = keyframes.first()
    var upper = keyframes.last()
    for (i in 0 until keyframes.size - 1) {
        if (wrappedHour >= keyframes[i].first && wrappedHour <= keyframes[i + 1].first) {
            lower = keyframes[i]
            upper = keyframes[i + 1]
            break
        }
    }
    val span = (upper.first - lower.first).coerceAtLeast(0.01f)
    val t = ((wrappedHour - lower.first) / span).coerceIn(0f, 1f)
    val top = lerp(lower.second, upper.second, t)

    return if (isDark) {

        val darkTop = lerp(top, MeshNight, 0.55f)
        MeshStops(
            top = darkTop,
            mid = lerp(darkTop, MeshLateNight, 0.5f),
            bottom = WormHoleBackgroundDark,
        )
    } else {
        MeshStops(
            top = top,
            mid = lerp(top, Color.White, 0.35f),
            bottom = WormHoleBackgroundLight,
        )
    }
}

private fun weatherTintFor(condition: WeatherCondition?): Color? = when (condition) {
    WeatherCondition.CLEAR -> MeshClearTint
    WeatherCondition.CLOUDY, WeatherCondition.FOG -> MeshCloudyTint
    WeatherCondition.RAIN -> MeshRainTint
    WeatherCondition.SNOW -> MeshSnowTint
    WeatherCondition.STORM -> MeshStormTint
    WeatherCondition.UNKNOWN, null -> null
}
