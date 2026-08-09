package com.knot.browser.core.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Handles [BrowserEvent.LaunchExternalApp]. WebView can't render schemes
 * like mailto:, tel:, intent:, market:, or app-specific deep links, so
 * these get handed to the system. Wrapped in try/catch since there's no
 * reliable way to check "is there an app that can handle this" up front
 * without racing the actual launch.
 */
object ExternalIntentLauncher {

    fun launch(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app found to open this link", Toast.LENGTH_SHORT).show()
        }
    }
}
