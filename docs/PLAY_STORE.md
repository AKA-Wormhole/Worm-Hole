# Play Store listing checklist

WormHole can be submitted as a production browser after the items below are completed in Play Console. Code-side blockers for a typical browser listing are addressed in the app (target SDK 36, optional hardware features, no advertising ID, in-app Privacy Policy and Terms, default-browser intent filters).

## Required Play Console fields

- **Privacy policy URL** (public): https://github.com/AKA-Wormhole/Worm-Hole/blob/main/docs/PRIVACY.md
- **App category**: Productivity or Tools (Browser)
- **Content rating**: IARC questionnaire. A general web browser is usually rated for teens/adults because the open web can include mature content. Do not declare “designed for children.”
- **Target audience**: 18+ recommended for a general-purpose browser.
- **Data safety form**
  - Collected: none by WormHole itself
  - Processed on device: browsing history, bookmarks, files the user downloads
  - Shared with third parties only when the user uses search or the optional AI assistant
  - Advertising ID: no
  - Encryption in transit: yes (HTTPS to sites the user visits)
- **Permissions justification**
  - INTERNET / NETWORK_STATE: load pages
  - CAMERA / RECORD_AUDIO: website getUserMedia after a prompt
  - LOCATION: website geolocation after a prompt
  - POST_NOTIFICATIONS: download progress
  - FOREGROUND_SERVICE_DATA_SYNC: continue downloads
- **Screenshots**: phone screenshots of home, a webpage, tabs, downloads, and settings
- **Feature graphic**: 1024×500
- **Contact email** for the listing

## Not done in code (you must do these in Play Console)

1. Create the Play app listing and attach the privacy policy URL.
2. Complete the IARC content rating questionnaire honestly (browser + user-generated/open web).
3. Complete Data safety.
4. Upload an AAB (`bundleRelease`), not only a fat APK, for Play.
5. Enroll in Play App Signing if this is a new listing.
6. Provide a support email and, if possible, a simple website.

## Build for Play

```
./gradlew bundleRelease
```

The workflow “Build APK” produces a sideload APK. Play wants the AAB from `bundleRelease`.
