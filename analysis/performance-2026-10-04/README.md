# First search-result draw: 4 October 2026

## Finding and change

The search launched from a query-keyed `LaunchedEffect`, after Compose had processed the input's
composition/layout. The repository itself was normally quick. A persistent `MutableStateFlow`
now receives edits from the input callback and a background `collectLatest` starts the search
without waiting for that UI work. External query changes still enter through a `SideEffect`.
Results are published on Main with cancellation and current-query guards. Result animations
are unchanged. No artificial debounce was present or removed.

## Matched emulator comparison

Android 15 ARM64 `Phone_A35`, 1080 x 2400, 60 Hz. Baseline: a092264 (experimental 20) with the
same input/draw trace markers added. Candidate: the scheduling change on that baseline.
Both run `GeckoBrowserDeviceTest#firstResultLatencyAudit`: six key taps (`s`, `c`, `w`, repeated)
on home, then six in browser search. Each key is cleared after 500 ms, with another 500 ms before
the next input. Same emulator data and builds; these are small sequential runs, not a statistical
physical-device benchmark.

| Metric, first 12 query samples | Baseline median | Candidate median |
| --- | ---: | ---: |
| Input callback to repository search start | 25.72 ms | 0.74 ms |
| Repository search duration | 2.94 ms | 2.21 ms |
| Input callback to first top-result draw | 53.89 ms | 37.33 ms |

Worst first draws: 225.75 ms baseline, 193.60 ms candidate. Cold UI composition/measure/layout
still produces long frames; this change does not eliminate those costs. An earlier independent
comparison also improved the medians (55.90 to 40.51 ms).

`baseline.csv` and `candidate.csv` retain all traced nonempty queries. Only the first 12 rows form
the matched measurement; subsequent rows are a rapid `set` burst and can have no draw because
newer input cancels them. The candidate test passed, including rapid typing and external clearing.
The final baseline test completed the timed portion but failed its final no-Settings-result-after-
clear assertion. That failure has not been independently diagnosed and is not claimed as a separate
fixed defect.

The timing begins at the input callback, not physical touch-down, and ends at a draw callback,
not display presentation or the end of the entrance fade. The existing animations remain intact.
No Xiaomi was attached for hardware validation, and this comparison does not establish when the
historical slowdown began.

## Reproduce

Install the Gecko APK and its androidTest APK, then record:

```sh
adb -e shell perfetto --background -o /data/misc/perfetto-traces/search.perfetto-trace \
  -t 30s -b 64mb -a com.searchlauncher.app.gecko gfx view wm input
adb -e shell am instrument -w \
  -e class com.searchlauncher.app.ui.browser.GeckoBrowserDeviceTest#firstResultLatencyAudit \
  com.searchlauncher.app.gecko.test/androidx.test.runner.AndroidJUnitRunner
# Wait for the 30-second recorder to finish before pulling the file.
adb -e pull /data/misc/perfetto-traces/search.perfetto-trace
trace_processor -q analysis/performance-2026-10-04/latency.sql search.perfetto-trace
```

Full traces remain outside Git in `/tmp/search-{baseline,candidate}-final.perfetto-trace`.
The original 32 MB buffer with scheduler events overwrote early samples; those partial runs
are not used in the table.


## Further work: experimental 23

The keyboard previously created four independent color animation states for every key. It now
shares those four states for the whole keyboard and keeps the text-input callback current without
capturing a new callback in every letter key. Themed-icon resolution and conversion now remain
in one IO block; resolution already dispatched internally, so this removes an extra handoff rather
than fixing synchronous PackageManager I/O. Icon-loading trace spans now use async tracing because
they include suspension. Their earlier long synchronous spans were not evidence of blocking I/O.

Matched 12-query runs on the same emulator, without screen recording: first-draw median 38.23 ms
on build 22 versus 36.71 ms with shared keyboard animation state; worst samples 176.67 and
154.59 ms. These small runs show only a modest difference and cannot establish a general hardware
speedup. Raw samples are in `gecko22.csv` and `shared-keyboard.csv` (first 12 rows only).

Separately, the first result no longer fades in: its text is fully opaque on its first draw.
Other new rows retain a stagger, shortened from 180 ms + up to 64 ms delay to 100 ms + up to
48 ms delay. Placement and disappearance animations remain intact. This improves time to a
readable result without pretending the animation duration was repository search latency.

