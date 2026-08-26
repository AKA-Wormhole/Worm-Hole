package com.wormhole.browser.core.gecko

import android.content.Context
import org.mozilla.geckoview.StorageController

object GeckoStorage {
    fun clearBrowsingData(context: Context) {
        try {
            val runtime = GeckoRuntimeHolder.get(context)
            val storage = runtime.storageController
            // Clear all data for all hosts (flags vary by version — use broad clear).
            storage.clearData(StorageController.ClearFlags.ALL).accept { }
        } catch (_: Throwable) {
            try {
                val runtime = GeckoRuntimeHolder.get(context)
                runtime.storageController.clearDataFromHost("*", StorageController.ClearFlags.ALL)
            } catch (_: Throwable) {
            }
        }
    }
}
