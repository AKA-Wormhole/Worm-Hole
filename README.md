# Knot

An Android browser with an Arc-inspired interface: sidebar Spaces, a
pill command bar, spring-physics motion throughout, and a built-in
search experience (your own search engine backend) with an AI-generated
summary at the top of results, gated behind a user-supplied Gemini API
key.

Package: `com.knot.browser` · Min SDK 26 · Target/Compile SDK 35 ·
Kotlin 2.0.21 · Compose (Material3) · AGP 8.6.1

## Scope note (read this first)

Knot is a **WebView-based Android browser app** — a real, installable
APK that renders web pages using Android's built-in WebView engine
(the same approach used by most non-Chromium-fork Android browsers).
It is not a from-scratch rendering engine, and it won't have Arc's
absolute deepest features (Boosts/CSS injection on arbitrary sites,
Easels) in early stages — those are realistic *later* additions once
the core is solid, not part of the initial build.

## Roadmap

- [x] **Stage 1 — Project skeleton.** Gradle project, version catalog,
      Arc-derived design tokens (color, type, motion/spring specs),
      adaptive launcher icon, CI workflow that bootstraps the Gradle
      wrapper jar and builds a debug APK. A placeholder screen proves
      out the palette and a bounce interaction before real UI is built.
- [x] **Stage 2 — WebView core.** Tab data model, `WebViewPool` with
      LRU eviction (bounded live WebView count), correctly-lifecycled
      WebView↔Compose bridge (`KnotWebViewHost`), navigation client/
      chrome client wired to a `BrowserViewModel` StateFlow, address
      bar with back/forward/reload, tab strip, download handling via
      `DownloadManager`, external-scheme intent launching (mailto:,
      tel:, intent:, etc.), find-in-page controller (wired into UI in
      Stage 3), and back-button delegation (WebView history before app
      exit). UI here is deliberately plain Material chrome -- it exists
      to prove the WebView core is solid before Arc-style chrome goes
      on top of it.
- [x] **Stage 2.1 — Search engine setting.** `SearchEngine` enum
      (Google, Knot Search), persisted via DataStore through
      `SettingsRepository`, defaulting to **Google**. Typed queries
      that aren't URLs resolve to `google.com/search?q=...` and load
      directly in the WebView -- this is "real Google search" in the
      sense of loading Google's actual results page, not an API
      integration (no API key needed, no query limits). A minimal
      settings screen (gear icon in the address bar) lets the user
      switch to Knot Search. This screen is a placeholder -- Stage 3's
      sidebar/command bar gets a proper settings surface, and this
      picker logic moves there as-is.
