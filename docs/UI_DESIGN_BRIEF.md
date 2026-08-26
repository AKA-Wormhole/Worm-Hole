# WormHole — UI Design Brief

This is the design target for WormHole's interface: an Arc-inspired Android
browser shell. Written as a standalone spec so implementation (Stage 3
onward) has a fixed reference instead of ad hoc screen-by-screen design.

---

## 1. Design philosophy

Arc's core UI idea: **the browser chrome should feel like a living,
tactile object, not a static toolbar.** Every element responds to touch
with physical, springy motion. Tabs live in a sidebar, not a top strip,
because a phone screen is tall and a sidebar can collapse away when not
needed. Navigation and search are unified into one command surface
instead of split across an address bar and a separate search bar.

Three things make an interface "feel like Arc" rather than "look like
Arc": the **motion language** (springy overshoot, nothing linear or
instant), the **warm neutral palette** with one confident accent color,
and **generous rounded geometry** (nothing sharp-cornered).

---

## 2. Layout structure

### 2.1 Sidebar (primary navigation surface)
- Slides in from the left edge; collapsible via edge-swipe or a handle.
- Contains, top to bottom:
  - Space switcher (horizontal row of small circular avatars/icons, one
    per Space, plus a "+" to create a new Space)
  - Pinned tabs (compact, icon-only rows)
  - Active Space's tab list (favicon + title, pill-shaped rows,
    reorderable by drag)
  - Bottom: settings gear, new-tab button
- Each Space carries its own accent color (from the palette defined in
  Stage 1's `Color.kt`) and optionally its own gradient wallpaper shown
  behind the tab content when on a "new tab" state.
- Collapsing the sidebar animates it off-screen with the `bouncy` spring
  spec; the content area expands to fill the freed width.

### 2.2 Command bar (search + navigate, unified)
- A pill-shaped bar, NOT a traditional full-width address bar.
- Summoned by: tapping the current URL/title display, a keyboard
  shortcut equivalent gesture (long-press on the tab area), or pulling
  down on a new tab.
- Appears centered, slightly overshooting past its final size before
  settling (`bouncy` spring) — this is the single most "Arc" motion
  moment in the whole app and should not be skipped or simplified.
- While active: background dims/blurs behind it, keyboard opens
  automatically, recent history + suggestions appear below as a list
  that fades/slides in.
- Typing a non-URL triggers WormHole's search flow (Stage 4+); typing a URL
  navigates directly.
- Dismiss by tapping outside, pressing back, or submitting — each with
  its own settle animation, not an instant cut.

### 2.3 Content area
- Fills the remaining space right of the sidebar (or full width when
  sidebar is collapsed).
- Holds the active tab's WebView (Stage 2's `WormHoleWebViewHost`) or, on a
  blank tab, the **New Tab surface**:
  - Space's gradient wallpaper, softly blurred, animated with slow
    ambient drift (subtle, not distracting)
  - Centered command bar entry point
  - Below it: shortcut tiles (favorites/frequently visited), populated
    later once history/bookmarks exist
