package com.wormhole.browser.core.extensions

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.wormhole.browser.core.gecko.GeckoRuntimeHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.Image
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import kotlin.coroutines.resume

/**
 * Installs, lists, enables/disables, and uninstalls user-facing
 * WebExtensions (add-ons) on the shared [GeckoRuntime], via
 * [WebExtensionController] -- the same API Fenix/Firefox for Android uses
 * for its own "Add-ons" manager. This is separate from
 * [com.wormhole.browser.core.gecko.GeckoExtensionBridge], which manages the
 * single built-in bridge extension the agent's page tools depend on; that
 * one is internal and never shown in this UI.
 *
 * Extensions installed here run with normal WebExtension permissions
 * (content scripts, browser action, etc.) exactly as they would in desktop
 * Firefox, scoped per the runtime's install.
 */
class ExtensionManager private constructor(private val context: Context) {

    private val _installed = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val installed: StateFlow<List<InstalledExtension>> = _installed.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    /**
     * Current browser-action (toolbar button) state per extension id, kept
     * live by [WebExtension.ActionDelegate.onBrowserAction]. This is what
     * lets an installed extension actually show up somewhere besides this
     * management screen -- a real icon + popup a user can tap, the same way
     * uBlock Origin or Bitwarden show a toolbar button in desktop Firefox.
     */
    private val _browserActions = MutableStateFlow<Map<String, BrowserAction>>(emptyMap())
    val browserActions: StateFlow<Map<String, BrowserAction>> = _browserActions.asStateFlow()

    /**
     * The currently-open extension popup, if any -- a live [GeckoSession]
     * GeckoView is rendering the extension's popup HTML into, plus which
     * extension it belongs to. Null when no popup is open. A StateFlow
     * (not a one-shot event) because the UI needs to know the popup closed
     * just as much as it needs to know one opened, so a sheet observing
     * this can dismiss itself when it flips back to null.
     */
    private val _openPopup = MutableStateFlow<OpenPopup?>(null)
    val openPopup: StateFlow<OpenPopup?> = _openPopup.asStateFlow()

    // One GeckoSession per extension id, created lazily the first time that
    // extension opens a popup and reused after -- avoids tearing down and
    // recreating the popup's page state (e.g. its own in-memory JS state)
    // every single time the user taps the toolbar button.
    private val popupSessions = java.util.concurrent.ConcurrentHashMap<String, GeckoSession>()

    // GeckoView is the source of truth for enabled/disabled via
    // MetaData.enabled + MetaData.disabledFlags (see toInstalledExtension
    // below). This override map exists only as a short-lived stopgap for the
    // gap between us calling enable()/disable() and the next list() call
    // actually reflecting it -- refresh() clears an id's entry as soon as
    // GeckoView's own state agrees with it, so a stale override can never
    // permanently shadow reality.
    private val pendingEnabledOverrides = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    // Extensions whose ActionDelegate we've already registered, so refresh()
    // (called after every install/enable/disable/uninstall) doesn't
    // re-attach a delegate to a handle we're already listening to.
    private val actionDelegatesAttached = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Icon resolution is async (GeckoResult<Bitmap>); cache resolved bitmaps
    // by id so repeated refresh() calls don't refetch, and so a bitmap that
    // resolves after the initial paint can still be pushed into _installed.
    private val resolvedIcons = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    // Keeps the most recent WebExtension handle per id around purely so
    // onBrowserActionClicked can locate the right extension without another
    // round trip through list(). Populated in lockstep with attaching the
    // ActionDelegate below.
    private val lastKnownHandles = java.util.concurrent.ConcurrentHashMap<String, WebExtension>()

    private fun runtime(): GeckoRuntime = GeckoRuntimeHolder.get(context)

