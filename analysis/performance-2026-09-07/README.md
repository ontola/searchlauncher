# Search and startup follow-up

Changes:
- Restore the fast index snapshot before opening AppSearch and registering observers.
- Prepare fallback search actions independently of query execution, so emitting matches does not wait on a second repository call.
- Maintain the search-shortcut subset when publishing the index, instead of scanning every indexed document on every shortcut request.
- Convert cached app and search-shortcut results sequentially; these conversions are memory-only and do not benefit from a coroutine per result.
- Load the durable index at background priority and yield between pages while typing if a usable snapshot exists.

Validation: spotlessCheck, assembleDebug, SearchRepositoryTest, and SearchProgressiveResultsTest. Includes a regression assertion that publishing replacement shortcut documents immediately updates shortcut retrieval.

## Emulator observations

Phone_A35, Android 15, debug package. Three process-cold launches via `adb shell am start -S -W`, same emulator data. No data wipe; first launch after each install is included.

| Build | TotalTime samples (ms) |
| --- | --- |
| scroll13 baseline | 2771, 2243, 2142 |
| perf14 repository changes | 3046, 2065, 2132 |

This small noisy sample does not establish an overall launch improvement.

The captured final baseline repository initialization log reported search ready at 549 ms. The updated cached-search-ready log reported 68, 90, and 68 ms. These measure repository readiness, not first frame or physical finger-to-result latency. The later UI fallback prefetch change was build/unit-tested after these timing runs.

No physical phone was connected. No end-to-end first-letter latency or full index-rebuild speedup is claimed. The indexing changes aim to reduce competition with interaction, not shorten total rebuild duration.
