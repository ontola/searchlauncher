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

Status: done

- Inventory SharedPreferences and DataStore usage. Done (see map below).
- Centralize ad hoc preference file/key names into one registry (`Prefs`). Done.
- Decide which state belongs in DataStore, cache files, or migration-only SharedPreferences. Done
  (see ownership principle and resolved decisions below — no data relocation needed).

Expected benefit:

- Easier backup/restore and reset behavior.
- Less mystery around which file owns which user-visible setting.

#### Storage inventory (2026-06-02)

DataStore (async, user-facing settings):

- `settings` — all `PreferencesKeys` (theme, dark/oled mode, wallpaper uri, show widgets, first run,
  store web history, history limit, min icon size, auto-theme, search shortcuts enabled).
- `onboarding` — separate store owned by `OnboardingManager`.

SharedPreferences (now all named via `Prefs`):

| File | Owner | Keys |
| --- | --- | --- |
| `privacy_prefs` | `SearchLauncherApp` | `consent_granted`, `asked_default_launcher` |
| `active_search` | `MainActivity` | `active_query`, `active_query_time` |
| `search_launcher_prefs` | `SearchScreen` + `SearchRepository` | `min_icon_size` (boot cache), `observed_history_limit`, `last_reindex_timestamp` |
| `window_prefs` | `SearchScreen` | `keyboard_height` |
| `favorites` | `FavoritesRepository` | `favorite_ids_ordered`, `favorite_ids` (legacy set) |
| `search_shortcuts` | `SearchShortcutRepository` | `shortcuts` (JSON) |
| `quick_copy` | `SnippetsRepository` | `items` (JSON) |
| `history` | `HistoryRepository` | `history_ids` (JSON) |
| `contact_chat_actions` | `ContactActionsRepository` | per-contact package (keys are contact ids) |

#### Ownership principle

DataStore (`settings`) owns durable, user-facing settings that should survive backup/restore.
SharedPreferences owns device-local, internal, transient, or synchronously-read state. Reviewed
against this principle, every current store is already in the right place — so Phase 4 is about
making the split legible, not relocating data.

#### Resolved decisions

- `min_icon_size` dual storage: KEEP. DataStore is the source of truth; the `search_launcher_prefs`
  copy is a synchronous boot cache that prevents real icon flicker on cold start. Encapsulated into
  `MinIconSize` (`flow` / `cached` / `updateCache`) so the read/write/default logic lives in one
  named place instead of inline in `SearchScreen`.
- `observed_history_limit`, `last_reindex_timestamp`: STAY in SharedPreferences.
  `observed_history_limit` is read synchronously on the cold-start hot path (to size the initial
  history frame without jumpiness); `last_reindex_timestamp` is device-local reindex-throttle state.
  Neither is a user setting, so moving them to async DataStore would add complexity for no benefit.
- Backup exclusions (`privacy_prefs`, `window_prefs`, `active_search`, `search_launcher_prefs`):
  INTENTIONAL. Privacy consent should re-prompt on a new device, `active_query` is transient, and the
  rest are device-specific. `min_icon_size`'s authoritative copy already rides along in the
  backed-up DataStore.

Phase 4 is effectively complete: storage is inventoried, named via `Prefs`, the ownership principle
is documented, and the one real redundancy (`min_icon_size`) is encapsulated.

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
- 2026-06-02: Phase 4 inventory complete. Centralized all SharedPreferences file/key names into a
  single `Prefs` registry (byte-identical string values, no data migration). Updated 9 call sites
  across `SearchLauncherApp`, `MainActivity`, `SearchScreen`, `SearchRepository`,
  `ContactActionsRepository`, and the Favorites/Snippets/SearchShortcut/History repositories.
  Behavior preserved; `./gradlew testDebugUnitTest assembleDebug` passes. Store-migration decisions
  deferred (see Phase 4 open decisions).
- 2026-06-02: Phase 4 complete. Reviewed each store against an ownership principle (DataStore =
  durable user settings; SharedPreferences = device-local/internal/transient) and confirmed no data
  needs relocating. Encapsulated the `min_icon_size` DataStore-plus-boot-cache pattern into
  `MinIconSize`, removing the duplicated default logic and inline SharedPreferences plumbing from
  `SearchScreen`. Behavior preserved; `./gradlew testDebugUnitTest assembleDebug` passes.
