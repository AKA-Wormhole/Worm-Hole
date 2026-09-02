package holocore.browser.app.core.settings

enum class ThemeMode(val id: String, val displayName: String) {
    SYSTEM(id = "system", displayName = "System default"),
    LIGHT(id = "light", displayName = "Light"),
    DARK(id = "dark", displayName = "Dark");

    companion object {
        val DEFAULT = DARK

        fun fromId(id: String?): ThemeMode =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
