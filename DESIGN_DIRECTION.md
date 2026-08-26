# WormHole visual direction

WormHole uses its **own core theme**, not Material You / wallpaper dynamic color.

- Fixed graphite + black/white palette (`ui/theme/Color.kt`, `Theme.kt`)
- Dark is the only shipped experience
- Accent is white on dark chrome (black on light)
- Pill-shaped controls (`WormHoleShapes` / `WormHoleSurface.PillShape`)
- Shared surfaces: `WormHoleSurface`, `WormHoleTile`, `WormHoleRow` — every sheet,
  menu, settings row, and search bar builds on these so the UI reads as one language
- Compact density closer to Chrome than a lifestyle app
- No candy gradients, coral, or decorative blobs for chrome

Do not re-enable Material You dynamic color. The product identity is the WormHole
graphite surface system, not the device wallpaper.
