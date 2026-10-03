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
adb root # Emulator only: required by the process-termination recovery test.
adb wait-for-device
# Record the current home app first, and restore it after the test run.
adb shell cmd package set-home-activity com.searchlauncher.app.gecko/.ui.MainActivity
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

## Experimental build 4

- Gecko responses open the existing full-screen Downloads panel immediately, including direct
  attachment links and JavaScript blob exports. The browser menu opens the same panel, keeping
  in-flight progress visible in the current browser process. System bars match its surface.
- Dismissing a download-only tab removes that empty tab. Exporting from a rendered page preserves
  the source page. Transfers continue after dismissing the temporary download tab.
- Completed files use DownloadManager's recorded file length for size display. Imported downloads
  can report zero transferred bytes even with a nonempty saved file. Partial and failed transfers
  continue to display only the bytes transferred. Existing history benefits without redownloading.
- Browser windows no longer inherit the launcher wallpaper flag. Gecko has an opaque compositor
  clear color, and the live surface is hidden behind load errors so retry controls remain visible.
  Retry and the Reload menu action target the failed address, even if it never committed.
- Validation: spotlessCheck, regular and Gecko unit suites, both APK builds, and six Android 15
  ARM64 device scenarios passed. The new direct-download scenario verifies live progress, exact
  saved bytes after closing the temporary tab, a single server request, and the visible file size.
  The blob-export scenario verifies Downloads opens and returns to the source page. A stalled
  reload retains an opaque viewport; a refused connection shows a drawn error and successfully
  retries once its local server becomes available.

Physical Xiaomi verification and the separately reported Proton Pass crash remain outstanding.

## Experimental build 5

- Restored domain-based ad filtering through a bundled privileged Gecko extension. Subresource
  requests use the existing AdBlocker rules, global toggle and saved site exceptions. Explicit
  top-level navigation remains allowed. The extension is enabled for private browsing without
  installing the public favicon/content-script bridge there. No request data is sent to a service.
  Native replies are booleans, and request decisions have a bounded timeout to prevent a stalled
  native callback from hanging the page.
- `about:blank` remains an internal empty tab instead of becoming a Google search. Gecko's initial
  blank location/state no longer replaces a requested or saved website address. Reload during
  startup leaves the real navigation underway.
- Restore from the saved URL when the history snapshot is absent, corrupt or stale. Flush history
  on location changes and persist matching snapshots, including same-document navigation, so
  activity recreation preserves the current route and Back history.
- Validation: both unit suites and APK variants, formatting, seven existing/new Gecko scenarios,
  and the corrected ad-filter scenario passed on Android 15 ARM64. The latter checks server-side
  absence of blocked requests, global disabling, per-site exceptions, re-enabling, and direct
  navigation to a listed host. Restoration coverage includes a missing snapshot, early Reload,
  pushState navigation, activity recreation, Reload and Back.
- Manually opened app.atomic.place, entered its demo, navigated to Team, refreshed and restored
  the tab after process restart. The reported Google search was not reproduced by that sequence;
  the independently confirmed blank-URL parser and startup/restore defects are addressed above.

The Proton Pass crash and physical Xiaomi verification remain outstanding.

## Experimental build 6

- Active Gecko downloads and page exports now use the same rounded card, icon tile, two-line
  filename and status typography as completed downloads. Progress and percentage stay inside
  the card; unknown-size downloads keep an indeterminate bar. Pending and completed downloads
  share one scrollable list.
- Browser-toolbar swipes measure drag distance and fling velocity in stationary screen
  coordinates. The home gesture uses those coordinates for velocity too. The browser-to-home
  handover waits for the slide to finish instead of cutting it off at 85%. The destination bitmap
  moves in the render layer without recomposing on every offset change, and previews refresh
  after page scrolling settles rather than starting a new bitmap capture during the gesture.
