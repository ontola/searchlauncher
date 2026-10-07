# Gecko browser test audit — 3 October 2026

## Scope and outcome

Tested the installed SearchLauncher `0.0.51-gecko-experimental.14` (version code 289,
app revision `16620ff`) using Gecko 157 on the Android 15 ARM64 `Phone_A35` emulator.
No production app code changed. Added six instrumentation scenarios and a local HTML fixture.

Across 24 distinct scenarios, 22 passed after correcting emulator prerequisites; two remain
failing reproductions (upload and passkey cancellation). One passing audit scenario deliberately
records the outcome of website sharing, which **does not work**. These counts combine focused
runs and rechecks, not one clean full-suite run. They do not mean every browser feature works.

## Confirmed problems

| Feature | Reproduction and observed outcome | Boundary |
| --- | --- | --- |
| Website file upload | Open an `<input type=file accept=text/plain>`, select an owned text file in Android Downloads. The picker closes, but the page still says “No file selected”; expected bytes never arrive. Gecko logs `NS_ERROR_FILE_UNRECOGNIZED_PATH` in `FilePickerDelegate.sys.mjs`, `_getDOMFile`. Reproduced in multiple focused runs. | Confirmed for this Downloads document-provider path; other providers/multiple-file selection not tested. The host passes selected content URIs directly to Gecko's file prompt. Path conversion is the likely integration failure, not a completed root-cause fix. |
| Website sharing | After a successful clipboard round trip, tap the fixture's Share button. `navigator.share()` rejects with `AbortError` without opening a sharesheet. | The browser menu's separate Share action is not this API. The audit test records rejection rather than treating API presence as support. |
| Cancelling a passkey request | With KeePassDX enabled, call `navigator.credentials.get()` and abort after three seconds. JavaScript finishes, but the Android credential picker remains on top, preventing the next page interaction. | No browser crash observed. This tests cancellation, not registration/sign-in, which were exercised separately before this audit. |

Useful regressions: `fileUploadReturnsTheChosenBytes`,
`clipboardRoundTripAndWebsiteShareOutcome`, and `passkeyRequestsReturnToThePageWithoutCrashing`
in `GeckoBrowserDeviceTest.kt`. The two failure tests remain enabled to preserve reproductions.

## APIs actually exercised

- Location: site denial returns permission error 1; allowing a fresh origin returns the injected
  coordinates `52.370216, 4.895168`, accuracy 3 m. Android runtime permissions were pre-granted.
- Camera/microphone: site denial returns `NotAllowedError`; allowing a fresh origin produces live
  audio/video tracks and an actual video frame. Stopping tracks changes their state to ended.
- Clipboard: a user-triggered write/read returns the original text.
- Storage: localStorage and sessionStorage round trips; IndexedDB write/read; Cache Storage
  put/match/delete; OPFS create/write/read/delete; positive storage quota.
- Networking: an actual WebSocket echo and an in-process WebRTC peer pair exchanging a data-channel
  message. No external STUN/TURN, network traversal, or real audio/video call was tested.
- Compute/rendering: Worker message exchange, AES-GCM encrypt/decrypt, WebAssembly instantiation,
  Canvas pixel check, WebGL2 clear/readPixels.
- Other: BroadcastChannel message exchange, Blob read, wake-lock acquire/release.

All 16 functional core probes completed successfully in the final focused recheck.

## Absent or incomplete web capabilities

The secure localhost fixture reports no Web Bluetooth, WebUSB, Web Serial, WebHID,
`showOpenFilePicker`, `showSaveFilePicker`, `showDirectoryPicker`, or `getDisplayMedia`.
These are absent APIs, not permission dialogs that were denied. OPFS works but only provides
private storage for the website, not arbitrary access to user folders.

Local website notifications passed the existing regression. `PushManager` is exposed, but remote
Web Push transport is not configured and end-to-end push delivery was not tested.

## Tab and recovery stress

The tab stress scenario passed in 85.74 seconds:

- Opened 12 owned tabs, each retaining a 2 MiB JavaScript buffer, document identifier, and session marker.
- Switched 36 times, alternating direction, and checked the active document and retained memory.
- Explicitly reloaded all 12 tabs and checked sessionStorage survived.
- Each tab reported exactly two document loads: initial load plus requested reload. No unexpected reloads.
- Browser process ID remained unchanged. Closing the owned tabs left the browser responsive and able
  to complete the core API probes again (the 12-probe version before the four networking/render additions).

Parent-process PSS at the three sample points was 296,194 / 316,115 / 302,963 KiB
(about 289 / 309 / 296 MiB). **This excludes Gecko child processes**, so it is not total browser RAM.
The scenario is a bounded synthetic stress check, not a long-duration leak or physical-device
frame-performance measurement. Its interaction timings include test waits and must not be used as
real tap latency measurements.

Foreground renderer termination/retry/new-navigation and background renderer termination/return
both passed after enabling emulator root. Repeated tab preview/home handoffs also passed after
setting Gecko as the default home app. The first run lacked these prerequisites; those initial
failures are not counted as product defects.

## Existing regressions rechecked

Navigation/popups, JavaScript exports, local notifications, ad filtering and both settings,
startup/reload address preservation, opaque failure pages/retry, app links with embedded-frame
isolation, direct-download routing, tab retention across home, stable download notification
identity, stable completion-card ordering, website keyboard viewport, built-in address keyboard,
download progress, private storage isolation/clearing, and favicons all passed. Passkey cancellation
was the remaining failure among the existing scenarios after the prerequisite rechecks.

## Reproduction and evidence

Source fixture: `app/src/androidTest/assets/browser-api-fixture.html`.
Instrumentation: `app/src/androidTestGecko/java/com/searchlauncher/app/ui/browser/GeckoBrowserDeviceTest.kt`.
Use a dedicated disposable emulator: these existing tests exercise downloads, browser settings,
site data, home roles, and renderer termination.

With JDK 17, build and install the test APK against the installed matching Gecko build:

```sh
./gradlew spotlessCheck assembleGeckoAndroidTest -PtestBuildType=gecko
adb -e install -r app/build/outputs/apk/androidTest/gecko/app-gecko-androidTest.apk
adb -e root
adb -e wait-for-device
adb -e shell input keyevent KEYCODE_WAKEUP
adb -e shell svc power stayon true
adb -e shell cmd package set-home-activity com.searchlauncher.app.gecko/com.searchlauncher.app.ui.MainActivity
adb -e shell am instrument -w -e class com.searchlauncher.app.ui.browser.GeckoBrowserDeviceTest#browserApiOperationsAndHardwareAvailability com.searchlauncher.app.gecko.test/androidx.test.runner.AndroidJUnitRunner
```

Change the method suffix to select other scenarios, or omit it to run the class. Location requires
mock `gps` and `fused` providers continuously supplying the expected Amsterdam coordinates; a single
`adb emu geo fix` left the fused provider returning old coordinates in the first run. The captured
`run-gecko-audit.py` sets both providers every two seconds and removes them in `finally`; adjust its
ADB path before using it. KeePassDX must be enabled to reproduce the credential-picker issue.

Local evidence is saved in `build/browser-audit-2026-10-03/` (ignored by Git): instrumentation
outputs, per-scenario JavaScript reports, UI hierarchies, screenshots, and filtered file-picker
logcat. Key outputs are `gecko-tab-stress-results.txt`, `gecko-tab-stress-evidence.txt`,
`gecko-api-audit-recheck.txt`, `gecko-upload-focused.txt`, `gecko-browser-regressions.txt`, and
`gecko-browser-recovery-api-recheck.txt`. The last file confirms all four prerequisite/core rechecks
passed. File upload screenshots show the returned browser with no selected file; the passkey XML
shows the provider picker remaining on screen after JavaScript finished.

No physical phone was connected. Real GPS accuracy, physical Bluetooth/USB devices, camera and
microphone quality, Xiaomi animation smoothness, long-running background push, and Proton Pass
remain outside this audit. No APK release was made.