- [x] **Stage 3 — Arc UI shell.** Built against `docs/UI_DESIGN_BRIEF.md`
      (read that file for full rationale). Ships: `KnotSidebar`
      (collapsible, spring-animated width), `SpaceSwitcher` (accent-
      colored avatar row), `TabListRow` (replaces Stage 2's `TabChip`),
      `CommandBar` (the summonable search/navigate pill with the spring-
      overshoot open animation), `NewTabSurface` (Space-tinted gradient
      wallpaper), `FindInPageBar` (wired to Stage 2's
      `FindInPageController`), and a shared `Modifier.bouncyClickable()`
      extension so every tappable element presses/releases identically.
      `Space`/`SpaceAccent` models added with a default "Home" Space
      seeded on first launch; `BrowserViewModel` gained space-switching,
      space-creation, and tab-reordering support (`visibleTabs` derives
      the sidebar's per-Space list). Settings screen unchanged from
      Stage 2.1, now reachable from the sidebar's gear icon instead of
      the old address bar.
- [ ] **Stage 4 — Search backend integration.** Wire the new-tab/command
      bar search to the Python search engine (crawler → BM25 →
      PageRank) from earlier, exposed over HTTP, with a results screen
      inside the app (web/video/image sections). Knot Search becomes a
      real third option alongside Google rather than a `knot://`
      placeholder scheme.
- [ ] **Stage 5 — AI summary.** Settings screen for a user-supplied
      Gemini API key (Google AI Studio), stored via DataStore. Summary
      panel appears above search results ONLY once a key is present;
      calls the Gemini API with top result snippets as context.
- [ ] **Stage 6 — Video/image sections + richer crawl data.** Extend
      the crawler to parse `<video>` tags and schema.org VideoObject/
      ImageObject metadata; dedicated tabs in the results screen.

## Why CI, not local Gradle

This project is built from Termux with no local Android SDK, so every
stage is verified via GitHub Actions rather than a local build. Push to
`main` (or open a PR) and check the Actions tab for the debug APK
artifact and lint report.

## Repo layout

```
app/
  src/main/java/com/knot/browser/
    core/browser/    -- Tab model, BrowserViewModel (StateFlow source of
                        truth), DownloadHandler, ExternalIntentLauncher
    core/webview/     -- WebViewPool (lifecycle owner), WebViewFactory
                        (settings/security config), KnotWebViewClient/
                        KnotWebChromeClient, FindInPageController
    ui/browser/       -- KnotWebViewHost (Compose↔WebView bridge),
                        BrowserScreen (Stage 2 plain chrome)
    ui/theme/         -- Color.kt, Type.kt, Motion.kt, Theme.kt (design tokens)
    MainActivity.kt
    KnotApplication.kt
  src/main/res/       -- manifest resources, adaptive icon, network security config
gradle/
  libs.versions.toml  -- single source of truth for dependency versions
.github/workflows/build.yml
```

## Stage 2 architecture notes

- **WebViewPool is Activity-scoped, not ViewModel-scoped.** WebViews are
  real Android Views tied to a Context; keeping them alive across a
  config change (the way ViewModel state normally survives rotation) is
  a classic source of "Activity leaked" crashes. `MainActivity` owns the
  pool directly and calls `destroyAll()` in `onDestroy()`.
- **Only the active tab is composed into the view hierarchy.** Inactive
  tabs' WebViews stay alive inside the pool (up to `maxLiveWebViews`,
  default 8) but detached from any parent -- this is what makes tab
  switching preserve scroll position and back/forward history instantly,
  without keeping every tab rendered on screen.
- **Back button delegates to the active WebView's history first**, only
  falling through to normal Activity back-press (and eventually app
  exit) once the WebView has nothing left to go back to.
- **Downloads go through Android's DownloadManager**, not a hand-rolled
  download stream -- it survives the app being killed and gives the
  user a real system notification with progress.

## Search engine setting (Stage 2.1)

- `SearchEngine` (`core/settings/SearchEngine.kt`) is a small enum where
  each entry knows how to turn a typed query into a loadable URL.
  `GOOGLE` builds `https://www.google.com/search?q=...` and is the
  default; `KNOT` builds a `knot://search?q=...` placeholder that
  Stage 4 will point at the real search backend.
- `SettingsRepository` persists the choice via DataStore
  (`core/settings/SettingsRepository.kt`), so it survives app restarts.
- `BrowserViewModel.resolveInput()` consults the selected engine
  whenever the user types something that isn't a URL.
- **Known minor edge case:** on cold start there's a brief window where
  `searchEngine.value` reads as the Google default before DataStore's
  persisted value has loaded asynchronously. If a user had switched to
  Knot Search and searched within roughly a frame of app launch, that
  one query could use Google instead. Cosmetic, not a correctness bug;
  not worth a loading-state gate at this stage.
- This is deliberately NOT the Google Custom Search API -- no API key,
  no query quota, just loading Google's real results page in the
  WebView, same as any browser's default search engine setting.

## Stage 3 notes

- **Design spec:** `docs/UI_DESIGN_BRIEF.md` is the reference for every
  Stage 3+ UI decision -- layout structure, motion rules, color/type
  rules, and the explicit non-goals for this pass (no tab thumbnails
  yet, no cross-Space drag, no custom wallpaper upload). Treat it as
  living documentation: if an implementation choice conflicts with it,
  either the code or the brief should change, not silently diverge.
- **`Modifier.bouncyClickable()`** (`ui/theme/BouncyClickable.kt`) is
  the shared press interaction every tappable element should use.
  Several Stage 3 components were drafted with their own duplicated
  press-scale logic before this was extracted -- if you add a new
  tappable element, reach for this first rather than re-deriving it.
- **`FindInPageController` is per-tab, not a singleton.** It wraps a
  specific `WebView` instance, so `BrowserScreen` recreates it via
  `remember(activeTab?.id)` whenever the active tab changes. On a
  brand-new tab's very first frame the underlying WebView may not
  exist in the pool yet, so this can transiently be null and self-
  correct next recomposition -- harmless since find-in-page is never
  needed before a page has loaded.
- **Space creation is a placeholder flow**: tapping "+" in the Space
  switcher cycles through the fixed accent palette and auto-names the
  Space ("Space 2", "Space 3", ...) rather than prompting for a name.
  A proper creation dialog is a natural follow-up, not required to
  prove the underlying Spaces model (switching, tab filtering,
  per-Space tinting) works end to end.
- **Tab thumbnails are not implemented.** The brief's tab switcher
  (section 2.4) is listed as a Stage 3 component but was deferred --
  `TabListRow`'s favicon slot is a static placeholder icon, and no
  `TabSwitcherGrid` exists yet. This is the largest gap between the
  brief and this stage's actual delivery; worth prioritizing in a
  follow-up pass before Stage 4 if grid-view tab overview matters to
  the workflow.

## Design tokens (Stage 1)

- **Color** (`ui/theme/Color.kt`): warm cream light mode / soft charcoal
  dark mode (Arc's paper-like neutrals, not stock Material grey), coral
  signature accent (`#FF6952`) with violet/mint/gold/sky as alternate
  Space accents.
- **Type** (`ui/theme/Type.kt`): tight tracking, deliberate weight steps
  — currently system sans as a placeholder; swapping in a bundled
  variable font is a tracked follow-up, not a Stage 1 blocker.
- **Motion** (`ui/theme/Motion.kt`): three shared spring specs
  (`bouncy`, `snappy`, `settled`) so every animation in the app —
  current and future — pulls from one consistent physical feel instead
  of ad hoc durations scattered through the codebase.

## Running the build

Push this repo to GitHub and let CI build it — see `.github/workflows/build.yml`.
The debug APK is uploaded as a build artifact you can download and
sideload.

If you want to try building locally with a real Android SDK
(not Termux), standard steps apply: `./gradlew assembleDebug`.


## 0.6 production-motion pass
- Unified low-overshoot motion system tuned for fast interruption and repeated interaction.
- Reduced press scale from playful squish to a subtle 0.975 response.
- Hardware-accelerated WebView rendering with scrollbars/overscroll suppressed for cleaner chrome.
- WebView settings tuned for modern sites, zoom behavior, media playback, and cache reuse.
- Hardware acceleration explicitly enabled at the application level.
- Existing tab pooling, bookmarks, history, downloads, Spaces, AI assistant, translation, desktop mode and tab grid retained.