    /** Refreshes [installed] from whatever GeckoView currently reports. */
    suspend fun refresh() {
        ensurePromptDelegate()
        val controller = runtime().webExtensionController
        val list = try {
            suspendCancellableCoroutine<List<WebExtension>> { cont ->
                controller.list()
                    .accept(
                        { exts -> if (cont.isActive) cont.resume(exts.orEmpty()) },
                        { err ->
                            _lastError.value = err?.message ?: "Failed to list extensions"
                            if (cont.isActive) cont.resume(emptyList())
                        },
                    )
            }
        } catch (e: Throwable) {
            _lastError.value = e.message ?: "Failed to list extensions"
            emptyList()
        }

        val visible = list.filterNot { it.id == BRIDGE_EXTENSION_ID } // never show the internal bridge

        visible.forEach { ext ->
            attachActionDelegateIfNeeded(ext)
            // GeckoView's own state has caught up (or never disagreed) --
            // drop the stopgap override so future reads come straight from
            // MetaData again.
            val meta = ext.metaData
            if (meta != null && pendingEnabledOverrides[ext.id] == meta.enabled) {
                pendingEnabledOverrides.remove(ext.id)
            }
        }

        _installed.value = visible.map { it.toInstalledExtension() }
    }

    /**
     * Installs an extension from an .xpi URL (e.g. an addons.mozilla.org
     * download link, or any other HTTPS-hosted .xpi). Returns once GeckoView
     * has finished installing (including any permission prompt it resolves
     * internally) or reports an error.
     */
    suspend fun installFromUrl(url: String): ExtensionInstallResult {
        if (url.isBlank()) return ExtensionInstallResult.Failure("No URL provided")
        val trimmed = url.trim()
        _isBusy.value = true
        return try {
            ensurePromptDelegate()
            var result = installFromLocation(trimmed)
            if (result is ExtensionInstallResult.Failure && shouldRetryAsLocalFile(result.message, trimmed)) {
                val local = downloadXpi(trimmed)
                if (local != null) {
                    result = installFromLocation(local)
                }
            }
            if (result is ExtensionInstallResult.Success) refresh()
            result
        } catch (e: Throwable) {
            ExtensionInstallResult.Failure(e.message ?: "Install failed")
        } finally {
            _isBusy.value = false
        }
    }

    private suspend fun installFromLocation(location: String): ExtensionInstallResult {
        val controller = runtime().webExtensionController
        return suspendCancellableCoroutine { cont ->
            val pending = runCatching {
                controller.install(location, WebExtensionController.INSTALLATION_METHOD_MANAGER)
            }.getOrElse {
                controller.install(location)
            }
            pending.accept(
                { ext ->
                    if (cont.isActive && ext != null) {
                        attachActionDelegateIfNeeded(ext)
                        cont.resume(ExtensionInstallResult.Success(ext.toInstalledExtension()))
                    } else if (cont.isActive) {
                        cont.resume(ExtensionInstallResult.Failure("Install returned no extension"))
                    }
                },
                { err ->
                    val message = describeInstallError(err)
                    Log.w(TAG, "Extension install failed: $location", err)
                    if (cont.isActive) cont.resume(ExtensionInstallResult.Failure(message))
                },
            )
        }
    }

    private fun shouldRetryAsLocalFile(message: String, url: String): Boolean {
        if (!url.startsWith("http", ignoreCase = true)) return false
        val lower = message.lowercase()
        return "network" in lower || "download" in lower || "connection" in lower ||
            "couldn't download" in lower || "install failed" in lower
    }

