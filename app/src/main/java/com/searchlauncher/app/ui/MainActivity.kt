package com.searchlauncher.app.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.searchlauncher.app.service.OverlayService
import com.searchlauncher.app.ui.theme.SearchLauncherTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SearchLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) { MainScreen() }
            }
        }
    }

    @Composable
    private fun MainScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var showPractice by remember { mutableStateOf(false) }

        val onboardingComplete by
        context.dataStore
            .data
            .map { preferences ->
                preferences[PreferencesKeys.ONBOARDING_COMPLETE] ?: false
            }
            .collectAsState(initial = false)

        if (showPractice) {
            PracticeGestureScreen(onBack = { showPractice = false })
        } else if (!onboardingComplete) {
            OnboardingScreen(
                onComplete = {
                    scope.launch {
                        context.dataStore.edit { preferences ->
                            preferences[PreferencesKeys.ONBOARDING_COMPLETE] = true
                        }
                        startOverlayService()
                    }
                }
            )
        } else {
            // Auto-start service if permissions are granted
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    Settings.canDrawOverlays(context)
                ) {
                    startOverlayService()
                }
            }

            HomeScreen(
                onStartService = { startOverlayService() },
                onStopService = { stopOverlayService() },
                onOpenPractice = { showPractice = true }
            )
        }
    }

    private fun startOverlayService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                val intent = Intent(this, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        }
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
    }

    private object PreferencesKeys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }
}

@Composable
fun HomeScreen(onStartService: () -> Unit, onStopService: () -> Unit, onOpenPractice: () -> Unit) {
    val context = LocalContext.current
    var isServiceRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SearchLauncher",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Service Status", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (isServiceRunning) "Active" else "Inactive",
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (isServiceRunning) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        if (isServiceRunning) {
                            onStopService()
                            isServiceRunning = false
                        } else {
                            onStartService()
                            isServiceRunning = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isServiceRunning) "Stop Service" else "Start Service") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "How to Use", style = MaterialTheme.typography.titleMedium)
                Text(
                    text =
                        "1. Swipe from the edge of the screen\n" +
                                "2. Swipe back to the edge\n" +
                                "3. The search bar will appear\n" +
                                "4. Start typing to search apps and content",
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(onClick = onOpenPractice, modifier = Modifier.fillMaxWidth()) {
                    Text("Practice Gesture")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Permissions", style = MaterialTheme.typography.titleMedium)

                PermissionStatus(
                    title = "Display Over Other Apps",
                    granted =
                        rememberPermissionState {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                Settings.canDrawOverlays(context)
                            } else true
                        }
                            .value,
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent =
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            context.startActivity(intent)
                        }
                    }
                )

                PermissionStatus(
                    title = "Accessibility Service",
                    granted =
                        rememberPermissionState { isAccessibilityServiceEnabled(context) }
                            .value,
                    onGrant = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
                )

                PermissionStatus(
                    title = "Usage Access (Optional)",
                    granted =
                        rememberPermissionState { hasUsageStatsPermission(context) }.value,
                    onGrant = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    }
                )

                PermissionStatus(
                    title = "Modify System Settings (Rotation)",
                    granted =
                        rememberPermissionState {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                Settings.System.canWrite(context)
                            } else true
                        }.value,
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionStatus(title: String, granted: Boolean, onGrant: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (granted) "Granted" else "Not granted",
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
            )
        }

        if (!granted) {
            TextButton(onClick = onGrant) { Text("Grant") }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabledServices =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    return enabledServices?.contains(context.packageName) == true
}

fun hasUsageStatsPermission(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}
