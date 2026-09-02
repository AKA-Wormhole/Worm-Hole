package holocore.browser.app.core.gecko

import org.mozilla.geckoview.WebRequestError

data class PageLoadFailure(
    val url: String,
    val title: String,
    val hint: String,
)

fun pageLoadFailureOf(error: WebRequestError, uri: String?): PageLoadFailure {
    val url = uri.orEmpty()
    val networkish = error.category == WebRequestError.ERROR_CATEGORY_NETWORK ||
        error.code == WebRequestError.ERROR_OFFLINE ||
        error.code == WebRequestError.ERROR_NET_TIMEOUT ||
        error.code == WebRequestError.ERROR_NET_RESET ||
        error.code == WebRequestError.ERROR_NET_INTERRUPT ||
        error.code == WebRequestError.ERROR_CONNECTION_REFUSED ||
        error.code == WebRequestError.ERROR_UNKNOWN_HOST
    return when {
        error.code == WebRequestError.ERROR_UNKNOWN_HOST -> PageLoadFailure(
            url = url,
            title = "Connection failed",
            hint = "Check your internet connection or DNS, then try again.",
        )
        error.code == WebRequestError.ERROR_OFFLINE -> PageLoadFailure(
            url = url,
            title = "Connection failed",
            hint = "Check your internet connection, then try again.",
        )
        error.code == WebRequestError.ERROR_NET_TIMEOUT -> PageLoadFailure(
            url = url,
            title = "Connection failed",
            hint = "The page took too long. Check your internet connection, then try again.",
        )
        error.code == WebRequestError.ERROR_CONNECTION_REFUSED -> PageLoadFailure(
            url = url,
            title = "Connection failed",
            hint = "The site refused the connection. Check the URL and your internet connection.",
        )
        networkish -> PageLoadFailure(
            url = url,
            title = "Connection failed",
            hint = "Check your internet connection or DNS, then try again.",
        )
        error.category == WebRequestError.ERROR_CATEGORY_SECURITY -> PageLoadFailure(
            url = url,
            title = "Connection failed",
            hint = "This site’s security certificate could not be trusted.",
        )
        else -> PageLoadFailure(
            url = url,
            title = "Connection failed",
            hint = "Check your internet connection or DNS, and that the page URL is correct.",
        )
    }
}
