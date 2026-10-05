package com.searchlauncher.app.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal data class ThemePreferences(
  val color: Int,
  val saturation: Float,
  val darkMode: Int,
  val oled: Boolean,
) {
  companion object {
    fun from(preferences: Preferences) =
      ThemePreferences(
        preferences[PreferencesKeys.THEME_COLOR] ?: 0xFF5E6D4E.toInt(),
        preferences[PreferencesKeys.THEME_SATURATION] ?: 50f,
        preferences[PreferencesKeys.DARK_MODE] ?: 0,
        preferences[PreferencesKeys.OLED_MODE] ?: false,
      )
  }
}

/** Share one complete appearance snapshot across activities, including newly opened overlays. */
private object ThemePreferenceState {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var state: StateFlow<ThemePreferences?>? = null

  fun get(context: Context): StateFlow<ThemePreferences?> =
    state
      ?: context.applicationContext.dataStore.data
        .map(ThemePreferences::from)
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)
        .also { state = it }
}

/**
 * Null only on a cold process start: do not draw or start entrance animations with a fake theme.
 */
@Composable
internal fun rememberThemePreferences(): ThemePreferences? {
  val context = LocalContext.current
  val preferences by remember { ThemePreferenceState.get(context) }.collectAsState()
  return preferences
}
