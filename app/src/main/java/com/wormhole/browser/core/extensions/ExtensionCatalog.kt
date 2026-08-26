package com.wormhole.browser.core.extensions

/**
 * Curated Firefox/Gecko-compatible extensions from addons.mozilla.org.
 * [geckoIds] are the real WebExtension ids GeckoView reports after install
 * (AMO slug ≠ runtime id). Matching uses these so Browse hides installed ones.
 */
object ExtensionCatalog {
    val entries: List<CatalogExtension> = listOf(
        CatalogExtension(
            id = "ublock-origin",
            name = "uBlock Origin",
            summary = "Efficient, general-purpose ad and tracker content blocker.",
            author = "Raymond Hill",
            xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi",
            geckoIds = listOf("uBlock0@raymondhill.net"),
        ),
        CatalogExtension(
            id = "bitwarden",
            name = "Bitwarden Password Manager",
            summary = "Free password manager and vault, synced across your devices.",
            author = "Bitwarden Inc.",
            xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/bitwarden-password-manager/latest.xpi",
            geckoIds = listOf(
                "{446900e4-71c3-b5b1-b8b1-d1c4f0e0e0e0}",
                "bitwarden@bitwarden.com",
            ),
        ),
        CatalogExtension(
            id = "dark-reader",
            name = "Dark Reader",
            summary = "Applies a dark theme to every website you visit.",
            author = "Dark Reader",
            xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/darkreader/latest.xpi",
            geckoIds = listOf("addon@darkreader.org"),
        ),
        CatalogExtension(
            id = "privacy-badger",
            name = "Privacy Badger",
            summary = "Automatically learns to block invisible trackers.",
            author = "Electronic Frontier Foundation",
            xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/privacy-badger17/latest.xpi",
            geckoIds = listOf("jid1-MnnxcxisBPnSXQ@jetpack"),
        ),
        CatalogExtension(
            id = "return-youtube-dislike",
            name = "Return YouTube Dislike",
            summary = "Restores the dislike count on YouTube videos.",
            author = "Dmitrii Selivanov",
            xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/return-youtube-dislikes/latest.xpi",
            geckoIds = listOf("{85860b32-02a8-431a-b2b1-40fbd675fe63}"),
        ),
        CatalogExtension(
            id = "read-aloud",
            name = "Read Aloud: A Text to Speech Voice Reader",
            summary = "Reads the text of any webpage aloud.",
            author = "Read Aloud",
            xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/read-aloud/latest.xpi",
            geckoIds = listOf("{ttsbrowser@readaloud.app}", "readaloud@kenan.fatih"),
        ),
    )

    /** Match catalog entry to an installed extension (Gecko id ≠ catalog id). */
    fun CatalogExtension.matchesInstalled(installed: InstalledExtension): Boolean {
        if (id.equals(installed.id, ignoreCase = true)) return true
        if (geckoIds.any { it.equals(installed.id, ignoreCase = true) }) return true
        val catalogName = name.lowercase()
        val installedName = installed.name.lowercase()
        if (installedName == catalogName) return true
        val shortCatalog = catalogName.substringBefore(':').trim()
        if (shortCatalog.length >= 4 && (
                installedName.contains(shortCatalog) || shortCatalog.contains(installedName)
            )
        ) {
            return true
        }
        val slug = id.replace("-", "").lowercase()
        val installedId = installed.id.lowercase().replace("-", "").replace("{", "").replace("}", "")
        if (slug.length >= 4 && installedId.contains(slug.take(6))) return true
        return false
    }

    fun isInstalled(entry: CatalogExtension, installed: List<InstalledExtension>): Boolean =
        installed.any { entry.matchesInstalled(it) }
}
