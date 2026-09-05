package com.searchlauncher.app.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import com.searchlauncher.app.data.Prefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Home-screen portrait lock.
 *
 * DataStore ([PreferencesKeys.LOCK_PORTRAIT]) is the source of truth, but the value is mirrored to
 * a synchronous SharedPreferences cache ([Prefs.Launcher.LOCK_PORTRAIT]) so [MainActivity] can set
 * [Activity.requestedOrientation] on a cold start before the async DataStore read completes.
 * Without that cache the launcher would follow auto-rotate for a frame and then snap.
 */
object PortraitLock {

  /** Source-of-truth flow from DataStore. Off by default. */
  fun flow(context: Context): Flow<Boolean> =
    context.dataStore.data.map { it[PreferencesKeys.LOCK_PORTRAIT] ?: false }

  /** Synchronously readable boot cache, used before [flow] emits. */
  fun cached(context: Context): Boolean =
    context
      .getSharedPreferences(Prefs.Launcher.FILE, Context.MODE_PRIVATE)
      .getBoolean(Prefs.Launcher.LOCK_PORTRAIT, false)

  /** Refreshes the boot cache so the next cold start can lock orientation without waiting. */
  fun updateCache(context: Context, locked: Boolean) {
    context
      .getSharedPreferences(Prefs.Launcher.FILE, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(Prefs.Launcher.LOCK_PORTRAIT, locked)
      .apply()
  }

  /**
   * Sets [activity] to portrait when [locked], or back to the system default when not. Skipped
   * while the activity is in picture-in-picture, where a requested-orientation change can kick the
   * window out of PiP.
   */
  fun apply(activity: Activity, locked: Boolean) {
    if (activity.isInPictureInPictureMode) return
    val orientation =
      if (locked) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    if (activity.requestedOrientation != orientation) {
      activity.requestedOrientation = orientation
    }
  }
}
