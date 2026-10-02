# GeckoView experiment

SearchLauncher Gecko is an isolated Android build using GeckoView 157.0.20260924084938.
It installs alongside production and debug SearchLauncher as `com.searchlauncher.app.gecko`,
with separate preferences, index, bookmarks, browser profile and cookies. It does not migrate or
modify the regular app's data. The APK is signed with the local Android debug key.

## Build and install

Use JDK 17, Android SDK Platform 37.0, and the checked-in Gradle wrapper:

```sh
./gradlew assembleGecko
adb install -r app/build/outputs/apk/gecko/app-gecko.apk
```

The experimental APK currently targets ARM64 (including the Xiaomi 15, Xiaomi Pad 7, and the
Apple Silicon Android emulator). `assembleDebug` and `assembleRelease` still use WebView and
contain no Gecko dependency. The experiment upgrades the branch's toolchain to AGP 9.1.0,
Gradle 9.3.1 and Kotlin 2.4.20 for Gecko's current dependency requirements. AGP emits a warning
that SDK 37.0 is newer than its tested SDK; AAR compatibility checks remain enabled.

## Implemented in this prototype

- Browser navigation and desktop mode, using the existing SearchLauncher search overlay.
- Shared public tab model: launcher search, favorites/bookmarks, tab overview, and recents tasks.
- Lazy Gecko runtime initialization. Renderer/GPU/helper processes never initialize AppSearch.
- Public popup sessions retain `window.opener`, rather than reloading the popup URL independently.
- Back/forward, reload, find, text selection, sharing, bookmarks and website favorites.
- Session-state snapshots for restoring public tabs through their recents task IDs.
- Original-response download streaming into the existing Downloads history, including blob exports.
  These transfers survive leaving the browser activity, but have no process-death resume yet.
- File selection, JavaScript dialogs, basic HTTP authentication, select controls, OS/site permissions,
  camera/microphone requests, fullscreen content, and explicit external-app handoff prompts.
- Separate private sessions in the incognito process/profile; no private history or previews.
- Per-host clear-data action through Gecko's storage controller.
- Android display and dismissal of local/service-worker website notifications, with notification taps
  forwarded to Gecko and a safe source-page fallback.

## Important boundaries

**Remote Web Push is not configured.** A running page/service worker can show notifications, but
this is not evidence of receiving a server's push after the browser process exits. A production
push transport/subscription service needs to be chosen, provisioned, integrated and tested.

The WebAuthn API is exposed, but successful registration/login with Proton Pass and KeePassDX
has not been established. An engine change does not automatically grant a password provider's
browser trust or make restricted OAuth providers accept this app.

This is a compatibility prototype, not full UI parity: page-derived toolbar colors, private popup windows, PiP/background media,
site-specific JavaScript/cookie/ad-block controls, and aggregate storage sizes are not yet ported.
The browser uses Gecko's defaults for those engine settings; existing WebView settings are not
silently claimed to apply. The global Website storage panel explains the missing size reporting;
the browser's own clear-site action targets Gecko data.

Gecko's engine is bundled, so its security updates require new app builds. Engine/APK size and
real Xiaomi phone/tablet performance must be measured before deciding on a migration. Existing
WebView cookies, IndexedDB and session bundles are not automatically transferable.

## Device checks

The Gecko-specific instrumentation test uses a local HTTP fixture in the emulator, not mocked
engine responses. It checks the user agent, browser APIs, a blob's actual downloaded bytes,
popup-to-opener messaging, Android notification display, and storage behavior.

```sh
./gradlew assembleGecko assembleGeckoAndroidTest -PtestBuildType=gecko
adb install -r app/build/outputs/apk/gecko/app-gecko.apk
adb install -r app/build/outputs/apk/androidTest/gecko/app-gecko-androidTest.apk
adb shell pm grant com.searchlauncher.app.gecko android.permission.POST_NOTIFICATIONS
adb shell am instrument -w \
  -e class com.searchlauncher.app.ui.browser.GeckoBrowserDeviceTest \
  com.searchlauncher.app.gecko.test/androidx.test.runner.AndroidJUnitRunner
```

Before adopting this engine, test real OAuth and passkey providers, DigiD, authenticated and
single-use downloads, uploads, camera/microphone, notification click routing and permission
revocation, session restore after process death, storage clearing, and performance on both Xiaomi
devices. Background push must pass separately, including closed tabs, process reclamation and
reboot; a user force-stopping an Android app is a different restriction.

## Validation on 2026-10-02

- `spotlessCheck`: passed.
- `test` / normal debug unit suite: 437 passed, 3 skipped, no failures.
- `assembleDebug` and `assembleGecko`: passed.
- ARM64 Android 15 emulator: both Gecko instrumentation scenarios passed. Verified fresh blob
  export bytes in Downloads, popup opener messaging, Android notification display, local storage,
  public/private isolation, private data disappearing after closing the session, and clearing a
  public site's data from the browser menu.
- Physical Xiaomi devices, actual third-party passkey providers and remote Web Push remain unverified.

During integration, the emulator exposed and verified fixes for two concrete issues: duplicate
AppSearch initialization in Gecko child processes, and missing screen offsets for Gecko's
accessibility bounds when embedded below Compose content.

## Experimental build 2

- Removed the permanent experiment header; details remain in the overflow menu. Private mode
  keeps a discreet icon in the bottom bar.
- Browser/system bars use the tab frame color, with contrasting icons, rather than inheriting
  the launcher theme. Restored the outlined tab-count button.
- Restored horizontal bottom-bar gestures between tabs and back to home, using the existing
  drag distance, flick threshold, and home-frame preview. Short/cancelled drags settle back.
- Capped the menu at 420 dp or 60% of the available height; it scrolls and has action icons.
- Capture previews on content paint/load, await a fresh capture before showing the overview or
  search overlay, and capture before suspending the session. Ignore late/hidden-page results and
  preserve the cached frame. This addresses the screenshot/visibility race behind intermittent
  white previews; the reported physical-device behavior still needs confirmation.
- Added a device scenario checking actual preview pixels, light toolbar, repeated overview/home
  round trips, a cancelled short drag, and reaching the bottom of the scrollable menu.

Proton Pass password/passkey crashes were reported against build 1. No phone was connected during
this follow-up, so its crash log is not available and no Proton crash fix is claimed in build 2.

## Experimental build 3

- Connected public Gecko tabs to the shared favicon cache. A bundled, top-level content script
  reports declared icon URLs (including custom paths and dynamically changed links), with a
  same-site `/favicon.ico` fallback. Icon fetching is anonymous and size-limited; decoding uses
  Gecko for SVG/ICO support. No external favicon lookup service is used.
- Website icons now populate open-tab recents, tab cards, Android task icons and saved favorites.
  Newly saved icons trigger refresh of favorites/history that were already showing a globe.
  Tabs can restore cached icons without fetching them again.
- Restored the optional browser favorites/recents strip, using the launcher's existing preferences,
  pinning, ordering and history filtering. Toggle it with Show favorites / Hide favorites.
- Private tabs do not install the icon bridge or write to the public cache.
- Retained the requested initial URL while the asynchronous icon bridge is installed, preventing
  Gecko's initial `about:blank` callback from replacing it.

Previously uncached websites need to be opened once in Gecko to collect their icon. This build
does not migrate icons from the regular app. Proton Pass crash investigation remains pending
affected-device logs.
