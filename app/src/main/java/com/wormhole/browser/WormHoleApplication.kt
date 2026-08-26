package com.wormhole.browser

import android.app.Application
import com.wormhole.browser.core.crash.CrashHandler
import com.wormhole.browser.core.log.AppLog
import com.wormhole.browser.core.downloads.DownloadRepository
import com.wormhole.browser.core.gecko.GeckoRuntimeHolder
import com.wormhole.browser.core.settings.SettingsRepository
import com.wormhole.browser.core.webview.FaviconCache
import com.wormhole.browser.core.webview.TabThumbnailCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WormHoleApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        AppLog.init(this)
        CrashHandler.install(this)
        AppLog.i("App", "WormHole started")
        FaviconCache.init(this)
        TabThumbnailCache.init(this)

        // Warm Gecko runtime early so first tab opens faster. Read the user's
        // real "website appearance" setting first so the runtime isn't created
        // with the hardcoded dark-mode default (which forced prefers-color-scheme:
        // dark on every site until the setting caught up, leaving pages looking
        // blank/dark-grey on first load).
        applicationScope.launch {
            val settingsRepository = SettingsRepository(applicationContext)
            val webDarkModeEnabled = runCatching {
                settingsRepository.webDarkModeEnabled.first()
            }.getOrDefault(true)
            val trackerBlockingEnabled = runCatching {
                settingsRepository.trackerBlockingEnabled.first()
            }.getOrDefault(true)
            val adBlockingEnabled = runCatching {
                settingsRepository.adBlockingEnabled.first()
            }.getOrDefault(true)
            // Do not silently swallow a failed runtime creation: if this throws
            // (bad GeckoView artifact, native lib load failure, etc.) every tab
            // will fail to render with no visible error, so record it the same
            // way an uncaught exception would be recorded.
            GeckoRuntimeHolder.setContentPrefersDark(webDarkModeEnabled)
            GeckoRuntimeHolder.setContentBlocking(trackerBlockingEnabled, adBlockingEnabled)
            DownloadRepository.resumeIncomplete(applicationContext)
        }
    }
}
