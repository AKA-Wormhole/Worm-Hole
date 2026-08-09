package com.knot.browser.core.settings

/**
 * User-facing appearance choice, persisted via [SettingsRepository] and
 * resolved to an actual light/dark [androidx.compose.material3.ColorScheme]
 * in KnotTheme. Kept separate from the boolean KnotTheme already accepts
 * so "follow system" can be represented and round-tripped through
 * DataStore without losing information.
 */
enum class ThemeMode(val id: String, val displayName: String) {
    SYSTEM(id = "system", displayName = "System default"),
    LIGHT(id = "light", displayName = "Light"),
    DARK(id = "dark", displayName = "Dark");

    companion object {
        val DEFAULT = SYSTEM

        fun fromId(id: String?): ThemeMode =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
