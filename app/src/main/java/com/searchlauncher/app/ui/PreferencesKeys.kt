package com.searchlauncher.app.ui

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferencesKeys {
  val THEME_COLOR = intPreferencesKey("theme_color")
  val THEME_SATURATION = floatPreferencesKey("theme_saturation")
  val DARK_MODE = intPreferencesKey("dark_mode")
  val OLED_MODE = booleanPreferencesKey("oled_mode")
  val BACKGROUND_LAST_IMAGE_URI = stringPreferencesKey("background_last_image_uri")
  val SWIPE_GESTURE_ENABLED = booleanPreferencesKey("swipe_gesture_enabled")
  val SHOW_WIDGETS = booleanPreferencesKey("show_widgets")
  val IS_FIRST_RUN = booleanPreferencesKey("is_first_run")
  val STORE_WEB_HISTORY = booleanPreferencesKey("store_web_history")
  val HISTORY_LIMIT = intPreferencesKey("history_limit")
  val MIN_ICON_SIZE = intPreferencesKey("min_icon_size")
}
