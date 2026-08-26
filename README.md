# WormHole

A Gecko-based Android browser. Package `com.wormhole.browser`.

- Engine: Mozilla GeckoView
- Min SDK 26 · Target / compile SDK 36
- Version: see `app/build.gradle.kts` (currently 1.7)

## What it is

WormHole is an installable browser: tabs, Spaces, downloads, bookmarks, history, find-in-page, desktop site, passkeys, optional extensions, and an optional Gemini assistant that uses a key you paste in Settings.

It is not a custom search engine and not a WebView wrapper. Pages render in Gecko.

## Privacy

In-app: Settings → Privacy Policy  
Public URL: [docs/PRIVACY.md](docs/PRIVACY.md)

## Play Store

See [docs/PLAY_STORE.md](docs/PLAY_STORE.md). Play wants an AAB (`./gradlew bundleRelease`), not only the sideload APK from Actions.

## Build

```
./gradlew assembleRelease
./gradlew bundleRelease
```

CI: `.github/workflows/main.yml` (Build APK).
