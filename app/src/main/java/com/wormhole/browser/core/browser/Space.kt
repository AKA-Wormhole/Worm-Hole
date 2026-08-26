package com.wormhole.browser.core.browser

import androidx.compose.ui.graphics.Color
import com.wormhole.browser.ui.theme.WormHoleGold
import com.wormhole.browser.ui.theme.WormHoleMint
import com.wormhole.browser.ui.theme.WormHoleSky
import com.wormhole.browser.ui.theme.WormHoleViolet

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
    VIOLET(WormHoleViolet),
    MINT(WormHoleMint),
    GOLD(WormHoleGold),
    SKY(WormHoleSky),
}
