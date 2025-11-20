package com.searchlauncher.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.ui.theme.SearchLauncherTheme
import kotlinx.coroutines.flow.map

class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        setContent {
            val context = LocalContext.current
            val query = remember { mutableStateOf("") }

            val showHistory =
                context.dataStore
                    .data
                    .map {
                        it[
                            androidx.datastore.preferences.core.booleanPreferencesKey(
                                "show_history"
                            )]
                            ?: true
                    }
                    .collectAsState(initial = true)

            SearchLauncherTheme {
                SearchScreen(
                    query = query.value,
                    onQueryChange = { query.value = it },
                    onDismiss = { finish() },
                    onOpenSettings = {
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                    },
                    searchRepository = (application as SearchLauncherApp).searchRepository,
                    focusTrigger = 0L,
                    showHistory = showHistory.value
                )
            }
        }
    }
}

private val Context.dataStore:
        androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> by
androidx.datastore.preferences.preferencesDataStore(name = "settings")
