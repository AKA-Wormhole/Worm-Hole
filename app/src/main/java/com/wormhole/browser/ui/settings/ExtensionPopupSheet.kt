package com.wormhole.browser.ui.settings

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.wormhole.browser.core.extensions.OpenPopup
import com.wormhole.browser.ui.theme.WormHoleSurface
import com.wormhole.browser.ui.theme.bouncyClickable
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Renders a WebExtension's popup (the small HTML page a browser_action opens,
 * e.g. an ad-blocker's per-site toggle panel) in a bottom sheet, backed by the
 * real [GeckoSession] GeckoView created for it in
 * [com.wormhole.browser.core.extensions.ExtensionManager.onTogglePopup].
 *
 * This is a separate, deliberately minimal host from
 * [com.wormhole.browser.ui.browser.WormHoleGeckoViewHost]: a popup has no
 * navigation chrome, no download handling, no scroll-driven toolbar --
 * it's a small fixed-size panel that opens and closes with the sheet.
 */
@Composable
fun ExtensionPopupSheet(
    popup: OpenPopup,
    extensionName: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .bouncyClickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WormHoleSurface.Fill, WormHoleSurface.SheetShape)
                .bouncyClickable(onClick = {}), // absorb taps so they don't fall through to dismiss
        ) {
            Text(
                extensionName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            PopupGeckoView(
                session = popup.session,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(POPUP_HEIGHT),
            )
        }
    }
}

private val POPUP_HEIGHT = 420.dp

/**
 * Thin [GeckoView] host for a popup session that already exists and is
 * already open (created and owned by ExtensionManager) -- unlike
 * WormHoleGeckoViewHost, this never creates, loads, or closes the session
 * itself; it only attaches/detaches the view. ExtensionManager owns the
 * session's lifecycle across popup opens, so the same JS state can survive
 * the sheet being dismissed and reopened.
 */
@Composable
private fun PopupGeckoView(session: GeckoSession, modifier: Modifier = Modifier) {
    DisposableEffect(session) {
        runCatching { session.setActive(true) }
        onDispose {
            runCatching { session.setActive(false) }
        }
    }

    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                val geckoView = GeckoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                    setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
                    // The popup's session can, in principle, already be
                    // closed by the time Compose gets around to inflating
                    // this view (e.g. the extension was disabled/uninstalled
                    // in the moment between the popup opening and this
                    // AndroidView composing) -- setSession on a closed
                    // session throws, which would otherwise crash the app
                    // over what should just be a popup that fails to render.
                    if (session.isOpen) {
                        runCatching { setSession(session) }
                    }
                }
                addView(geckoView)
            }
        },
        update = { container ->
            val geckoView = container.getChildAt(0) as? GeckoView ?: return@AndroidView
            if (geckoView.session !== session && session.isOpen) {
                runCatching { geckoView.releaseSession() }
                runCatching { geckoView.setSession(session) }
            }
        },
        onRelease = { container ->
            (container.getChildAt(0) as? GeckoView)?.let { geckoView ->
                runCatching { geckoView.releaseSession() }
            }
        },
        modifier = modifier,
    )
}
