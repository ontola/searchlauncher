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