- Reopen Gecko sessions after a content-process kill/crash before retrying or navigating to
  another address. Reattach the view and icon bridge, restore matching in-memory history for a
  retry, and protect the destination against startup `about:blank` events. Website storage is
  retained. This fixes recovery; it does not establish why Android reclaimed memory on the
  reported Xiaomi device.
- Emulator coverage includes real `kill -9` termination of Gecko content processes, successful
  retry with retained site storage, another termination followed by same-tab navigation to a
  different URL, and repeated swipes in both directions between the browser and home. The test
  fixture disables HTTP caching so recovery evidence cannot be hidden by cached report requests.

Validation: `spotlessCheck`, both browser variants' unit tests and APK builds passed. All nine
Gecko emulator scenarios passed across the full run and the corrected bidirectional swipe rerun
(the first run hit first-launch setup dialogs, now explicitly dismissed in the fixture).

Physical Xiaomi smoothness and the separately reported Proton Pass crash remain unverified.

## Experimental build 7

- Prepare the home preview while the launcher is idle and resumed, before any swipe starts.
  The old capture happened after child layers began moving and could include the incoming browser
  inside the next home preview. Pending captures now cancel when home starts transitioning.
- Measure both toolbars' gestures with their actual rendered position in the root window. Keep
  home gesture callbacks current without restarting pointer input on recomposition.
- Use Gecko's TextureView backend so the live page participates in the same animation as its
  toolbar. The outgoing page no longer swaps to a bitmap while dragging. TextureView supports
  this composition but has additional rendering cost compared with SurfaceView.
- Keep the wallpaper window configuration stable and explicitly clear the revealed part of the
  root canvas. This prevents old page pixels leaving trails and keeps the system wallpaper visible
  through the home preview. Loading and error states retain an opaque page panel.
- Reset a completed browser swipe when its activity stops, as well as when it resumes, so reopening
  the last tab does not begin from its old off-screen position.

Validation includes a home-preview pixel regression that fails against build 6 and passes here,
plus recorded repeated swipes in both directions. `spotlessCheck`, both unit suites and APK builds
passed. All nine Gecko scenarios passed across the full run and the notification/recovery rerun
after granting the missing Android permission and enabling emulator root for the deliberate
process kill. The notification fixture now grants its own runtime permission.

Physical Xiaomi frame pacing remains unverified;
this is not a claim of measured FPS improvement on the phone or tablet.

## Experimental build 8

- Prevent Android's cached task screenshot from fading over the live page when reopening the
  last tab. Handovers from an already drawn activity use the scene-transition protocol with
  enter, exit, reenter, return and background effects disabled. Clear NO_ANIMATION for that
  path: combining it with the protocol reproduced the stale home-keyboard overlay. Cold launches
  and application-context callers retain explicit zero-animation options.
- Share this path between home, tab activation and the Gecko home action. Keep the final app-drawn
  swipe frame in place when an activity stops; reset it before it starts/resumes again.
- Exercise the default-HOME configuration in the swipe scenario and check that repeated gestures
  return to the same home task. Check the fixture's lower viewport for keyboard remnants as well
  as the existing home-preview, toolbar and tab-thumbnail assertions.

The fade was reproduced in emulator recordings with window/task animations at 5x speed. The
corrected recording shows four round trips without the fading keyboard overlay. Pixel sampling
of the fully arrived page found 84 overlay samples in the earlier recording and none during the
corrected gestures (30 fps resampling, not an FPS/performance measurement). Physical Xiaomi
verification remains outstanding.

Validation: all nine Gecko emulator scenarios passed, including renderer recovery after process
termination. Both Gecko and debug unit suites passed (442 tests each, three skipped each), along
with full `spotlessCheck`, Gecko APK/test APK builds and the regular debug build.

## Experimental build 9

- Restore a killed page automatically while its browser activity is resumed. If it was killed
  in the background, wait until the user returns; do not restart hidden tabs. Reopen the closed
  Gecko session and restore the matching saved session state through the existing recovery path.
