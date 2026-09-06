# Search performance — 2026-09-06

Measured the ranking code from release `dcdbbd6` (v0.0.32) and a local optimization to
`FuzzyMatch.typoFuzzyScore`. The optimization skips temporary candidate lists, repeated
single-word candidates, and compacted target strings whose lengths cannot match.

## Results

Host-JVM ranking only, on this Mac (Apple M5 Pro, OpenJDK 17.0.2). Two separate runs per
implementation. Each cell below spans the two runs. MB means decimal megabytes.

| Synthetic index | Query | Before p50 ms | After p50 ms | Before MB/query | After MB/query |
| --- | --- | --- | --- | --- | --- |
| 10,000 | `set` | 0.73–0.80 | 0.51–0.57 | 3.20 | 1.23 |
| 10,000 | `settings` | 1.56–1.70 | 1.01–1.02 | 4.54 | 2.71 |
| 10,000 | `soptify` | 1.69–1.90 | 1.07–1.08 | 4.96 | 2.94 |
| 10,000 | `jane` | 0.76–0.78 | 0.46–0.48 | 3.49 | 1.51 |
| 10,000 | `qzx7` (no matches) | 0.74–0.80 | 0.46–0.46 | 3.67 | 1.58 |
| 1,000 | `soptify` | 0.19–0.22 | 0.14–0.15 | 0.52 | 0.32 |

Allocations fell 40–62% for the 10,000-entry text queries above. Timing improvements
were consistent across the repeat runs, but these are microbenchmark observations,
not statistically established Android speedups. The one-character `s` path does not
use typo matching and shows unchanged allocations; its small timing variation is noise.
Raw p50, p95, allocations, result counts, and checksums are in the adjacent four CSVs.

## Method and limits

- Opt-in `SearchRankingBenchmarkTest`: calls the production `SearchRanker.rankCandidates`.
- Deterministic 1,000- and 10,000-entry synthetic indexes: 17 repeated names with numeric
  suffixes, mixing app and contact namespaces; no actual personal contact data.
- Seven fixed queries: one character, prefix, full word, typo, contact name, phone fragment,
  and no match. Empty usage maps; this does not measure large learned-usage histories.
- 100 warmup calls, then 100 timed calls per query/index combination. p50 and p95 are
  ordered samples 50 and 95. Allocation bytes use the host thread allocation counter;
  they include minor result-checksum overhead outside the timed ranking call.
- Construction/index loading excluded. No network, icons, Compose, startup, garbage
  collection pause attribution, or key-to-visible-result latency measured.
- Ranking currently stops after 1,001 candidates. The broad `s` and phone queries hit
  this cap at 10,000 entries and do **not** scan the whole index. Do not compare their
  scaling with full-scan queries. No ranking-cap behavior was changed.
- This is a lightweight JUnit timing harness, not JMH or an Android benchmark. The
  first run per process is more sensitive to JIT warmup, especially the smaller index.
- No physical device was connected. The available Pixel emulator stayed offline with
  unavailable HVF and memory-protection errors; it was stopped. No emulator timing is
  presented as device performance.

## Correctness checks

All benchmark result counts and score/ID checksums match before/after. A temporary
differential test also compared 50,000 seeded generated query/target pairs against the
original scorer from `dcdbbd6`, including spaces, digits, accented characters and long
names: zero score differences. The temporary copy of the old implementation was removed.
Permanent regression cases cover compact names, repeated spaces, and long targets.
The full debug/release unit suites, `spotlessCheck`, and `assembleDebug` passed after
restoring the optimized implementation.

## Reproduce

From the repository root, using the project's JDK 17:

```sh
SEARCHLAUNCHER_BENCHMARK=1 ./gradlew testDebugUnitTest \
  --tests com.searchlauncher.app.data.SearchRankingBenchmarkTest \
  --rerun-tasks
```

Read `BENCH` lines from
`app/build/test-results/testDebugUnitTest/TEST-com.searchlauncher.app.data.SearchRankingBenchmarkTest.xml`.
Without the environment variable the benchmark is skipped in normal unit-test runs.
No timing assertions are enforced in CI. To reproduce the baseline, use the same
benchmark harness against `dcdbbd6` in a separate checkout.

## Initial measurement queue (follow-up results below)

These are source-inspection candidates, not measured bottlenecks:

1. **Key-to-result latency with suggestions enabled.** `searchApps` waits for HTTP
   suggestions before returning local results. Connect and read timeouts are each
   configured at 500 ms. Measure slow-network typing and cancellation before deciding
   whether to publish local results before fetching suggestions.
