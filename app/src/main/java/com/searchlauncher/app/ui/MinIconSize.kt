package com.searchlauncher.app.ui

import android.content.Context
import com.searchlauncher.app.data.Prefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The minimum search-result icon size, in dp.
 *
 * DataStore ([PreferencesKeys.MIN_ICON_SIZE]) is the source of truth, but the value is mirrored to
 * a synchronous SharedPreferences cache ([Prefs.Launcher.MIN_ICON_SIZE]) so the first frame after a
 * cold start can size icons immediately, rather than rendering at the default and snapping once the
 * async DataStore read completes.
 */
object MinIconSize {

  /** Source-of-truth flow from DataStore, falling back to the device default. */
  fun flow(context: Context): Flow<Int> =
    context.dataStore.data.map {
      it[PreferencesKeys.MIN_ICON_SIZE] ?: PreferencesKeys.getDefaultIconSize(context)
    }

  /** Synchronously readable boot cache, used to seed the initial UI state before [flow] emits. */
  fun cached(context: Context): Int =
    context
      .getSharedPreferences(Prefs.Launcher.FILE, Context.MODE_PRIVATE)
      .getInt(Prefs.Launcher.MIN_ICON_SIZE, PreferencesKeys.getDefaultIconSize(context))

  /** Refreshes the boot cache so the next cold start can render at [size] without waiting. */
  fun updateCache(context: Context, size: Int) {
    context
      .getSharedPreferences(Prefs.Launcher.FILE, Context.MODE_PRIVATE)
      .edit()
      .putInt(Prefs.Launcher.MIN_ICON_SIZE, size)
      .apply()
  }
}
