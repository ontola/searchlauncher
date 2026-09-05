package com.searchlauncher.app.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.searchlauncher.app.data.Prefs
import kotlinx.coroutines.flow.map

/** Synchronous boot cache prevents a different keyboard flashing before DataStore loads. */
object HomeKeyboardPreference {
  private const val CACHE_KEY = "built_in_keyboard"

  fun cached(context: Context): Boolean =
    context
      .getSharedPreferences(Prefs.Launcher.FILE, Context.MODE_PRIVATE)
      .getBoolean(CACHE_KEY, true)

  fun flow(context: Context) =
    context.dataStore.data.map {
      val enabled = it[PreferencesKeys.BUILT_IN_KEYBOARD] ?: true
      cache(context, enabled)
      enabled
    }

  suspend fun set(context: Context, enabled: Boolean) {
    context.dataStore.edit { it[PreferencesKeys.BUILT_IN_KEYBOARD] = enabled }
    cache(context, enabled)
  }

  private fun cache(context: Context, enabled: Boolean) {
    context
      .getSharedPreferences(Prefs.Launcher.FILE, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(CACHE_KEY, enabled)
      .apply()
  }
}
