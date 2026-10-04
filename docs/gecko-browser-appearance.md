# Gecko browser bar colors — 4 October 2026

## Behavior

Gecko previously left `BrowserTab.pageBackgroundArgb` at white and never populated its theme
color. The webpage could honor dark mode while the search bar, status area and navigation area
remained white. A top-level isolated content script now supplies computed document background and
media-qualified theme-color metadata. The existing tab model drives all three bar backgrounds.

The bridge validates session, top-level sender and current document URL. It handles live root/body
attribute changes, stylesheet/meta changes, media-query changes, and page restoration. It does not
listen to scrolling or sample website screenshot pixels. Private tabs get appearance metadata
without enabling favicon disk caching. The existing metadata extension version is bumped to 1.1
so installed builds update the content scripts.

An unqualified pure-white theme-color over a dark page is treated as stale and falls back to the
page background. NOS currently serves such a white declaration; blindly honoring it would leave
this reported bug visible. Explicit media-qualified colors and other site theme colors are retained.
New document loads clear the old document's colors; pages with no color styling retain a readable
white default rather than inheriting a previous site's dark background.

Browser colors transition together over 140 ms. Foreground text/icons switch at luminance 0.18,
rather than 0.5, to avoid pale text on middle-gray animation frames. Window setup is applied once,
not recreated on each color-animation frame. Android controls its own system-icon transition timing.

## Device and visual checks

Android 15 ARM64 emulator, locally built Gecko APK (not a published release):

- Dark and light `prefers-color-scheme` theme-color variants; switch both directions while open.
- Dynamic body fallback, inserted theme-color and removed theme-color.
- Generic white theme-color over a dark document, reproducing the NOS declaration.
- Embedded iframe with a magenta theme cannot recolor the top-level browser.
- Fresh unstyled white page, followed by returning to the retained dark tab.
- Exact screenshot pixels at status/search/navigation bar edges after each state settles, plus
  status/navigation icon-mode assertions.
- Favicon and private-storage regressions passed (2/2). Appearance scenario passed (1/1).
- Live `https://nos.nl` rendered dark with dark top/bottom browser chrome; saved screenshot inspected.

Recorded the actual emulator screen, decoded every recorded video frame and saved per-frame bar
color samples. In the browser transition interval (2.6–18.3 s), all three sampled bar colors match
on every recorded frame. Dark-to-dark transitions did not pass through white. Inspected intermediate
frames visually; this revealed the weak foreground contrast and led to the luminance-threshold fix.
Android's system glyphs have their own transition timing, so this is not a claim of identical glyph
animation timing across devices. Capture cadence varies; this is not a physical-device FPS benchmark.

Evidence is in `build/browser-appearance-2026-10-04/` (Git-ignored): `theme-final.mp4`,
`theme-slow.mp4` (3x slower excerpt), `frame-colors.csv`, `frame-summary.json`, inspected PNGs,
`nos-dark-chrome.png`, and instrumentation outputs. Build and `spotlessCheck` passed.


## Follow-up: app theme settings and header fallback (experimental 16)

The earlier checks set Gecko's runtime preference directly, with Android in dark mode. That did
not exercise SearchLauncher's persisted Dark/OLED setting against a light Android configuration.
The expanded device scenario reproduces the mismatch on published experimental 15: the first
website document reports `prefers-color-scheme: dark = false` despite the app preference.

Gecko now reads the persisted app preference before loading a document, observes live changes,
and receives Android configuration changes through the runtime's `configurationChanged` API.
System, Light and Dark have distinct mappings; OLED remains an app-surface setting rather than
rewriting a site's own CSS. The browser activity opts out of the generic app theme's system-bar
SideEffect, so that recompositions cannot overwrite page-dependent icon contrast.

Tweakers does not currently declare a theme-color in its HTML or web manifest. When there is no
valid theme declaration, the metadata bridge can use the solid background of a broad main header
or navigation element near the document top. Article headers, dialog contents and gradients are
excluded. Explicit metadata wins. The selected header is retained through scrolling so hiding a
sticky header does not make the browser bars switch to white. No scroll listener or image pixel
sampling is introduced. The metadata extension version is 1.2.

The expanded test changes the actual DataStore preferences and Android night mode. It covers the
first document, forced Dark against system Light, forced Light against system Dark, live System
mode changes, OLED recomposition over a white page, a red navigation header, explicit-theme
precedence, and returning to a retained dark tab. It checks rendered bar backgrounds and Android
icon-mode flags. Optional `-e liveAppearance true` additionally opens NOS and Tweakers with the app
forced Dark/OLED while Android is Light. These are emulator checks, not physical Xiaomi results.


Three-button emulator limitation: the Android 15 Google image's Pixel taskbar forces its own
background icon palette (`mOnTaskbarBackgroundNavButtonColorOverride=1`) even when both the app
and SystemUI report the correct light/dark navigation mode. Thus OS glyph pixels can still be dim
on dark or red bars in that configuration. Enabling contrast enforcement and making the window
navigation color transparent did not change that override; neither workaround was retained.
This does not establish the same platform behavior on Xiaomi. The app's conflicting theme
SideEffect is fixed, but physical-device navigation-icon contrast still needs confirmation.


Final checks: `spotlessCheck` passed; `testDebugUnitTest` reported 449 tests, zero failures/errors,
and 3 skipped. Device appearance (with live NOS/Tweakers), favicon propagation and private storage
passed 3/3. Live bar colors were NOS `#202020` and Tweakers `#a11236`. Evidence and the old-build
failure are saved in `build/browser-appearance-2026-10-04/fix16/` (Git-ignored).


## Same-domain navigation (experimental 18)

Keep the previous page background and chrome theme while navigating within the same
host, including reloads. Track the appearance host separately from `tab.url`, which may
already contain the destination by the time Gecko announces navigation. Both page-start
and location-change callbacks apply the rule so cross-host redirects reset the old color.

The appearance bridge waits for document load before publishing its initial colors,
avoiding transient white styles while an asynchronous stylesheet loads. Once loaded,
a new theme or genuinely unthemed white page still updates normally. Live color-scheme
and theme changes remain supported.

`browserFrameKeepsColorThroughSameHostNavigation` delays both the next document and its
stylesheet. On Phone_A35, all 81 sampled display frames retained red; screenshots of the
status, address, and navigation bars matched red before the document, before the CSS,
and after load. The same test checks a white page on the same host and changing hosts.
The existing `browserFrameFollowsWebsiteThemeAndBackground` regression also passed.
These are emulator checks; physical Xiaomi behavior still needs confirmation.
