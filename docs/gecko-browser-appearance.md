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
