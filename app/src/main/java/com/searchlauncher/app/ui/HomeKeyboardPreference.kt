package com.searchlauncher.app.ui

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.searchlauncher.app.data.Prefs
import kotlinx.coroutines.flow.map

/**
 * A DataStore boolean mirrored into SharedPreferences, so the first frame can read it
 * synchronously. Keyboard choices change the layout's height, and DataStore loads a frame late.
 */
open class CachedKeyboardPreference(
  private val key: Preferences.Key<Boolean>,
  private val cacheKey: String,
  private val default: Boolean,
) {
  fun cached(context: Context): Boolean =
    context
      .getSharedPreferences(Prefs.Launcher.FILE, Context.MODE_PRIVATE)
      .getBoolean(cacheKey, default)

  fun flow(context: Context) =
    context.dataStore.data.map {
      val enabled = it[key] ?: default
      cache(context, enabled)
      enabled
    }

  suspend fun set(context: Context, enabled: Boolean) {
    context.dataStore.edit { it[key] = enabled }
    cache(context, enabled)
  }

  private fun cache(context: Context, enabled: Boolean) {
    context
      .getSharedPreferences(Prefs.Launcher.FILE, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(cacheKey, enabled)
      .apply()
  }
}

/** Synchronous boot cache prevents a different keyboard flashing before DataStore loads. */
object HomeKeyboardPreference :
  CachedKeyboardPreference(PreferencesKeys.BUILT_IN_KEYBOARD, "built_in_keyboard", true)

/** Number row above the letters of the built-in keyboard. It makes the keyboard taller. */
object KeyboardNumberRowPreference :
  CachedKeyboardPreference(PreferencesKeys.KEYBOARD_NUMBER_ROW, "keyboard_number_row", false)
