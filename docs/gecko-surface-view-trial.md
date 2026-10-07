# SurfaceView scrolling trial — 5 October 2026

The user reports slow scrolling on atomic.place and its dashboard in SearchLauncher,
while Firefox on the same tablet scrolls smoothly. The production experiment uses
TextureView to integrate the page with Compose's horizontal home/tab transitions.

Build an opt-in, optimized comparison APK:

```sh
./gradlew spotlessCheck :app:assembleGecko -PgeckoSurfaceView=true
```

This retains GeckoView's constructor-created SurfaceView. Do not explicitly call
`setViewBackend(BACKEND_SURFACE_VIEW)` on a newly constructed GeckoView: the first
trial did that and produced blank pages with zero-sized accessibility bounds.
Mozilla's SurfaceViewWrapper recreates that surface without registering the listener
installed on the original surface. Keeping the constructor default fixed rendering.

The flag defaults to false. Ordinary builds retain TextureView. Candidate version is
`0.0.51-gecko-experimental.27-surface-test`, code 300, with the same package/signature
as Gecko27. Both APKs can replace each other without uninstalling. Prior favicon and
backup changes in Gecko27 are included in both. No website code is changed.

## Validation

Android 15 ARM64 Phone_A35 emulator, optimized non-debuggable APK:

- Full `spotlessCheck`, Gecko assembly, APK signature verification, and `git diff --check` passed.
- `FullscreenBrowserTest#siteAndVideoEnterAndExitFullscreen`: passed (6.414 s).
- `TypingPerformanceTest#navigationBarTracksSwipeAndCancellation`: passed (16.877 s),
  including fixture navigation, reload, completed download, home/tab committed swipes,
  reversal/cancellation, and navigation-bar color interpolation.
- Held intermediate swipe screenshots inspected: the page and toolbar translate together.
- Actual atomic.place page loaded and scrolled with injected vertical touch gestures;
  before/after screenshots retained. SurfaceFlinger reports the separate browser SurfaceView.

Earlier swipe attempts were blocked by emulator onboarding and the download notification
permission; those prerequisites were completed before the passing run. The first explicit
backend-selection trial also failed fullscreen rendering; that candidate is superseded.

Artifacts and logs: `/tmp/searchlauncher-surface-test/`, including the candidate APK,
preserved TextureView APK, `fullscreen.txt`, `transitions-ready.txt`, screenshots,
and `surface-layers.txt`. The physical tablet was not connected. These checks establish
basic functionality, not improved frame rate or elimination of device-specific swipe flashes.
Signed-in dashboard scrolling has not been tested. Compare both APKs on that tablet before
changing the default backend.

## Keyboard background follow-up (experimental 28)

The user reports much faster scrolling on the tablet with SurfaceView. They also report a
white area exposed after dismissing a website keyboard; whether it predates SurfaceView is
unknown. The browser's GeckoView background and compositor clear/first-paint color were
hard-coded white for both backends.

The browser content underlay, GeckoView background and compositor clear color now follow
`pageBackgroundArgb`, not the independently chosen toolbar theme. The first-paint cover on
creation/session recovery uses that same document background. This does not repaint or cover
an already rendered page on every keyboard resize. SurfaceView remains selected through the
opt-in build flag. Candidate: `0.0.51-gecko-experimental.28-surface-test`, code 301.

`BrowserKeyboardBackgroundTest` runs a black document with a contrasting red toolbar through
three input/touch-keyboard open/type/dismiss cycles. It checks visual viewport shrink/recovery,
returning browser controls, dark pixels in the area formerly occupied by the IME, and (in the
strengthened final version) visibility of the document's fixed bottom control.

The initial fixture passed on both unmodified Gecko27 renderers (4.489 s SurfaceView, 5.008 s
TextureView). It therefore does **not** reproduce or establish the cause of the reported tablet
strip. The fixed candidate passed the keyboard and fullscreen tests together (9.646 s), then
the strengthened bottom-control check (3.740 s); its screenshot was visually checked.
Repository-wide Spotless and debug unit tests also passed. The user's authenticated Atomic
page and Xiaomi-specific keyboard/compositor behavior remain unverified. This fixes the
confirmed hard-coded fallback color, not a demonstrated device-specific viewport fault.
