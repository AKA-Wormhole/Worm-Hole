package holocore.browser.app.core.settings

enum class HomeBackground(val id: String, val displayName: String) {
    DEFAULT("default", "Default art"),
    PHOTON("photon", "Photon"),
    VIOLET("violet", "Private violet"),
    NAVY("navy", "Navy"),
    PAPER("paper", "Paper"),
    ;

    companion object {
        fun fromId(id: String?): HomeBackground =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
