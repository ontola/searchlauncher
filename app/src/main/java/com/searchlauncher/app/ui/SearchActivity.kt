package com.searchlauncher.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.searchlauncher.app.SearchLauncherApp

class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        val app = application as SearchLauncherApp

        setContent {
            var query by remember { mutableStateOf("") }
            SearchScreen(
                    query = query,
                    onQueryChange = { query = it },
                    onDismiss = { finish() },
                    onOpenSettings = {
                        val intent = android.content.Intent(this, MainActivity::class.java)
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    },
                    searchRepository = app.searchRepository
            )
        }
    }
}