The final UI recording (`/tmp/search23-final.mp4`) was inspected for typing, clearing, and the
home/browser result transitions; its profiling run is not compared with non-recorded timings.
The clear assertion now waits for the accessibility text update and disappearing row instead of
assuming an asynchronous `setText` action has completed immediately. Earlier failures had no
remaining result in the subsequently captured screenshot/hierarchy.


## Optimized Gecko build: experimental 24

The shipped experimental build still inherited `debug`: no R8 optimization, resource shrinking,
or release-mode runtime. Gecko now defaults to non-debuggable, optimized builds, keeping its
existing package and signing identity for in-place upgrades. Android test core was accidentally
an application dependency; it is now confined to unit/instrumentation tests. Gecko's bundled
consumer rules preserve its JNI/reflection entry points. Shell profiling remains available.
This follows [Android's Compose performance guidance](https://developer.android.com/develop/ui/compose/performance).

The new `performance` module runs UIAutomator in its own process, against an unchanged installed
APK. Unlike the earlier combined home/browser audit, this comparison types twelve isolated
`s`, `c`, `w` queries on home only, clearing between queries. Same Android 15 ARM64 60 Hz
emulator and app data; no recording or build running during either measurement. Candidate was
measured first, then the previously shipped Gecko23 APK (0d335eb) was installed over it. Small
sequential samples, not a physical-device or statistically controlled 120 Hz benchmark.

| Metric | Shipped debug Gecko23 | Optimized candidate |
| --- | ---: | ---: |
| Input callback to first result draw, median (12 samples) | 39.45 ms | 20.01 ms |
| Input callback to first result draw, worst | 101.24 ms | 27.24 ms |
| Repository search, median | 1.98 ms | 0.95 ms |
| Main-thread frame duration, p95 during result transitions | 39.08 ms | 9.72 ms |
| Main-thread frame duration, worst during result transitions | 93.21 ms | 24.01 ms |

Frame duration here is the outer `Choreographer#doFrame` CPU slice in the first 300 ms after
each query (141 baseline / 151 optimized frames). Nested resync spans are excluded. It is not
GPU completion or display presentation. First-result timing starts after touch handling, at
the input callback, and ends at the draw callback, not at presentation. These isolated queries
start from an empty list, avoiding an old row's draw being mistaken for new query results.
See `gecko24-{debug,release}.csv`, corresponding `-frames.csv`, and `typing-frames.sql`.

The separate `continuousTyping` test touches keyboard keys about 100 ms apart to type
`settings`, `camera`, and `clock`, deletes each word, and repeats three times. Both APKs passed
all word/clear assertions. Overall gfxinfo jank was similar (roughly 6% in each run), so this
is evidence of faster query transitions, not a demonstrated elimination of sustained animation
jank. `gecko24-continuous.csv` retains the per-word process totals, without double-counting the
per-window totals repeated by gfxinfo. The emulator has few indexed apps and no realistic
contact corpus; physical Xiaomi measurements are still needed.

The optimized APK also passed external browser navigation, reload, and completed-download UI
checks. Internal instrumentation initially failed because R8 removes APIs only used by the
in-process test runner; the new performance harness avoids modifying the measured app. Existing
internal tests can use `-PgeckoDebug=true -PtestBuildType=gecko`; do not use that diagnostic APK
for performance claims or publishing. No animation timings or gesture delays changed in this build.

### Run the external tests

```sh
./gradlew assembleGecko :performance:assembleGecko
adb install -r app/build/outputs/apk/gecko/app-gecko.apk
adb install -r performance/build/outputs/apk/gecko/performance-gecko.apk
adb shell am instrument -w \
  com.searchlauncher.performance/androidx.test.runner.AndroidJUnitRunner
```

Complete onboarding and enable the built-in keyboard beforehand; leave animations enabled.
For timing, run just `TypingPerformanceTest#firstResultLatency` with a 22-second Perfetto
recording, using the same categories as above. Wait for Perfetto to finish before pulling its
file (an early pull can be empty). Raw traces and continuous gfxinfo dumps are in
`/tmp/search24-{debug,release}.perfetto-trace` and `/tmp/search24-{debug,release}-typing.txt`.
Do not run screen recording or builds concurrently with timing measurements.
