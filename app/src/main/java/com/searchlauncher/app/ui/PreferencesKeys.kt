package com.searchlauncher.app.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val android.content.Context.dataStore: DataStore<Preferences> by
  preferencesDataStore(name = "settings")

object PreferencesKeys {
  val THEME_COLOR = intPreferencesKey("theme_color")
  val THEME_SATURATION = floatPreferencesKey("theme_saturation")
  val DARK_MODE = intPreferencesKey("dark_mode")
  val OLED_MODE = booleanPreferencesKey("oled_mode")
  val BACKGROUND_LAST_IMAGE_URI = stringPreferencesKey("background_last_image_uri")
  val SHOW_WIDGETS = booleanPreferencesKey("show_widgets")
  val IS_FIRST_RUN = booleanPreferencesKey("is_first_run")
  val STORE_WEB_HISTORY = booleanPreferencesKey("store_web_history")
  val BROWSER_SHOW_FAVORITES = booleanPreferencesKey("browser_show_favorites")
  val HISTORY_LIMIT = intPreferencesKey("history_limit")
  val MIN_ICON_SIZE = intPreferencesKey("min_icon_size")
  /**
   * How many rows the favorites bar may wrap onto. `-1` is Auto (grow as needed, up to four);
   * `1`–`4` cap growth at that many rows. See
   * [com.searchlauncher.app.ui.components.FAVORITES_MAX_ROWS_AUTO].
   */
  val FAVORITES_MAX_ROWS = intPreferencesKey("favorites_max_rows")
  val AUTO_THEME_FROM_WALLPAPER = booleanPreferencesKey("auto_theme_from_wallpaper")
  val THEMED_ICONS = booleanPreferencesKey("themed_icons")
  val SEARCH_SHORTCUTS_ENABLED = booleanPreferencesKey("search_shortcuts_enabled")

  /**
   * Whether the keyboard may autocorrect what is typed into the search bar. Off by default: a query
   * is usually a name, a command or a URL fragment, and having it silently rewritten into a
   * dictionary word is worse than a typo the user can see.
   */
  val SEARCH_AUTOCORRECT = booleanPreferencesKey("search_autocorrect")

  /** Id of the [com.searchlauncher.app.data.SearchShortcut] used by the global search button. */
  val DEFAULT_SEARCH_ENGINE = stringPreferencesKey("default_search_engine")

  val AD_BLOCK_ENABLED = booleanPreferencesKey("ad_block_enabled")

  /**
   * Set once the opening offer of the optional permissions has been made, whatever the answer was.
   * Declining is an answer, so the offer is not repeated on every launch that finds them missing.
   */
  val ONBOARDING_PERMISSIONS_ASKED = booleanPreferencesKey("onboarding_permissions_asked")

  fun getDefaultIconSize(context: android.content.Context): Int {
    val config = context.resources.configuration
    return if (config.smallestScreenWidthDp >= 600) 48 else 32
  }
}
