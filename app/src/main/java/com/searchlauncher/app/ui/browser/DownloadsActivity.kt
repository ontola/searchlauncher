package com.searchlauncher.app.ui.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.dataStore
import com.searchlauncher.app.ui.theme.SearchLauncherTheme

class DownloadsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (android.os.Build.VERSION.SDK_INT >= 34) {
      overrideActivityTransition(
        OVERRIDE_TRANSITION_OPEN,
        android.R.anim.fade_in,
        android.R.anim.fade_out,
      )
      overrideActivityTransition(
        OVERRIDE_TRANSITION_CLOSE,
        android.R.anim.fade_in,
        android.R.anim.fade_out,
      )
    } else {
      @Suppress("DEPRECATION")
      overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
    setContent {
      val prefs by dataStore.data.collectAsState(initial = null)
      SearchLauncherTheme(
        prefs?.get(PreferencesKeys.THEME_COLOR) ?: 0xFF5E6D4E.toInt(),
        prefs?.get(PreferencesKeys.DARK_MODE) ?: 0,
        prefs?.get(PreferencesKeys.THEME_SATURATION) ?: 50f,
        prefs?.get(PreferencesKeys.OLED_MODE) ?: false,
      ) {
        BrowserDownloadsScreen(onDismiss = ::finish)
      }
    }
  }

  override fun finish() {
    super.finish()
    if (android.os.Build.VERSION.SDK_INT < 34) {
      @Suppress("DEPRECATION")
      overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
  }
}
