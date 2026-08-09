package com.knot.browser.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** One motion system: fast, restrained and interruption-friendly. */
object KnotMotion {
    fun <T> fluid() = spring<T>(
        dampingRatio = 0.92f,
        stiffness = 650f,
    )

    fun <T> bouncy() = fluid<T>()

    fun <T> snappy() = spring<T>(
        dampingRatio = 0.96f,
        stiffness = 1000f,
    )

    fun <T> settled() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 500f,
    )

    fun fadeIn() = tween<Float>(durationMillis = 140)
    fun fadeOut() = tween<Float>(durationMillis = 100)

    const val PRESS_SCALE = 0.975f
}
