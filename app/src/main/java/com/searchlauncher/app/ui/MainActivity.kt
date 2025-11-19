package com.searchlauncher.app.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.service.OverlayService
import com.searchlauncher.app.ui.theme.SearchLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {

    private var queryState by mutableStateOf("")
    private var currentScreenState by mutableStateOf(Screen.Search)
    private var focusTrigger by mutableStateOf(0L)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) && intent.action == Intent.ACTION_MAIN) {
            queryState = ""
            currentScreenState = Screen.Search
            focusTrigger = System.currentTimeMillis()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ensure keyboard opens automatically
        window.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        setContent {
            SearchLauncherTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) { MainScreen() }
            }
        }
    }

    private enum class Screen {
        Search,
        Settings
    }

    @Composable
    private fun MainScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var showPractice by remember { mutableStateOf(false) }

        val onboardingComplete =
                context.dataStore
                        .data
                        .map { it[booleanPreferencesKey("onboarding_complete")] ?: false }
                        .collectAsState(initial = false)

        val showHistory =
                context.dataStore
                        .data
                        .map { it[booleanPreferencesKey("show_history")] ?: true }
                        .collectAsState(initial = true)

        // Handle back press
        BackHandler(enabled = currentScreenState == Screen.Settings) {
            currentScreenState = Screen.Search
        }

        if (showPractice) {
            PracticeGestureScreen(onBack = { showPractice = false })
        } else {
            if (!onboardingComplete.value) { // Show onboarding if not complete
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

                when (currentScreenState) {
                    Screen.Search -> {
                        val app = application as SearchLauncherApp
                        SearchScreen(
                                query = queryState,
                                onQueryChange = { queryState = it },
                                onDismiss = { /* No-op for home screen */},
                                onOpenSettings = { currentScreenState = Screen.Settings },
                                searchRepository = app.searchRepository,
                                focusTrigger = focusTrigger,
                                showHistory = showHistory.value
                        )
                    }
                    Screen.Settings -> {
                        HomeScreen(
                                onStartService = { startOverlayService() },
                                onStopService = { stopOverlayService() },
                                onOpenPractice = { showPractice = true },
                                onBack = { currentScreenState = Screen.Search }
                        )
                    }
                }
            }
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
fun HomeScreen(
        onStartService: () -> Unit,
        onStopService: () -> Unit,
        onOpenPractice: () -> Unit,
        onBack: () -> Unit
) {
    val context = LocalContext.current
    var isServiceRunning by remember { mutableStateOf(OverlayService.isRunning) }

    // Keep UI in sync if service state changes externally (optional but good practice)
    LaunchedEffect(Unit) {
        while (true) {
            if (isServiceRunning != OverlayService.isRunning) {
                isServiceRunning = OverlayService.isRunning
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                        contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "SearchLauncher", style = MaterialTheme.typography.headlineLarge)
        }

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
                Text(text = "Search Settings", style = MaterialTheme.typography.titleMedium)

                val scope = rememberCoroutineScope()
                val showHistory =
                        context.dataStore
                                .data
                                .map { it[booleanPreferencesKey("show_history")] ?: true }
                                .collectAsState(initial = true)

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Show History", style = MaterialTheme.typography.bodyMedium)
                        Text(
                                text = "Display recently used items when search is empty",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                            checked = showHistory.value,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    context.dataStore.edit { preferences ->
                                        preferences[booleanPreferencesKey("show_history")] = enabled
                                    }
                                }
                            }
                    )
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
                        title = "Contacts (Optional)",
                        granted =
                                rememberPermissionState {
                                            context.checkSelfPermission(
                                                    android.Manifest.permission.READ_CONTACTS
                                            ) ==
                                                    android.content.pm.PackageManager
                                                            .PERMISSION_GRANTED
                                        }
                                        .value,
                        onGrant = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.fromParts("package", context.packageName, null)
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
                                        }
                                        .value,
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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Developer Actions", style = MaterialTheme.typography.titleMedium)

                val scope = rememberCoroutineScope()
                val searchRepository = remember { SearchRepository(context) }

                LaunchedEffect(Unit) { searchRepository.initialize() }

                Button(
                        onClick = {
                            scope.launch {
                                searchRepository.resetIndex()
                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                                    context,
                                                    "Search Index Reset",
                                                    android.widget.Toast.LENGTH_SHORT
                                            )
                                            .show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                ) { Text("Reset Search Index") }

                Button(
                        onClick = {
                            val activityManager =
                                    context.getSystemService(Context.ACTIVITY_SERVICE) as
                                            android.app.ActivityManager
                            activityManager.clearApplicationUserData()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                )
                ) { Text("Reset App Data") }
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
                    appOps.unsafeCheckOpNoThrow(
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
    } catch (e: Exception) {
        false
    }
}
