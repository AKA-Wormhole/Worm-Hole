package holocore.browser.app.core.permissions

import java.util.concurrent.atomic.AtomicReference

/**
 * Lets GeckoView's PermissionDelegate ask the Activity for runtime
 * Android permissions instead of granting them blindly.
 */
object AndroidPermissionRequests {
    fun interface Handler {
        fun request(permissions: Array<out String>, onResult: (granted: Boolean) -> Unit)
    }

    private val handler = AtomicReference<Handler?>(null)

    fun bind(value: Handler?) {
        handler.set(value)
    }

    fun request(permissions: Array<out String>, onResult: (Boolean) -> Unit) {
        val current = handler.get()
        if (current == null || permissions.isEmpty()) {
            onResult(permissions.isEmpty())
            return
        }
        current.request(permissions, onResult)
    }
}
