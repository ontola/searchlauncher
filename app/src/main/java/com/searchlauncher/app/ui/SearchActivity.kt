package com.searchlauncher.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.ui.browser.BrowserActivity

class SearchActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Make window transparent
    window.setBackgroundDrawableResource(android.R.color.transparent)
    window.setFlags(
      android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    )

    window.setSoftInputMode(
      android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
        android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
    )

    setContent {
      val context = LocalContext.current
      val query = remember { mutableStateOf(intent.getStringExtra(EXTRA_INITIAL_QUERY).orEmpty()) }
      val browserSearchMode = intent.getBooleanExtra(EXTRA_BROWSER_SEARCH, false)
      val chromeBarColor =
        intent
          .getIntExtra(EXTRA_CHROME_COLOR, 0)
          .takeIf { it != 0 }
          ?.let { androidx.compose.ui.graphics.Color(it) }

      SearchScreen(
        query = query.value,
        onQueryChange = { query.value = it },
        onDismiss = { finish() },
        onOpenSettings = {
          val intent =
            Intent(this, MainActivity::class.java).apply {
              putExtra("open_settings", true)
              flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
          startActivity(intent)
          finish()
        },
        onOpenAppDrawer = {},
        searchRepository = (application as SearchLauncherApp).searchRepository,
        focusTrigger = 0L,
        showBackgroundImage = false,
        privateWebResults = intent.getBooleanExtra(EXTRA_PRIVATE_WEB_RESULTS, false),
        startVoiceSearchOnOpen = intent.getBooleanExtra(EXTRA_START_VOICE_SEARCH, false),
        fixedHint = if (browserSearchMode) "Search anything…" else null,
        onOpenBrowserContext =
          if (browserSearchMode) {
            {
              sendBroadcast(
                Intent(BrowserActivity.ACTION_SHOW_BROWSER_MENU).setPackage(packageName)
              )
              finish()
            }
          } else null,
        chromeBarColor = chromeBarColor,
      )
    }
  }

  companion object {
    const val EXTRA_PRIVATE_WEB_RESULTS = "private_web_results"
    const val EXTRA_START_VOICE_SEARCH = "start_voice_search"
    const val EXTRA_BROWSER_SEARCH = "browser_search"
    const val EXTRA_CHROME_COLOR = "chrome_color"
    const val EXTRA_INITIAL_QUERY = "initial_query"
  }
}
