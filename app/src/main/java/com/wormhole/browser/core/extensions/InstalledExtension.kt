package com.wormhole.browser.core.extensions

import android.graphics.Bitmap

/**
 * Why an extension isn't running, mirrored from
 * [org.mozilla.geckoview.WebExtension.MetaData.disabledFlags] so the UI can
 * explain the state instead of just showing an inert toggle.
 */
enum class ExtensionDisabledReason {
    /** Not disabled -- [InstalledExtension.isEnabled] is true. */
    NONE,

    /** The user turned it off (our own enable()/disable() calls land here). */
    USER,

    /** Blocklisted by Mozilla/AMO for safety reasons; the user can't re-enable it. */
    BLOCKLISTED,

    /** Disabled by the app itself (e.g. incompatible signature state). */
    APP_DISABLED,

    /** Disabled pending the user granting permissions the extension now requires. */
    APP_SUPPORT,

    /** disabledFlags was nonzero but didn't match a known bit; still off, reason unclear. */
    UNKNOWN,
}

/**
 * A user-facing view of a [org.mozilla.geckoview.WebExtension] that's
 * currently installed in the runtime.
 */
data class InstalledExtension(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val iconUrl: String?,
    val iconBitmap: Bitmap? = null,
    val isEnabled: Boolean,
    val disabledReason: ExtensionDisabledReason = ExtensionDisabledReason.NONE,
    val homepageUrl: String?,
    /** Origin permissions (host match patterns). */
    val origins: List<String> = emptyList(),
    /** API permissions the extension holds, e.g. "tabs", "webRequest", "storage". */
    val permissions: List<String> = emptyList(),
    /** Optional permissions the extension may request later but doesn't hold yet. */
    val optionalPermissions: List<String> = emptyList(),
    val optionalOrigins: List<String> = emptyList(),
)

/**
 * An entry in the "browse extensions" catalog.
 * [geckoIds] are runtime ids GeckoView uses after install (differs from AMO slug).
 */
data class CatalogExtension(
    val id: String,
    val name: String,
    val summary: String,
    val author: String,
    val xpiUrl: String,
    val iconUrl: String? = null,
    val geckoIds: List<String> = emptyList(),
)

sealed class ExtensionInstallResult {
    data class Success(val extension: InstalledExtension) : ExtensionInstallResult()
    data class Failure(val message: String) : ExtensionInstallResult()
}
