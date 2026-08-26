package com.wormhole.browser.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * iOS-like motion: creamy chrome with almost no bounce, slightly underdamped
 * popups. Modest stiffness so 120–185 Hz panels interpolate instead of stepping.
 */
object WormHoleMotion {
    val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.2f, 1f)

    /** Bottom bar / sheet slide. Critically damped — bounce reads as flicker. */
    fun <T> chrome() = spring<T>(
        dampingRatio = 0.96f,
        stiffness = 260f,
    )

    fun <T> fluid() = spring<T>(
        dampingRatio = 0.9f,
        stiffness = 240f,
    )

    fun <T> bouncy() = spring<T>(
        dampingRatio = 0.84f,
        stiffness = 220f,
    )

    fun <T> popup() = spring<T>(
        dampingRatio = 0.86f,
        stiffness = 280f,
    )

    fun <T> snappy() = spring<T>(
        dampingRatio = 0.92f,
        stiffness = 380f,
    )

    fun <T> settled() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 280f,
    )

    fun fadeIn() = tween<Float>(durationMillis = 280, easing = EaseOutExpo)
    fun fadeOut() = tween<Float>(durationMillis = 200, easing = EaseInOut)
    fun overlay() = tween<Float>(durationMillis = 320, easing = EaseOutExpo)

    const val PRESS_SCALE = 0.97f
}
