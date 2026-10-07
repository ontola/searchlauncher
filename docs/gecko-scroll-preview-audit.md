# Gecko scrolling and address-bar copy — 4 October 2026

Tested against the Gecko experiment on the Android 15 ARM64 emulator. The baseline APK was
experimental build 14; the updated local APK retains that version and is not a new release.

## What was found

A fixture with 200 long article cards, a sticky header, and 80 history entries was scrolled
with 12 alternating gestures. The baseline recorded no session-history preference writes during
the gestures and one after idle. Consequently, session persistence was left unchanged; this test
did not identify it as the cause of the reported slowdown.

A second check dragged the document, then kept the finger pressed for 600 ms. Build 14 replaced
the tab preview during that held gesture, failing the new assertion. Automatic preview capture
was scheduled only 200 ms after the last scroll callback, irrespective of whether the finger was
still down. Its bitmap scaling also ran in the UI callback.

## Change

- Observe touch down/up/cancel without consuming the event; Gecko retains native scroll, fling,
  selection and pinch handling.
- Defer automatic previews while a finger is down and until 500 ms after scrolling settles.
  New scroll callbacks postpone the capture. Page-load captures use the same scheduling path.
- Resize preview bitmaps on a background dispatcher. Recheck navigation, visibility and gesture
  state before publishing; recycle unadopted bitmaps even if the lifecycle cancels the job.
- Keep explicit captures for tab overview/search/lifecycle transitions.
- Long-press the browser address area to copy the full current URL using existing clipboard
  feedback. A regular tap still opens search. This is wired into both Gecko and WebView chrome.

## Validation

The held-scroll regression passes on the updated build: no preview replacement during the held
finger, followed by a refreshed preview after release. The full-URL clipboard assertion includes
an encoded query and fragment, rather than just the shortened displayed address. Existing checks
cover ordinary search-bar tapping with the built-in keyboard, repeated preview/home swipes,
retaining documents across home/tab switches, and startup reload/history restoration.

The first combined run passed five of six tests; the copy assertion passed but its subsequent
immediate tap hit Android's clipboard overlay and opened its sharesheet. The focused copy test now
asserts clipboard contents and retained browser activity. Ordinary tapping remains covered by the
separate existing test, avoiding a tap through system UI. The focused recheck passed both tests in 23.235 seconds. All six distinct scenarios therefore
passed across the combined run and focused recheck; results are saved with the other evidence.

`assembleGecko`, `assembleGeckoAndroidTest`, `compileDebugKotlin`, and `spotlessCheck` passed.
Evidence: `build/browser-scroll-audit-2026-10-04/` (Git-ignored), including failing baseline output
and updated test results. Tests live in `GeckoBrowserDeviceTest.kt` as `scrollWorkloadAudit` and
`holdingAddressCopiesFullCurrentUrlWithoutOpeningSearch`.

This removes demonstrated preview work during a held gesture; it is **not** a measured FPS or
scroll-speed improvement on the user's Xiaomi. No affected site URL was supplied during this run,
and no physical phone was connected. Site-specific slow scrolling still requires reproduction;
Gecko's scrolling physics and compositor backend have not been changed.
