package com.wormhole.browser.core.webview.blocking

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

class ContentBlocker(
    private var trackersEnabled: Boolean = true,
    private var adsEnabled: Boolean = true,
    private var popupsEnabled: Boolean = true,
) {
    private val blockedTrackerHosts = BlockList.TRACKERS
    private val blockedAdHosts = BlockList.ADS
    private val blockedPopupHosts = BlockList.POPUP_AD_NETWORKS

    private val trackersBlockedThisPage = AtomicInteger(0)
    private val adsBlockedThisPage = AtomicInteger(0)
    val lastPageTrackersBlocked: Int get() = trackersBlockedThisPage.get()
    val lastPageAdsBlocked: Int get() = adsBlockedThisPage.get()

    fun updateSettings(trackersEnabled: Boolean, adsEnabled: Boolean, popupsEnabled: Boolean) {
        this.trackersEnabled = trackersEnabled
        this.adsEnabled = adsEnabled
        this.popupsEnabled = popupsEnabled
    }

    fun resetPageCounters() {
        trackersBlockedThisPage.set(0)
        adsBlockedThisPage.set(0)
    }

    fun intercept(request: WebResourceRequest, forceAllCategories: Boolean = false): WebResourceResponse? {
        if (request.isForMainFrame) return null
        val blockTrackers = trackersEnabled || forceAllCategories
        val blockAds = adsEnabled || forceAllCategories
        val blockPopups = popupsEnabled || forceAllCategories
        if (!blockTrackers && !blockAds && !blockPopups) return null

        val host = request.url.host?.lowercase() ?: return null

        if (blockPopups && host.matchesAny(blockedPopupHosts)) {
            return EMPTY_RESPONSE
        }
        if (blockTrackers && host.matchesAny(blockedTrackerHosts)) {
            trackersBlockedThisPage.incrementAndGet()
            return EMPTY_RESPONSE
        }
        if (blockAds && host.matchesAny(blockedAdHosts)) {
            adsBlockedThisPage.incrementAndGet()
            return EMPTY_RESPONSE
        }
        return null
    }

    internal fun String.matchesAny(hosts: Set<String>): Boolean {
        var candidate = this
        while (true) {
            if (candidate in hosts) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    companion object {

        private val EMPTY_RESPONSE: WebResourceResponse
            get() = WebResourceResponse(
                "text/plain",
                "UTF-8",
                ByteArrayInputStream(ByteArray(0)),
            )
    }
}