2. **Shortcut conversion during typing.** `getSearchShortcuts(limit)` scans the whole
   snapshot and converts/ranks all shortcut entries before applying `take(limit)`.
   Measure conversion counts, icon/disk work and latency with many custom shortcuts.
3. **Correct coroutine traces before collecting UI timings.** Current synchronous
   `Trace.beginSection/endSection` wrappers surround some suspending calls. They can
   cross threads or encompass unrelated work during suspension. Use async trace sections
   for suspending spans and retain thread slices only around synchronous work.
4. **Real-device baseline.** Measure repeated process-cold and warm home launches,
   typing through common app/contact queries with the built-in keyboard, result scrolling,
   and return from a browser tab. Capture frame deadlines, input-to-results latency,
   and allocations/GC in a release-like build, with a stable index and documented device
   temperature, refresh rate, and background workload. Recheck this ranking optimization
   there before claiming a user-visible responsiveness improvement.


## Iteration 2 — progressive results and bounded shortcut conversion

A controlled host/Robolectric experiment confirmed two costs and tested their fixes.
These numbers measure repository delivery, not Android input-to-pixel latency.

| Scenario | Before | After (two runs) |
| --- | --- | --- |
| First local result, server deliberately waits 300 ms | 322.2 ms | 4.8–6.6 ms |
| Final result including suggestions, same server | 322.2 ms | 326.1–328.3 ms |
| 3 shortcuts from 20, warm p50 | 0.444 ms | 0.180–0.269 ms |
| 3 shortcuts from 200, warm p50 | 1.141 ms | 0.274–0.313 ms |

The network itself is unchanged. `searchAppUpdates` publishes a sorted, independent
snapshot of local results before the HTTP request completes, then publishes the merged
list only if it changed. `SearchScreen` collects these updates inside its existing
query-keyed effect. The existing one-shot `searchApps` API keeps returning the complete
result list. Cancellation propagates through the flow, so an obsolete query cannot
publish late suggestions to its cancelled collector. The existing blocking HTTP call
can still occupy an IO thread until it returns or times out; this iteration does not
claim immediate network cancellation or debounce requests.

`getSearchShortcuts` now limits the sorted candidates **before** creating result objects
and loading icons, for both indexed and fallback shortcuts. It still scans/ranks the
available shortcuts. A regression test instruments the conversion factory to require
exactly three conversions for a limit of three, and zero for a limit of zero. Returned
ordering still prefers the existing Google/Play Store defaults.

Synchronous thread trace spans around suspending search operations were replaced with
async spans carrying distinct cookies and ending in `finally`. Synchronous scoring and
UI merge spans remain thread slices. No on-device trace has yet been captured with the
corrected instrumentation.

### Measurement method

`SearchPipelineBenchmarkTest` is opt-in using the same `SEARCHLAUNCHER_BENCHMARK=1`
environment flag. The shortcut measurement builds 20 or 200 synthetic indexed engines,
warms 20 calls and measures 40 calls requesting three results. It uses production
conversion with Robolectric's Android graphics behavior; it is not an Android icon
rendering benchmark. The network experiment uses a loopback HTTP server, a custom
shortcut, a warm local search, and one timed request per run. The server waits 300 ms
before responding with one suggestion. No external service or user query data is used.
The after measurement includes the progressive-flow startup overhead. Raw results are
in `pipeline-before.txt`, `pipeline-after.txt`, and `pipeline-after-repeat.txt`.

```sh
SEARCHLAUNCHER_BENCHMARK=1 ./gradlew testDebugUnitTest \
  --tests com.searchlauncher.app.data.SearchPipelineBenchmarkTest --rerun-tasks
```

Permanent regression tests gate the server response on delivery of the first local
update, then verify suggestion merging and ordering, unchanged first snapshots, HTTP
failure fallback, cancellation, disabled suggestions, and empty queries. They assert
behavior rather than millisecond thresholds.

The remaining measurement priority is physical-device startup, typing and frame pacing.
ADB reported no connected device during this work. These changes are included in
v0.0.33; no physical-device validation was performed during these measurements.

Validation after iteration 2: full debug and release unit suites, `spotlessCheck`, and
`assembleDebug` passed. An earlier full run had a transient `IndexingDispatchersTest`
priority assertion; it passed both in the focused rerun and the subsequent complete
suite. No production dispatcher behavior was changed.
