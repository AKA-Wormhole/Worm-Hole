package holocore.browser.app.core.browser

import androidx.compose.ui.graphics.Color
import holocore.browser.app.ui.theme.HoloCoreGold
import holocore.browser.app.ui.theme.HoloCoreMint
import holocore.browser.app.ui.theme.HoloCoreSky
import holocore.browser.app.ui.theme.HoloCoreViolet

data class Space(
    val id: String,
    val name: String,
    val accent: SpaceAccent,
    val order: Int,
) {
    companion object {
        const val DEFAULT_SPACE_ID = "default"

        fun defaultSpaces(): List<Space> = listOf(
            Space(id = DEFAULT_SPACE_ID, name = "Home", accent = SpaceAccent.CORAL, order = 0),
        )
    }
}

enum class SpaceAccent(val color: Color) {
    CORAL(Color.White),
    VIOLET(HoloCoreViolet),
    MINT(HoloCoreMint),
    GOLD(HoloCoreGold),
    SKY(HoloCoreSky),
}
