package com.wormhole.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.wormhole.browser.core.browser.BrowserViewModel
import com.wormhole.browser.core.settings.ThemeMode
import com.wormhole.browser.core.gecko.GeckoSessionPool
import com.wormhole.browser.ui.browser.BrowserScreen
import com.wormhole.browser.ui.theme.HighRefreshRate
import com.wormhole.browser.ui.theme.WormHoleBarBackground
import com.wormhole.browser.ui.theme.WormHoleStatusBar
import com.wormhole.browser.ui.theme.WormHoleTheme

class MainActivity : FragmentActivity() {

    private val browserViewModel: BrowserViewModel by viewModels()

    private val geckoSessionPool = GeckoSessionPool()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Same color as the in-app bottom bar — system nav bar never goes transparent/hidden.
        val navBarColor = WormHoleBarBackground.toArgb()
        val statusBarColor = WormHoleStatusBar.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(statusBarColor),
            navigationBarStyle = SystemBarStyle.dark(navBarColor),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            // Keep system navigation bar visible (no swipe-away immersive).
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }

        handleViewIntent(intent)
        HighRefreshRate.apply(this)

        setContent {
            val themeMode by browserViewModel.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            val dynamicColorEnabled by browserViewModel.dynamicColorEnabled.collectAsState()
            val statusBarColor = if (darkTheme) {
                com.wormhole.browser.ui.theme.WormHoleStatusBarDark.toArgb()
            } else {
                com.wormhole.browser.ui.theme.WormHoleStatusBarLight.toArgb()
            }
            val navBarColor = if (darkTheme) {
                com.wormhole.browser.ui.theme.WormHoleBarBackgroundDark.toArgb()
            } else {
                com.wormhole.browser.ui.theme.WormHoleBarBackgroundLight.toArgb()
            }

            var isWebViewVisible by remember { mutableStateOf(false) }

            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(statusBarColor)
                    } else {
                        SystemBarStyle.light(statusBarColor, statusBarColor)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(navBarColor)
                    } else {
                        SystemBarStyle.light(navBarColor, navBarColor)
                    },
                )
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                }
                HighRefreshRate.apply(this@MainActivity)
            }

            WormHoleTheme(darkTheme = darkTheme, dynamicColor = dynamicColorEnabled) {
                BrowserScreen(
                    viewModel = browserViewModel,
                    geckoSessionPool = remember { geckoSessionPool },
                    onWebViewVisibleChanged = { isWebViewVisible = it },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        HighRefreshRate.apply(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) HighRefreshRate.apply(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val url = intent.dataString ?: return
                browserViewModel.openExternalUrl(url)
            }
            Intent.ACTION_WEB_SEARCH -> {
                val query = intent.getStringExtra(android.app.SearchManager.QUERY)?.trim().orEmpty()
                if (query.isNotEmpty()) {
                    browserViewModel.openExternalUrl(browserViewModel.resolveInput(query))
                }
            }
        }
    }

    override fun onDestroy() {
        geckoSessionPool.destroyAll()
        super.onDestroy()
    }
}
