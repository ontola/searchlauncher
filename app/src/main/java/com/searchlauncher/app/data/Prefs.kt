package com.searchlauncher.app.data

/**
 * Central registry of the launcher's `SharedPreferences` files and their keys.
 *
 * Async, user-facing settings live in DataStore (see [com.searchlauncher.app.ui.PreferencesKeys]);
 * this object covers the remaining `SharedPreferences`-backed state — per-store, so it's clear
 * which file owns which value.
 *
 * IMPORTANT: every string here names data persisted on users' devices. Changing a value orphans
 * existing data, so treat these constants as immutable; add new ones rather than renaming.
 */
object Prefs {

  /** Privacy/consent flags, written before any DataStore access. */
  object Privacy {
    const val FILE = "privacy_prefs"
    const val CONSENT_GRANTED = "consent_granted"
    const val ASKED_DEFAULT_LAUNCHER = "asked_default_launcher"
  }

  /** The in-flight query, persisted so it can be restored across short app exits. */
  object ActiveSearch {
    const val FILE = "active_search"
    const val QUERY = "active_query"
    const val QUERY_TIME = "active_query_time"
  }

  /**
   * Miscellaneous launcher state.
   *
   * Note: [MIN_ICON_SIZE] mirrors the DataStore setting of the same name; it is kept here only as a
   * synchronous boot cache so the first frame can size icons without waiting on DataStore.
   */
  object Launcher {
    const val FILE = "search_launcher_prefs"
    const val MIN_ICON_SIZE = "min_icon_size"
    const val OBSERVED_HISTORY_LIMIT = "observed_history_limit"
    const val LAST_REINDEX_TIMESTAMP = "last_reindex_timestamp"
  }

  /** Measured soft-keyboard height, cached to avoid layout jump on the next launch. */
  object Window {
    const val FILE = "window_prefs"
    const val KEYBOARD_HEIGHT = "keyboard_height"
  }

  /** Pinned favorites (ordered list plus a legacy unordered set). */
  object Favorites {
    const val FILE = "favorites"
    const val IDS_ORDERED = "favorite_ids_ordered"
    const val IDS = "favorite_ids"
  }

  /** User-defined search shortcuts, stored as a JSON array. */
  object SearchShortcuts {
    const val FILE = "search_shortcuts"
    const val SHORTCUTS = "shortcuts"
  }

  /** Quick-copy text snippets, stored as a JSON array. */
  object Snippets {
    const val FILE = "quick_copy"
    const val ITEMS = "items"
  }

  /** Recently used result ids, stored as a JSON array. */
  object History {
    const val FILE = "history"
    const val IDS = "history_ids"
  }

  /** Last-used chat app per contact (keys are contact ids, so no fixed key names). */
  object ContactActions {
    const val FILE = "contact_chat_actions"
  }
}
