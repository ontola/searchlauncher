# SearchLauncher Simplification Plan

## Goal

Simplify the codebase and make it easier to understand without changing launcher behavior.

Primary measures:

- Reduce very large files by extracting cohesive modules.
- Remove duplicated launch/menu/action logic.
- Make storage and action handling easier to reason about.
- Keep behavior covered by `./gradlew testDebugUnitTest assembleDebug`.

## Baseline

Captured: 2026-06-02

Kotlin LOC:

- Total: 14,541
- `app/src/main/java/com/searchlauncher/app/data/SearchRepository.kt`: 3,164
- `app/src/main/java/com/searchlauncher/app/ui/SearchScreen.kt`: 1,606
- `app/src/main/java/com/searchlauncher/app/ui/SettingsScreen.kt`: 1,115
- `app/src/main/java/com/searchlauncher/app/ui/MainActivity.kt`: 1,064
- `app/src/main/java/com/searchlauncher/app/ui/components/SearchResultItem.kt`: 478
- `app/src/main/java/com/searchlauncher/app/ui/components/FavoritesRow.kt`: 393

Baseline command:

```sh
find app/src/main/java app/src/test/java -name '*.kt' -print0 | xargs -0 wc -l
```

## Principles

- Prefer behavior-preserving extraction over rewrites.
- Keep one change small enough to verify independently.
- Move code toward named concepts: launchers, actions, indexers, ranking, icon storage.
- Avoid reducing LOC by hiding logic in vague abstractions.

## Work Plan

### Phase 1: Remove duplicated UI/action code

Status: in progress

- Extract shared app actions menu from `SearchResultItem` and `FavoritesRow`. Done.
- Extract result launching from `SearchScreen` and `MainActivity`. Done.
- Make result rows delegate actions instead of knowing repositories directly where practical.

Expected benefit:

- Fewer divergent App Info/favorite/uninstall implementations.
- One place to add future app context actions.
- Lower complexity in `SearchScreen`.

### Phase 2: Extract contact actions

Status: in progress

- Move contact chat/SMS/call/email discovery and launch code out of `SearchRepository`. Done.
- Keep `SearchRepository` focused on search/index results.
- Add focused tests for Telegram MIME detection, phone fallback, SMS, email, and last-used ordering.

Expected benefit:

- Contact action behavior becomes easier to inspect and extend.
- Search indexing stops absorbing unrelated UI action responsibilities.

### Phase 3: Split search/index responsibilities

Status: in progress

- Extract indexers: apps, shortcuts, contacts, snippets. Done.
- Extract ranking and learned-query scoring. Done.
- Extract `SearchableDocument -> SearchResult` conversion. Done.
- Extract icon cache/disk persistence. Done.

Expected benefit:

- `SearchRepository.kt` becomes orchestration rather than the whole search subsystem.
- Ranking/indexing/icon changes become independently testable.

### Phase 4: Normalize storage

Status: pending

- Inventory SharedPreferences and DataStore usage.
- Decide which state belongs in DataStore, cache files, or migration-only SharedPreferences.
- Remove ad hoc preference names where possible.

Expected benefit:

- Easier backup/restore and reset behavior.
- Less mystery around which file owns which user-visible setting.

## Change Log

- 2026-06-02: Created plan and LOC baseline.
- 2026-06-02: Extracted shared `AppActionsMenuItems` from `SearchResultItem` and `FavoritesRow`.
  Kotlin LOC now 14,518. `SearchResultItem.kt` is 399 lines, `FavoritesRow.kt` is 365 lines.
- 2026-06-02: Extracted `ContactActionsRepository` from `SearchRepository`.
  Kotlin LOC now 14,510. `SearchRepository.kt` is 2,887 lines.
- 2026-06-02: Extracted shared `ResultLauncher` from `SearchScreen` and `MainActivity`.
  Kotlin LOC now 14,509. `SearchScreen.kt` is 1,427 lines, `MainActivity.kt` is 1,036 lines.
- 2026-06-02: Extracted `IconRepository` from `SearchRepository`.
  Kotlin LOC now 14,531. `SearchRepository.kt` is 2,801 lines.
- 2026-06-02: Extracted `SearchRanker` from `SearchRepository`.
  Kotlin LOC now 14,581. `SearchRepository.kt` is 2,637 lines.
- 2026-06-02: Extracted `SearchResultFactory` from `SearchRepository`.
  Kotlin LOC now 14,633. `SearchRepository.kt` is 2,301 lines, `SearchResultFactory.kt` is 409 lines.
- 2026-06-02: Extracted `AppIndexer`, `ShortcutIndexer` (dynamic/static/custom), `ContactIndexer`,
  and `SnippetIndexer` from `SearchRepository`. The repo keeps AppSearch persistence and
  orchestration; indexers are pure document builders (icon-caching side effects retained). Behavior
  preserved; `./gradlew testDebugUnitTest assembleDebug` passes. Kotlin LOC now 14,747.
  `SearchRepository.kt` is 1,968 lines. This completes the Phase 3 indexer split.