- A thin progress indicator (not a full-width Material bar — a slim
  tinted line matching the Space's accent) animates across the very top
  edge during page load.

### 2.4 Tab switcher (overview mode)
- Triggered from the sidebar's tab list via a "grid" toggle, or by a
  pinch gesture on the content area.
- Tabs render as cards in a scrollable grid, each showing a live
  thumbnail-ish preview (or favicon + title as a placeholder until
  thumbnail capture exists).
- Closing a tab: card springs/shrinks away with `snappy`, remaining
  cards reflow into the gap with `bouncy`.
- Opening a tab from the switcher: card scales up and the switcher
  fades away, handing off to the content area.

### 2.5 Find-in-page bar
- Slides up from the bottom as a compact pill (not a full-width bar),
  wired to Stage 2's `FindInPageController`.
- Shows "`n` of `total`" match count, prev/next chevrons, close button.
- Appears/disappears with `bouncy`; match count updates should feel
  instant (no animation on the number itself, only on bar appearance).

### 2.6 Settings surface
- Replaces the Stage 2.1 placeholder screen with a proper slide-over
  panel (not a separate Activity) reachable from the sidebar's gear
  icon.
- Sectioned list: General (search engine picker — carries over exactly
  from Stage 2.1), Spaces (manage/reorder/recolor), AI (Gemini API key
  field, Stage 5), About.
- Uses the same rounded-row, tap-to-select pattern as the Stage 2.1
  search engine picker — that component's visual language is the
  template for every settings row going forward.

---

## 3. Motion specification

All animations pull from the three shared specs already defined in
`ui/theme/Motion.kt` — no new ad hoc animation curves should be
introduced in Stage 3+ without a reason:

| Spec | Use for |
|---|---|
| `bouncy` (medium bounce) | Anything appearing/opening: command bar summon, sidebar open, new tab card, settings panel slide-in, tab switcher card open |
| `snappy` (low bounce, high stiffness) | Frequent micro-interactions: button press, toggle, tab close, tab reorder drag-release |
| `settled` (no bounce) | Precision elements: text field focus ring, progress indicator, find-in-page match count |

Press feedback: every tappable element scales to `WormHoleMotion.PRESS_SCALE`
(0.94f) on press-down, using `snappy` to release — this is the "squish"
half of squish-and-bounce and should be applied consistently via a
shared `Modifier` extension rather than reimplemented per component.

Nothing in the interface should use `tween`/linear easing for anything
the user directly triggers. Linear easing is acceptable only for
continuous/ambient motion (e.g. the new-tab wallpaper drift).

---

## 4. Color & theming rules

- Pulls from `ui/theme/Color.kt` (already built): warm cream/charcoal
  base, coral default accent, violet/mint/gold/sky as alternate Space
  accents.
- Each Space's accent tints: its sidebar tab-list selection highlight,
  its new-tab wallpaper gradient, its progress-bar line color.
- Never introduce a new color outside this palette without adding it to
  `Color.kt` first — no inline hex values in composables.
- Dark mode is a first-class target, not an afterthought: every new
  component must be checked against both `WormHoleDarkScheme` and
  `WormHoleLightScheme`.

---

## 5. Typography rules

- Pulls from `ui/theme/Type.kt`. Headline/title weights stay
  SemiBold/Medium — never regular weight for anything a user scans
  quickly (tab titles, Space names, settings row labels).
- Body copy (settings descriptions, help text) uses `bodyMedium`/
  `bodySmall` at the muted ink color, never full-contrast ink — this is
  what gives Arc's UI its soft, non-shouty hierarchy.

---

## 6. Component inventory (what Stage 3 needs to build)

In rough build order:
1. `WormHoleSidebar` — collapsible container, Space switcher row, tab list
2. `SpaceSwitcher` — horizontal Space avatar row with add-Space action
3. `TabListRow` — replaces Stage 2's plain `TabChip`; drag-reorderable
4. `CommandBar` — the summonable pill; owns its own open/closed state
   machine and animation
5. `NewTabSurface` — gradient wallpaper + centered command bar entry
6. `TabSwitcherGrid` — overview mode
7. `FindInPageBar` — bottom pill wired to `FindInPageController`
8. `SettingsPanel` — slide-over replacing the Stage 2.1 full-screen
   `SettingsScreen`; General section reuses that screen's search-engine
   picker composable directly

Each should be a self-contained composable taking state + callbacks
(no ViewModel references inside the composable itself), consistent with
how `BrowserScreen`/`AddressBar` are already structured.

---

## 7. Explicit non-goals for this pass

To keep Stage 3 scoped and shippable:
- No tab thumbnail image capture yet (placeholder favicon+title cards
  are fine in the switcher for now)
- No drag-and-drop between Spaces (reordering within a Space's list is
  in scope; cross-Space drag is not)
- No custom wallpaper upload — gradient presets only
- No gesture customization/settings for the summon gesture — one fixed
  gesture for this stage

---

## 8. How to use this brief

Reference this document by section when discussing or requesting
changes to Stage 3+ UI work (e.g. "adjust the CommandBar per section
2.2" or "SpaceSwitcher needs the add-Space affordance from section 6.2").
Treat it as the spec Stage 3 is built against — if an implementation
detail conflicts with this brief, the brief wins unless we explicitly
decide to amend it (and if we do, amend this file too, so it stays the
one source of truth for what "Arc-styled" means in WormHole).


## 9. Comet-inspired mobile interaction layer

WormHole should follow the modern AI-browser interaction model without copying proprietary artwork, exact assets, or pixel-identical layouts. The target is the same product-level behavior: browsing remains primary, while AI is always one tap away and can work alongside the current page.

- Keep the floating bottom browser chrome as the main navigation/search surface.
- Add a dedicated Assistant action beside the search surface so AI is not hidden behind a generic overflow menu.
- When AI is actively processing the current page, show a restrained blue activity edge around the browser content. This indicator must only appear while an actual AI request is running.
- The Assistant surface should eventually expose its live action timeline, current page context, sources, stop/cancel, and confirmations for consequential actions.
- The command surface should remain the primary entry point for both URLs/searches and natural-language browser tasks.
- Use neutral graphite/white surfaces and restrained blue accents; avoid decorative pastel or overly translucent chrome.

## 10. Liquid glass correctness

The glass material must never blur its own children. `Modifier.blur()` must not be applied to a modifier chain that contains text, icons, buttons, or other foreground content. WormHole's current glass implementation therefore uses translucent layered fills, subtle gradients, a rim, and a background-only highlight.

If true backdrop blur is added later, it must be implemented as a separate behind-content rendering layer so the foreground remains pixel-sharp.


## 2026-08-10 browser polish pass

- The home surface uses a compact AI-browser layout with a prominent Ask WormHole/search entry and actionable suggestion cards.
- The tab switcher uses a two-column visual grid, Tabs/Incognito segmentation, history access, and a bottom Select/+ /Done control pattern.
- Settings is available from the browser three-dot menu.
- Liquid Glass is content-safe: no blur is applied to a composable subtree containing text or icons.
- Dark mode uses light/white foreground chrome; light mode uses dark/black foreground chrome.
- DownloadManager querying is defensive and moved off the UI thread to avoid crashes during active downloads.