    private suspend fun downloadXpi(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android 13; Mobile) Gecko/147.0 Firefox/147.0")
                .header("Accept", "application/x-xpinstall,*/*")
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val dir = java.io.File(context.cacheDir, "extensions").apply { mkdirs() }
                val file = java.io.File(dir, "install-${System.currentTimeMillis()}.xpi")
                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                if (file.length() < 64) {
                    file.delete()
                    return@withContext null
                }
                file.toURI().toString()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "XPI download failed: $url", e)
            null
        }
    }

    private fun ensurePromptDelegate() {
        val controller = runtime().webExtensionController
        if (controller.promptDelegate != null) return
        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                return GeckoResult.fromValue(
                    WebExtension.PermissionPromptResponse(true, false, false),
                )
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<out String>,
                newOrigins: Array<out String>,
                newDataCollectionPermissions: Array<out String>,
            ): GeckoResult<AllowOrDeny> {
                return GeckoResult.allow()
            }
        })
    }

    suspend fun setEnabled(extensionId: String, enabled: Boolean) {
        val ext = findExtensionHandle(extensionId) ?: return
        _isBusy.value = true
        try {
            val controller = runtime().webExtensionController
            suspendCancellableCoroutine<Unit> { cont ->
                val action = if (enabled) {
                    controller.enable(ext, WebExtensionController.EnableSource.USER)
                } else {
                    controller.disable(ext, WebExtensionController.EnableSource.USER)
                }
                action.accept(
                    { if (cont.isActive) cont.resume(Unit) },
                    { err ->
                        _lastError.value = err?.message ?: "Failed to update extension"
                        if (cont.isActive) cont.resume(Unit)
                    },
                )
            }
            // Stopgap only: bridges the moment before GeckoView's own
            // metadata reflects this call. refresh() below reconciles and
            // clears it once MetaData.enabled agrees.
            pendingEnabledOverrides[extensionId] = enabled
            if (!enabled) {
                _browserActions.value = _browserActions.value - extensionId
                if (_openPopup.value?.extensionId == extensionId) _openPopup.value = null
            }
            refresh()
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun uninstall(extensionId: String) {
        val ext = findExtensionHandle(extensionId) ?: return
        _isBusy.value = true
        try {
            val controller = runtime().webExtensionController
            suspendCancellableCoroutine<Unit> { cont ->
                controller.uninstall(ext).accept(
                    { if (cont.isActive) cont.resume(Unit) },
                    { err ->
                        _lastError.value = err?.message ?: "Failed to uninstall extension"
                        if (cont.isActive) cont.resume(Unit)
                    },
                )
            }
            pendingEnabledOverrides.remove(extensionId)
            actionDelegatesAttached.remove(extensionId)
            lastKnownHandles.remove(extensionId)
            resolvedIcons.remove(extensionId)
            _browserActions.value = _browserActions.value - extensionId
            popupSessions.remove(extensionId)?.let { session ->
                if (_openPopup.value?.extensionId == extensionId) _openPopup.value = null
                runCatching { session.close() }
            }
            refresh()
        } finally {
            _isBusy.value = false
        }
    }

    /** Tapping an extension's toolbar button/badge -- opens its popup if it has one. */
    fun onBrowserActionClicked(extensionId: String) {
        val action = _browserActions.value[extensionId] ?: return
        if (action.click == null) {
            Log.d(TAG, "Browser action for $extensionId has no popup/click handler")
        }
        // Invoking the action's own click() is what causes GeckoView to
        // call back into onTogglePopup/onOpenPopup above (for actions that
        // have a popup at all -- some browser_actions just run a background
        // script on click with no popup, in which case nothing further
        // happens here and that's correct). This crosses straight into
        // GeckoView/native code, so a misbehaving extension throwing here
        // must not be allowed to take down the whole app.
        runCatching { action.click?.invoke() }
            .onFailure { Log.w(TAG, "Browser action click failed for $extensionId", it) }
    }

    /** Dismisses the currently-open extension popup sheet, if any. */
    fun closePopup() {
        _openPopup.value = null
    }

    fun clearError() {
        _lastError.value = null
    }

    private fun openOrCreatePopupSession(extension: WebExtension): GeckoSession {
        popupSessions[extension.id]?.let { existing ->
            // GeckoView expects a session passed to onTogglePopup to be open;
            // if this session somehow got closed elsewhere, drop it and make
            // a fresh one rather than handing back a dead session.
            if (existing.isOpen) return existing
            popupSessions.remove(extension.id)
        }
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .build()
        val session = GeckoSession(settings)
        // GeckoView expects a session handed back from onTogglePopup/onOpenPopup
        // to behave like any other session it's about to load content into --
        // that means it needs the same delegate set a "real" tab session has
        // (content/navigation/prompt), or an unhandled callback (e.g. a JS
        // dialog, or a permission request the popup's own JS fires on load)
        // has nowhere to go and can bring the whole process down instead of
        // just failing this one popup. A dedicated popup has no chrome to show
        // any of these in, so every delegate here safely no-ops/denies rather
        // than leaving GeckoView with a null delegate for something it isn't
        // prepared to have silently ignored.
        session.contentDelegate = object : GeckoSession.ContentDelegate {}
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                s: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                val uri = request.uri.orEmpty()
                // Popup HTML and its own script/style resources load fine;
                // anything the popup tries to navigate to beyond that (e.g. a
                // link tapped inside it) is denied rather than hijacking this
                // small fixed-size panel into a full page load.
                return if (uri.startsWith("moz-extension://") || uri.startsWith("about:blank")) {
                    GeckoResult.fromValue(AllowOrDeny.ALLOW)
                } else {
                    GeckoResult.fromValue(AllowOrDeny.DENY)
                }
            }
        }
        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onAlertPrompt(
                s: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AlertPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
                GeckoResult.fromValue(prompt.dismiss())

            override fun onButtonPrompt(
                s: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ButtonPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
                GeckoResult.fromValue(prompt.dismiss())

            override fun onTextPrompt(
                s: GeckoSession,
                prompt: GeckoSession.PromptDelegate.TextPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
                GeckoResult.fromValue(prompt.dismiss())
        }
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {}
        if (!session.isOpen) {
            runCatching { session.open(runtime()) }.onFailure {
                Log.w(TAG, "Failed to open popup session for ${extension.id}", it)
            }
        }
        popupSessions[extension.id] = session
        return session
    }

    private fun attachActionDelegateIfNeeded(ext: WebExtension) {
        lastKnownHandles[ext.id] = ext
        if (!actionDelegatesAttached.add(ext.id)) return
        ext.setActionDelegate(object : WebExtension.ActionDelegate {
            override fun onBrowserAction(
                extension: WebExtension,
                session: org.mozilla.geckoview.GeckoSession?,
                action: WebExtension.Action,
            ) {
                updateBrowserAction(extension.id, action)
            }

            override fun onPageAction(
                extension: WebExtension,
                session: org.mozilla.geckoview.GeckoSession?,
                action: WebExtension.Action,
            ) {
                updateBrowserAction(extension.id, action)
            }

            override fun onTogglePopup(
                extension: WebExtension,
                action: WebExtension.Action,
            ): GeckoResult<GeckoSession>? {
                // GeckoView calls this whenever the toolbar button is
                // tapped and expects toggle semantics: return a session to
                // open/show the popup (GeckoView will loadUri the popup
                // HTML into it), or null to close it. If we always return a
                // session, tapping the button a second time while the popup
                // sheet is already open just reloads the same popup instead
                // of dismissing it -- so check our own open-popup state first.
                //
                // This is invoked directly by GeckoView (not through a
                // suspend/coroutine boundary we control), so any exception
                // here -- e.g. a malformed action from a misbehaving
                // extension -- would otherwise be an uncaught crash rather
                // than just a popup that fails to open.
                return try {
                    if (_openPopup.value?.extensionId == extension.id) {
                        _openPopup.value = null
                        return null
                    }
                    val popupSession = openOrCreatePopupSession(extension)
                    _openPopup.value = OpenPopup(extensionId = extension.id, session = popupSession)
                    GeckoResult.fromValue(popupSession)
                } catch (e: Throwable) {
                    Log.w(TAG, "onTogglePopup failed for ${extension.id}", e)
                    null
                }
            }

            override fun onOpenPopup(
                extension: WebExtension,
                action: WebExtension.Action,
            ): GeckoResult<GeckoSession>? {
                // Some extensions/actions route through onOpenPopup instead
                // of (or in addition to) onTogglePopup depending on how the
                // action was triggered; treat it the same way so the sheet
                // opens either path leads down.
                return try {
                    val popupSession = openOrCreatePopupSession(extension)
                    _openPopup.value = OpenPopup(extensionId = extension.id, session = popupSession)
                    GeckoResult.fromValue(popupSession)
                } catch (e: Throwable) {
                    Log.w(TAG, "onOpenPopup failed for ${extension.id}", e)
                    null
                }
            }
        })
    }

    private fun updateBrowserAction(extensionId: String, action: WebExtension.Action) {
        val title = action.title
        val badgeText = action.badgeText
        val icon: Image? = action.icon
        if (icon == null) {
            _browserActions.value = _browserActions.value + (
                extensionId to BrowserAction(
                    title = title,
                    badgeText = badgeText,
                    icon = null,
                    click = { action.click() },
                )
            )
            return
        }
        icon.getBitmap(ACTION_ICON_SIZE_PX).accept({ bitmap ->
            _browserActions.value = _browserActions.value + (
                extensionId to BrowserAction(
                    title = title,
                    badgeText = badgeText,
                    icon = bitmap,
                    click = { action.click() },
                )
            )
        }, { _ ->
            _browserActions.value = _browserActions.value + (
                extensionId to BrowserAction(
                    title = title,
                    badgeText = badgeText,
                    icon = null,
                    click = { action.click() },
                )
            )
        })
    }

    private suspend fun findExtensionHandle(extensionId: String): WebExtension? {
        val controller = runtime().webExtensionController
        return try {
            suspendCancellableCoroutine { cont ->
                controller.list().accept(
                    { exts ->
                        val match = exts.orEmpty().firstOrNull { it.id == extensionId }
                        if (cont.isActive) cont.resume(match)
                    },
                    { if (cont.isActive) cont.resume(null) },
                )
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun describeInstallError(error: Throwable?): String {
        // WebExtensionController reports install failures as a
        // WebExtension.InstallException carrying an error code -- fall back
        // to the raw message when the type isn't what's expected, so a
        // GeckoView version mismatch never crashes this path.
        val code = try {
            val codeField = error?.javaClass?.getMethod("code")
            codeField?.invoke(error)?.toString()
        } catch (_: Throwable) {
            null
        }
        return when (code) {
            "ERROR_NETWORK_FAILURE" -> "Couldn't download the extension. Check your connection and try again."
            "ERROR_INCORRECT_HASH", "ERROR_CORRUPT_FILE" -> "That file isn't a valid extension package."
            "ERROR_INCOMPATIBLE" -> "This extension isn't compatible with this browser."
            "ERROR_BLOCKLISTED" -> "This extension has been blocked for safety reasons."
            "ERROR_SIGNEDSTATE_REQUIRED" -> "This extension isn't signed and can't be installed."
            "ERROR_USER_CANCELED" -> "Installation was canceled."
            else -> error?.message ?: "Install failed"
        }
    }

    /**
     * disabledFlags is a bitmask on [WebExtension.MetaData]; the bit
     * constants live as static ints on MetaData itself
     * (DISABLED_FLAG_APP_DISABLED, DISABLED_FLAG_USER_DISABLED,
     * DISABLED_FLAG_BLOCKLISTED, DISABLED_FLAG_APP_SUPPORT as of this
     * GeckoView release). Read via reflection defensively so a future
     * GeckoView rename degrades to UNKNOWN rather than crashing this screen.
     */
    private fun decodeDisabledReason(meta: WebExtension.MetaData): ExtensionDisabledReason {
        if (meta.enabled) return ExtensionDisabledReason.NONE
        val flags = meta.disabledFlags
        if (flags == 0) return ExtensionDisabledReason.USER // enabled=false, no flags: our own disable() call
        fun bit(fieldName: String): Int? = try {
            WebExtension.MetaData::class.java.getField(fieldName).getInt(null)
        } catch (_: Throwable) {
            null
        }
        return when {
            bit("DISABLED_FLAG_BLOCKLISTED")?.let { flags and it != 0 } == true ->
                ExtensionDisabledReason.BLOCKLISTED
            bit("DISABLED_FLAG_APP_SUPPORT")?.let { flags and it != 0 } == true ->
                ExtensionDisabledReason.APP_SUPPORT
            bit("DISABLED_FLAG_APP_DISABLED")?.let { flags and it != 0 } == true ->
                ExtensionDisabledReason.APP_DISABLED
            bit("DISABLED_FLAG_USER_DISABLED")?.let { flags and it != 0 } == true ->
                ExtensionDisabledReason.USER
            else -> ExtensionDisabledReason.UNKNOWN
        }
    }

    private fun WebExtension.toInstalledExtension(): InstalledExtension {
        val meta = this.metaData
        val override = pendingEnabledOverrides[this.id]
        val enabled = override ?: meta?.enabled ?: true
        val disabledReason = when {
            enabled -> ExtensionDisabledReason.NONE
            meta != null -> decodeDisabledReason(meta)
            else -> ExtensionDisabledReason.UNKNOWN
        }
        if (resolvedIcons[this.id] == null) resolveIconAsync(this)
        return InstalledExtension(
            id = this.id,
            name = meta?.name ?: this.id,
            version = meta?.version ?: "",
            description = meta?.description.orEmpty(),
            iconUrl = null,
            iconBitmap = resolvedIcons[this.id],
            isEnabled = enabled,
            disabledReason = disabledReason,
            homepageUrl = meta?.homepageUrl,
            origins = meta?.requiredOrigins?.toList().orEmpty(),
            permissions = meta?.requiredPermissions?.toList().orEmpty(),
            optionalPermissions = meta?.optionalPermissions?.toList().orEmpty(),
            optionalOrigins = meta?.optionalOrigins?.toList().orEmpty(),
        )
    }

    // toInstalledExtension() above is a plain synchronous mapper called from
    // refresh(); icon resolution is async (Image.getBitmap returns
    // GeckoResult<Bitmap>), so we don't block list() on N bitmap round-trips.
    // The first render of a newly-seen extension may have no icon yet; once
    // resolveIconAsync's callback lands we patch the bitmap into the live
    // _installed list directly, so it appears without waiting for the next
    // refresh().
    private fun resolveIconAsync(ext: WebExtension) {
        val meta = ext.metaData ?: return
        val image: Image = meta.icon ?: return
        image.getBitmap(ICON_SIZE_PX).accept({ bitmap ->
            if (bitmap != null) {
                resolvedIcons[ext.id] = bitmap
                _installed.value = _installed.value.map {
                    if (it.id == ext.id) it.copy(iconBitmap = bitmap) else it
                }
            }
        }, { err ->
            Log.d(TAG, "No icon for extension ${ext.id}: ${err?.message}")
        })
    }

    companion object {
        private const val TAG = "ExtensionManager"
        private const val BRIDGE_EXTENSION_ID = "knot-bridge@wormhole.browser"

        // Requested pixel size passed to Image.getBitmap(size); GeckoView
        // picks the closest available source and scales it, so these just
        // need to be reasonable targets for where each bitmap is drawn (a
        // 24dp row icon vs a ~20dp toolbar action icon at common densities).
        private const val ICON_SIZE_PX = 96
        private const val ACTION_ICON_SIZE_PX = 64

        @Volatile private var instance: ExtensionManager? = null

        fun get(context: Context): ExtensionManager {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val created = ExtensionManager(context.applicationContext)
                instance = created
                return created
            }
        }
    }
}

/**
 * Live browser/page-action state for one extension's toolbar button, as
 * reported through [WebExtension.ActionDelegate]. `click` re-invokes the
 * action's own click handling (which is what triggers GeckoView to call
 * onTogglePopup/onOpenPopup), letting the UI wire a tap without needing to
 * hold a raw [WebExtension.Action] itself.
 */
data class BrowserAction(
    val title: String?,
    val badgeText: String?,
    val icon: Bitmap?,
    val click: (() -> Unit)?,
)

/** A currently-open extension popup: which extension, and the live session rendering it. */
data class OpenPopup(
    val extensionId: String,
    val session: GeckoSession,
)
