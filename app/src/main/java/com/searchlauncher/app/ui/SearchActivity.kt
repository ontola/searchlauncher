package com.searchlauncher.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.searchlauncher.app.SearchLauncherApp
import kotlinx.coroutines.flow.map

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
      val query = remember { mutableStateOf("") }

      val showHistory =
              remember {
                        context.dataStore.data.map {
                          it[
                                  androidx.datastore.preferences.core.booleanPreferencesKey(
                                          "show_history"
                                  )]
                                  ?: true
                        }
                      }
                      .collectAsState(initial = true)

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
              searchRepository = (application as SearchLauncherApp).searchRepository,
              focusTrigger = 0L,
              showHistory = showHistory.value,
              showBackgroundImage = false,
      )
    }
  }
}