- Allow at most one automatic recovery per page instance in 30 seconds. A repeated termination
  within that window leaves the manual retry action available. Crashes still require manual retry.
- Keep visible sessions active until ON_STOP, including while paused behind a prompt or during
  a task transition; reactivate at ON_START. Do not queue activity changes on a closed session.
- Replace the memory-reclamation claim with a neutral stopped-page message: Gecko's onKill callback
  establishes process termination, not its cause. Log kill/crash and foreground/background status
  without URLs or page content for subsequent device diagnosis.

Mozilla's [session visibility contract](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSession.html#setActive(boolean))
requires inactive sessions to be hidden; its [content delegate contract](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSession.ContentDelegate.html#onKill(org.mozilla.geckoview.GeckoSession))
requires reopening a killed session before loading or restoring it.

Focused emulator tests terminated real Gecko child processes with SIGKILL. They verified automatic
foreground recovery with preserved local storage, suppression of repeated automatic reloads,
manual retry, navigation to another address, deferred background recovery, and preserved Back history.
The underlying reason for the Xiaomi process termination remains unverified: no physical phone
was connected for log capture during this change.

Validation: all ten Gecko device scenarios passed on the Android 15 ARM64 emulator, including
the existing swipe/preview checks. The Gecko unit suite passed (442 tests, three skipped), as did
full `spotlessCheck`, the Gecko app build and its instrumentation APK build.

## Experimental build 10

- Keep streamed downloads in the same keyed card from progress through completion. Publish the
  registered DownloadManager ID and completed metadata before cleanup, so the next 750 ms history
  poll cannot leave a gap or move an older APK into the first row. Deduplicate that history record
  by ID, preserve existing order, and insert late initial history by age. Remove retained transfer
  metadata when its observed history record is deleted. The card resizes over 160 ms on completion.
  This shared path covers Gecko responses, webpage exports and direct WebView downloads.
- Remove the rectangular address-field press indication from Gecko chrome and vertically align
  its address with the toolbar actions. Browser search now honors the built-in keyboard preference,
  using the same keyboard component as home. Apply the window setting before the first frame and
  avoid requesting a system IME on focus when the built-in keyboard is selected.
- Match the existing WebView backend's HTTPS app-link handling for top-level Gecko loads, including
  script navigation and redirects after a user gesture has expired. Android must still resolve a
  default non-browser app. Embedded frames cannot trigger this handoff. This addresses a missing
  route used by login handoffs such as DigiD; no authenticated DigiD login was performed.

Regression coverage includes late download history, the registration-to-poll gap, stable row keys,
concurrent downloads finishing out of order, and deletion. A device test repeatedly checks the
latest filename's screen position through completion with older downloads below it. The keyboard
scenario types via a built-in key, dismisses back to the page, and checks the system-IME fallback.
A separate test APK receives an exact HTTPS URL after a JavaScript navigation and HTTP redirect;
an iframe to the same registered host is also checked to remain inside the browser.

Validation: all thirteen Gecko emulator scenarios passed. Both Gecko and debug unit suites passed
(445 tests each, three skipped each), as did full `spotlessCheck`, both app builds, and the Gecko
instrumentation build. Download and keyboard screenshots were inspected. Physical DigiD account
authentication and Xiaomi-specific behavior still require confirmation on the phone.

## Experimental build 11

- Give the six most recently visible Gecko sessions high retention priority. Hidden sessions remain
  inactive for rendering; returning to home or another tab no longer drops all pages straight to
  default process priority. Older sessions return to default priority and closing a page releases
  its hint. This is a bounded retention preference, not a guarantee against Android reclamation.
- In-process downloads and webpage exports now show an ongoing Android notification with the
  filename and determinate/indeterminate progress. The same notification becomes complete or
  failed; tapping it opens Downloads. Completion does not launch an APK installer automatically.
- Request Android notification permission once when the user starts a download. Denial does not
  stop the download. Use a quiet Downloads channel and avoid duplicate DownloadManager completion
  notifications for these imported files. Normal DownloadManager transfers retain system handling.
- Throttle progress posts and hide filenames from public lock-screen notification content.
- [Browser API audit](browser-api-audit.md) records the current Gecko capability and integration gaps.

Validation: both regular and Gecko unit suites report 447 tests, no failures/errors, three skipped;
spotlessCheck and both APK builds pass. All 15 Android 15 ARM64 emulator scenarios pass. The new
retention scenario checks repeated home/two-tab switches preserve volatile JavaScript state and
cause no extra document loads. Download coverage checks progress, stable notification identity,
completion, and opening Downloads. Existing killed-process recovery and animation scenarios pass.
Xiaomi memory retention remains unverified because no physical phone was connected. Downloads
still do not resume after process death; these notifications do not add a foreground transfer service.

## Experimental build 12

- Publish and save a wallpaper selection only after the pager settles. Previously `currentPage`
  changed halfway through motion, starting palette/theme work and scheduling a home preview capture
  120 ms later, during the end of the slide.
- Report wallpaper motion to the home screen so another swipe cancels a pending preview capture.
  Ignore asynchronous saved-wallpaper echoes during active scrolling instead of interrupting it
  with `scrollToPage`. Direct finger tracking and the existing settling animation are preserved.
- A drag that crosses halfway and reverses does not publish a temporary wallpaper/theme selection.

Validation: a new device regression fails on the previous implementation because it publishes the
selection during motion, and passes with this change. Two wallpaper emulator tests cover keyboard
animation completion, the settled preference, motion callbacks, and a finger drag crossing halfway
and reversing. The Gecko home/tab preview and swipe-handoff scenario also passes. Both unit suites
report 447 tests, zero failures/errors and three skipped; spotlessCheck and both APK builds pass.
These checks establish timing/state correctness on Android 15 ARM64; the reported Xiaomi visual
hiccup still needs confirmation on the physical phone.

## Experimental build 13

- Open browser search immediately instead of awaiting a compositor screenshot (previously capped
  at 500 ms). Preview capture continues asynchronously. This removes an avoidable wait; it is not
  a claim that every tap previously took 500 ms or that all activity startup costs are eliminated.
- Slide the built-in keyboard and search/favorites/results column in together over 140 ms. The
  entrance transforms an already measured layout, avoiding repeated search-row layout work.
- Hide Gecko's browser toolbar/favorites while a website uses the system keyboard, restoring them
  on dismissal. Keep the page resized to the actual IME insets. Find-in-page controls can still
  appear above the keyboard.
- Keep explicit browser navigation (including pasted URLs) inside Gecko even when another app
  claims the domain. Recognizable file links also remain in the browser so its download handler
  can inspect the response. Scripted app links and redirects remain supported for login handoffs.
  The regression reproduced with a verified test handler; the exact Xiaomi-to-Chrome chain has
  not been traced on the physical device.

Validation: both unit suites report 449 tests, zero failures/errors and three skipped; full
spotlessCheck and both app builds pass. All 17 Gecko emulator scenarios passed, followed by
focused keyboard rechecks on the final inset implementation. Emulator recording confirms the
keyboard entrance motion. Website typing coverage checks toolbar dismissal/restoration and the
full bounds of a bottom input on a page using `interactive-widget=resizes-content`.

Known limitation: a default `resizes-visual` fixed-position input in the fixture was partly clipped
by Gecko after caret panning (47 physical pixels on this emulator), despite the native browser view
ending exactly above the IME. Removing browser chrome does not resolve that separate engine/page
viewport interaction. No forced meta-viewport rewrite or site-script workaround is applied.
Physical Xiaomi behavior and end-to-end latency still need confirmation on the phone.
