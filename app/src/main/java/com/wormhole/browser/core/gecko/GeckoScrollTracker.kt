package com.wormhole.browser.core.gecko

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.abs
import org.mozilla.geckoview.GeckoSession

/**
 * One scroll stream for the dynamic toolbar.
 *
 * Compositor updates win. The JS probe only runs when the compositor has been
 * quiet (inner overflow boxes). Both delegates are not allowed to emit at once.
 */
class GeckoScrollTracker(
    private val session: GeckoSession,
    private val onScroll: (deltaY: Int, scrollY: Int, isScrollable: Boolean) -> Unit,
    private val onScrollSettled: () -> Unit = {},
    private val intervalMs: Long = 80L,
    private val settleMs: Long = 240L,
) {
    private val main = Handler(Looper.getMainLooper())
    private var lastY = 0
    private var hasSample = false
    private var lastCompositorAt = 0L
    private var compositorBound = false
    private var running = false

    private val jsProbe = """
        (function(){
          var y = window.pageYOffset || window.scrollY ||
            (document.documentElement && document.documentElement.scrollTop) || 0;
          var sh = Math.max(
            document.body ? document.body.scrollHeight : 0,
            document.documentElement ? document.documentElement.scrollHeight : 0
          );
          var vh = window.innerHeight || 0;
          return JSON.stringify({y:y|0, scrollable:(sh-vh)>48});
        })();
    """.trimIndent()

    private val settleRunnable = Runnable {
        if (running) onScrollSettled()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            GeckoJs.evaluateAsync(session, jsProbe)?.accept { raw ->
                if (!running) return@accept
                try {
                    val cleaned = raw?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: return@accept
                    val yMatch = Regex("\"y\"\\s*:\\s*(-?\\d+)").find(cleaned)
                    val sMatch = Regex("\"scrollable\"\\s*:\\s*(true|false)").find(cleaned)
                    val y = yMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@accept
                    val scrollable = sMatch?.groupValues?.get(1) == "true"
                    emitFromProbe(y, scrollable)
                } catch (_: Throwable) {
                }
            }
            main.postDelayed(this, intervalMs)
        }
    }

    fun start() {
        if (running) return
        running = true
        bindGeckoDelegates()
        main.post(tick)
    }

    fun reset() {
        lastY = 0
        hasSample = false
        lastCompositorAt = 0L
        main.removeCallbacks(settleRunnable)
    }

    fun stop() {
        running = false
        main.removeCallbacks(tick)
        main.removeCallbacks(settleRunnable)
        reset()
        runCatching { session.scrollDelegate = null }
        runCatching { session.setCompositorScrollDelegate(null) }
        compositorBound = false
    }

    private fun bindGeckoDelegates() {
        compositorBound = false
        runCatching {
            session.setCompositorScrollDelegate(
                object : GeckoSession.CompositorScrollDelegate {
                    override fun onScrollChanged(
                        session: GeckoSession,
                        update: GeckoSession.ScrollPositionUpdate,
                    ) {
                        compositorBound = true
                        emit(update.scrollY.toInt(), fromCompositor = true)
                    }
                },
            )
            compositorBound = true
        }
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                if (compositorBound) return
                emit(scrollY, fromCompositor = true)
            }
        }
    }

    private fun emit(y: Int, fromCompositor: Boolean) {
        if (!running) return
        if (!hasSample) {
            lastY = y
            hasSample = true
            return
        }
        val delta = y - lastY
        lastY = y
        if (fromCompositor) lastCompositorAt = SystemClock.uptimeMillis()
        if (abs(delta) < 2) return
        onScroll(delta, y.coerceAtLeast(0), true)
        scheduleSettle()
    }

    private fun emitFromProbe(y: Int, scrollable: Boolean) {
        if (SystemClock.uptimeMillis() - lastCompositorAt < 220L) return
        if (!hasSample) {
            lastY = y
            hasSample = true
            return
        }
        val delta = y - lastY
        lastY = y
        if (abs(delta) < 3) return
        onScroll(delta, y.coerceAtLeast(0), scrollable)
        scheduleSettle()
    }

    private fun scheduleSettle() {
        main.removeCallbacks(settleRunnable)
        main.postDelayed(settleRunnable, settleMs)
    }
}
