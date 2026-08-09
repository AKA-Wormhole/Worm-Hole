package com.knot.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import com.knot.browser.core.browser.BrowserViewModel
import com.knot.browser.core.settings.ThemeMode
import com.knot.browser.core.webview.WebViewPool
import com.knot.browser.ui.browser.BrowserScreen
import com.knot.browser.ui.theme.KnotTheme

class MainActivity : ComponentActivity() {

    private val browserViewModel: BrowserViewModel by viewModels()

    // Activity-scoped, not ViewModel-scoped: WebViewPool holds real
    // Android View instances, which must never survive a configuration
    // change the way ViewModel state does (that's how you get
    // "WebView leaked a Context" crashes). It's torn down explicitly
    // in onDestroy below.
    private val webViewPool = WebViewPool()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val activeTab = browserViewModel.uiState.value.activeTab
                    val activeWebView = activeTab?.let { webViewPool.get(it.id) }
                    when {
                        activeWebView?.canGoBack() == true -> activeWebView.goBack()
                        else -> {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                }
            },
        )

        setContent {
            val themeMode by browserViewModel.themeMode.collectAsState()
            val systemInDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemInDarkTheme
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            KnotTheme(darkTheme = darkTheme) {
                BrowserScreen(
                    viewModel = browserViewModel,
                    webViewPool = remember { webViewPool },
                )
            }
        }
    }

    override fun onDestroy() {
        webViewPool.destroyAll()
        super.onDestroy()
    }
}
