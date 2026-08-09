package com.knot.browser.core.browser

import androidx.compose.ui.graphics.Color
import com.knot.browser.ui.theme.KnotCoral
import com.knot.browser.ui.theme.KnotGold
import com.knot.browser.ui.theme.KnotMint
import com.knot.browser.ui.theme.KnotSky
import com.knot.browser.ui.theme.KnotViolet

/**
 * A Space groups tabs, mirroring Arc's Spaces concept -- switching
 * Spaces swaps the entire visible tab list and re-tints the sidebar/
 * new-tab surface to that Space's accent (UI_DESIGN_BRIEF.md section 2.1).
 */
data class Space(
    val id: String,
    val name: String,
    val accent: SpaceAccent,
    val order: Int,
) {
    companion object {
        const val DEFAULT_SPACE_ID = "default"

        /** Every fresh install starts with one Space so the sidebar and
         * tab list always have somewhere to attach to -- there's no
         * "zero Spaces" state to design around. */
        fun defaultSpaces(): List<Space> = listOf(
            Space(id = DEFAULT_SPACE_ID, name = "Home", accent = SpaceAccent.CORAL, order = 0),
        )
    }
}

/**
 * The fixed palette of Space accent colors, sourced from the tokens in
 * ui/theme/Color.kt (UI_DESIGN_BRIEF.md section 4 -- no inline hex
 * values outside that file). A Space picks one of these rather than an
 * arbitrary color, keeping every Space visually consistent with the
 * rest of the app in both light and dark mode.
 */
enum class SpaceAccent(val color: Color) {
    CORAL(KnotCoral),
    VIOLET(KnotViolet),
    MINT(KnotMint),
    GOLD(KnotGold),
    SKY(KnotSky),
}
