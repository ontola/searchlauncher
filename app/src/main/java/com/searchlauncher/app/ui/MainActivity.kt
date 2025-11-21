package com.searchlauncher.app.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.google.android.material.color.utilities.Hct
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.service.OverlayService
import com.searchlauncher.app.ui.theme.SearchLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {

    private val exportBackupLauncher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
                    uri ->
                uri?.let { lifecycleScope.launch { performExport(it) } }
            }

    private val importBackupLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { lifecycleScope.launch { performImport(it) } }
            }

    private var queryState by mutableStateOf("")
    private var currentScreenState by mutableStateOf(Screen.Search)
    private var focusTrigger by mutableStateOf(0L)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        // Check if we should open settings directly
        if (intent.getBooleanExtra("open_settings", false)) {
            currentScreenState = Screen.Settings
            return
        }

        if (intent.hasCategory(Intent.CATEGORY_HOME) && intent.action == Intent.ACTION_MAIN) {
            queryState = ""
            currentScreenState = Screen.Search
            focusTrigger = System.currentTimeMillis()
        }
    }

    fun exportBackup() {
        val timestamp =
                java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                        .format(java.util.Date())
        val fileName = "searchlauncher_backup_$timestamp.searchlauncher"

        exportBackupLauncher.launch(fileName)
    }

    fun importBackup() {
        importBackupLauncher.launch(arrayOf("*/*"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ensure keyboard opens automatically
        @Suppress("DEPRECATION")
        window.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        setContent {
            val themeColor =
                    dataStore
                            .data
                            .map { it[PreferencesKeys.THEME_COLOR] ?: 0xFF00639B.toInt() }
                            .collectAsState(initial = 0xFF00639B.toInt())

            val themeSaturation =
                    dataStore
                            .data
                            .map { it[PreferencesKeys.THEME_SATURATION] ?: 50f }
                            .collectAsState(initial = 50f)

            val darkMode =
                    dataStore
                            .data
                            .map { it[PreferencesKeys.DARK_MODE] ?: 0 }
                            .collectAsState(initial = 0)

            SearchLauncherTheme(
                    themeColor = themeColor.value,
                    darkThemeMode = darkMode.value,
                    chroma = themeSaturation.value
            ) {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) { MainScreen() }
            }
        }
    }

    private enum class Screen {
        Search,
        Settings,
        CustomShortcuts
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
        BackHandler(enabled = currentScreenState != Screen.Search) {
            if (currentScreenState == Screen.CustomShortcuts) {
                currentScreenState = Screen.Settings
            } else {
                currentScreenState = Screen.Search
            }
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
                                onDismiss = { queryState = "" },
                                onOpenSettings = { currentScreenState = Screen.Settings },
                                searchRepository = app.searchRepository,
                                focusTrigger = focusTrigger,
                                showHistory = showHistory.value,
                                showBackgroundImage = true
                        )
                    }
                    Screen.Settings -> {
                        HomeScreen(
                                onStartService = { startOverlayService() },
                                onStopService = { stopOverlayService() },
                                onOpenPractice = { showPractice = true },
                                onOpenCustomShortcuts = {
                                    currentScreenState = Screen.CustomShortcuts
                                },
                                onBack = { currentScreenState = Screen.Search }
                        )
                    }
                    Screen.CustomShortcuts -> {
                        CustomShortcutsScreen(onBack = { currentScreenState = Screen.Settings })
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

    object PreferencesKeys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val THEME_COLOR = intPreferencesKey("theme_color")
        val THEME_SATURATION = floatPreferencesKey("theme_saturation")
        val DARK_MODE = intPreferencesKey("dark_mode")
        val BACKGROUND_URI =
                androidx.datastore.preferences.core.stringPreferencesKey(
                        "background_uri"
                ) // 0: System, 1: Light, 2: Dark
    }
}

@Composable
fun HomeScreen(
        onStartService: () -> Unit,
        onStopService: () -> Unit,
        onOpenPractice: () -> Unit,
        onOpenCustomShortcuts: () -> Unit,
        onBack: () -> Unit
) {
    val context = LocalContext.current
    var isServiceRunning by remember { mutableStateOf(OverlayService.isRunning) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Keep UI in sync if service state changes externally (optional but good practice)
    LaunchedEffect(Unit) {
        while (true) {
            if (isServiceRunning != OverlayService.isRunning) {
                isServiceRunning = OverlayService.isRunning
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    // Check if required permissions are granted
    val hasOverlayPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }
    val hasAccessibilityPermission = remember { isAccessibilityServiceEnabled(context) }

    // Check if this app is the default launcher
    val isDefaultLauncher = remember {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        resolveInfo?.activityInfo?.packageName == context.packageName
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
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "SearchLauncher", style = MaterialTheme.typography.headlineLarge)
        }

        ThemeSettingsCard(onNavigateToHome = onBack)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Side swipe gesture", style = MaterialTheme.typography.titleMedium)
                Text(
                        text = "Swipe from the edge of the screen and back to open search",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                            onClick = {
                                if (isServiceRunning) {
                                    onStopService()
                                    isServiceRunning = false
                                } else {
                                    // Check permissions before starting
                                    if (hasOverlayPermission && hasAccessibilityPermission) {
                                        onStartService()
                                        isServiceRunning = true
                                    } else {
                                        showPermissionDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                    ) { Text(if (isServiceRunning) "Stop" else "Start") }

                    OutlinedButton(onClick = onOpenPractice, modifier = Modifier.weight(1f)) {
                        Text("Practice")
                    }
                }
            }
        }

        // Show launcher settings if not default launcher
        if (!isDefaultLauncher) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "Launcher", style = MaterialTheme.typography.titleMedium)
                    Text(
                            text = "Set SearchLauncher as your default home screen",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                    ) { Text("Set as Default Launcher") }
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
                Text(text = "Custom Shortcuts", style = MaterialTheme.typography.titleMedium)
                Text(
                        text = "Manage your custom search shortcuts (e.g., 'r' for Reddit)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onOpenCustomShortcuts, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage Shortcuts")
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

        QuickCopyCard()

        BackupRestoreCard()

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

    // Show permission guide dialog
    if (showPermissionDialog) {
        AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Required Permissions") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                                "To use the side swipe gesture, SearchLauncher needs the following permissions:",
                                style = MaterialTheme.typography.bodyMedium
                        )

                        if (!hasOverlayPermission) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                        "• Display Over Other Apps",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                Text(
                                        "Allows SearchLauncher to show the search interface on top of other apps",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!hasAccessibilityPermission) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                        "• Accessibility Service",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                Text(
                                        "Detects swipe gestures from the edge of your screen",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                                "You can grant these permissions in the Permissions section below.",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { showPermissionDialog = false }) { Text("Got it") }
                }
        )
    }
}

@Composable
fun PermissionStatus(title: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
        )

        if (granted) {
            Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Check,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
            )
        } else {
            Button(onClick = onGrant) { Text("Grant") }
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
                    @Suppress("DEPRECATION")
                    appOps.unsafeCheckOpNoThrow(
                            AppOpsManager.OPSTR_GET_USAGE_STATS,
                            android.os.Process.myUid(),
                            context.packageName
                    )
                } else {
                    @Suppress("DEPRECATION")
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

@Composable
private fun QuickCopyCard() {
    val context = LocalContext.current
    val app = context.applicationContext as SearchLauncherApp
    val quickCopyItems = app.quickCopyRepository.items.collectAsState()
    val scope = rememberCoroutineScope()

    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<com.searchlauncher.app.data.QuickCopyItem?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "QuickCopy", style = MaterialTheme.typography.titleMedium)
                    Text(
                            text = "Quick access to frequently used text snippets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                        onClick = {
                            editingItem = null
                            showDialog = true
                        }
                ) { Text("Add") }
            }

            // List existing items
            if (quickCopyItems.value.isNotEmpty()) {
                Text(
                        text =
                                "${quickCopyItems.value.size} item${if (quickCopyItems.value.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                quickCopyItems.value.forEach { item ->
                    Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.surfaceVariant
                                    )
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = item.alias,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                Text(
                                        text =
                                                item.content.take(50) +
                                                        if (item.content.length > 50) "..." else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(
                                        onClick = {
                                            editingItem = item
                                            showDialog = true
                                        }
                                ) {
                                    Icon(
                                            imageVector =
                                                    androidx.compose.material.icons.Icons.Default
                                                            .Edit,
                                            contentDescription = "Edit"
                                    )
                                }
                                IconButton(
                                        onClick = {
                                            scope.launch {
                                                app.quickCopyRepository.removeItem(item.alias)
                                            }
                                        }
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        QuickCopyDialog(
                item = editingItem,
                onDismiss = { showDialog = false },
                onSave = { alias, content ->
                    scope.launch {
                        if (editingItem != null) {
                            app.quickCopyRepository.updateItem(editingItem!!.alias, alias, content)
                        } else {
                            app.quickCopyRepository.addItem(alias, content)
                        }
                        showDialog = false
                    }
                }
        )
    }
}

@Composable
private fun QuickCopyDialog(
        item: com.searchlauncher.app.data.QuickCopyItem?,
        onDismiss: () -> Unit,
        onSave: (String, String) -> Unit
) {
    var alias by remember { mutableStateOf(item?.alias ?: "") }
    var content by remember { mutableStateOf(item?.content ?: "") }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (item != null) "Edit QuickCopy" else "Add QuickCopy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                            value = alias,
                            onValueChange = { alias = it },
                            label = { Text("Alias") },
                            placeholder = { Text("e.g., 'bank', 'meet'") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                    )

                    OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("Content") },
                            placeholder = { Text("The text to copy") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6
                    )
                }
            },
            confirmButton = {
                Button(
                        onClick = {
                            if (alias.isNotBlank() && content.isNotBlank()) {
                                onSave(alias.trim(), content.trim())
                            }
                        },
                        enabled = alias.isNotBlank() && content.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ThemeSettingsCard(onNavigateToHome: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeColor by
            context.dataStore
                    .data
                    .map { it[MainActivity.PreferencesKeys.THEME_COLOR] ?: 0xFF00639B.toInt() }
                    .collectAsState(initial = 0xFF00639B.toInt())

    val themeSaturation by
            context.dataStore
                    .data
                    .map { it[MainActivity.PreferencesKeys.THEME_SATURATION] ?: 50f }
                    .collectAsState(initial = 50f)
    val darkMode by
            context.dataStore
                    .data
                    .map { it[MainActivity.PreferencesKeys.DARK_MODE] ?: 0 }
                    .collectAsState(initial = 0)
    val backgroundUriString by
            context.dataStore
                    .data
                    .map { it[MainActivity.PreferencesKeys.BACKGROUND_URI] }
                    .collectAsState(initial = null)

    val launcher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri: Uri? ->
                        uri?.let {
                            val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            context.contentResolver.takePersistableUriPermission(it, flag)
                            scope.launch {
                                context.dataStore.edit { preferences ->
                                    preferences[MainActivity.PreferencesKeys.BACKGROUND_URI] =
                                            it.toString()
                                }
                                // Navigate after save completes
                                withContext(Dispatchers.Main) { onNavigateToHome() }
                            }
                        }
                    }
            )

    Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                    text = "Theme Color",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
            )

            val hct = remember(themeColor) { Hct.fromInt(themeColor) }
            var hue by remember(themeColor) { mutableStateOf(hct.hue.toFloat()) }

            Box(contentAlignment = Alignment.Center) {
                // Gradient background
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(12.dp)
                                        .clip(CircleShape)
                                        .background(
                                                brush =
                                                        Brush.horizontalGradient(
                                                                colors =
                                                                        listOf(
                                                                                Color.Red,
                                                                                Color.Yellow,
                                                                                Color.Green,
                                                                                Color.Cyan,
                                                                                Color.Blue,
                                                                                Color.Magenta,
                                                                                Color.Red
                                                                        )
                                                        )
                                        )
                )

                Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        onValueChangeFinished = {
                            scope.launch {
                                val newColor = Hct.from(hue.toDouble(), 48.0, 40.0).toInt()
                                context.dataStore.edit { preferences ->
                                    preferences[MainActivity.PreferencesKeys.THEME_COLOR] = newColor
                                }
                            }
                        },
                        valueRange = 0f..360f,
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.Transparent,
                                        inactiveTrackColor = Color.Transparent
                                )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                    text = "Saturation",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                    value = themeSaturation,
                    onValueChange = { newValue ->
                        scope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[MainActivity.PreferencesKeys.THEME_SATURATION] =
                                        newValue
                            }
                        }
                    },
                    valueRange = 0f..100f
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                    text = "Dark Mode",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
            )
            val modes = listOf("System", "Light", "Dark")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index, modes.lastIndex),
                            selected = darkMode == index,
                            onClick = {
                                scope.launch {
                                    context.dataStore.edit { preferences ->
                                        preferences[MainActivity.PreferencesKeys.DARK_MODE] = index
                                    }
                                }
                            }
                    ) { Text(mode) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                    text = "Background Image",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                        onClick = { launcher.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                ) { Text("Pick Image") }
                if (backgroundUriString != null) {
                    OutlinedButton(
                            onClick = {
                                scope.launch {
                                    context.dataStore.edit { preferences ->
                                        preferences.remove(
                                                MainActivity.PreferencesKeys.BACKGROUND_URI
                                        )
                                    }
                                    // Navigate after clear completes
                                    withContext(Dispatchers.Main) { onNavigateToHome() }
                                }
                            },
                            modifier = Modifier.weight(1f)
                    ) { Text("Clear Image") }
                }
            }

            // Preview of selected image
            if (backgroundUriString != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                        model = android.net.Uri.parse(backgroundUriString),
                        contentDescription = "Background preview",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(120.dp)
                                        .clip(MaterialTheme.shapes.medium)
                )
            }
        }
    }
}

@Composable
private fun BackupRestoreCard() {
    val context = LocalContext.current
    val app = context.applicationContext as SearchLauncherApp
    val scope = rememberCoroutineScope()
    val activity = context as? MainActivity

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Backup & Restore", style = MaterialTheme.typography.titleMedium)
            Text(
                    text =
                            "Export all your data (QuickCopy, Shortcuts, Favorites, Background) to a .searchlauncher file",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { activity?.exportBackup() }, modifier = Modifier.weight(1f)) {
                    Text("Export")
                }

                OutlinedButton(
                        onClick = { activity?.importBackup() },
                        modifier = Modifier.weight(1f)
                ) { Text("Import") }
            }
        }
    }
}

private suspend fun MainActivity.performExport(uri: android.net.Uri) {
    withContext(Dispatchers.IO) {
        try {
            val app = applicationContext as SearchLauncherApp
            val backgroundUri =
                    dataStore.data.map { it[MainActivity.PreferencesKeys.BACKGROUND_URI] }.first()

            val backupManager =
                    com.searchlauncher.app.data.BackupManager(
                            context = this@performExport,
                            quickCopyRepository = app.quickCopyRepository,
                            customShortcutRepository = app.customShortcutRepository,
                            favoritesRepository = app.favoritesRepository
                    )

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val result = backupManager.exportBackup(outputStream, backgroundUri)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        android.widget.Toast.makeText(
                                        this@performExport,
                                        "Backup exported successfully (${result.getOrNull()} items)",
                                        android.widget.Toast.LENGTH_LONG
                                )
                                .show()
                    } else {
                        android.widget.Toast.makeText(
                                        this@performExport,
                                        "Export failed: ${result.exceptionOrNull()?.message}",
                                        android.widget.Toast.LENGTH_LONG
                                )
                                .show()
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                                this@performExport,
                                "Export failed: ${e.message}",
                                android.widget.Toast.LENGTH_LONG
                        )
                        .show()
            }
        }
    }
}

private suspend fun MainActivity.performImport(uri: android.net.Uri) {
    withContext(Dispatchers.IO) {
        try {
            val app = applicationContext as SearchLauncherApp

            val backupManager =
                    com.searchlauncher.app.data.BackupManager(
                            context = this@performImport,
                            quickCopyRepository = app.quickCopyRepository,
                            customShortcutRepository = app.customShortcutRepository,
                            favoritesRepository = app.favoritesRepository
                    )

            contentResolver.openInputStream(uri)?.use { inputStream ->
                val result = backupManager.importBackup(inputStream)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        val stats = result.getOrNull()!!
                        val message = buildString {
                            append("Import successful:\n")
                            append("- ${stats.quickCopyCount} QuickCopy items\n")
                            append("- ${stats.shortcutsCount} Custom Shortcuts\n")
                            append("- ${stats.favoritesCount} Favorites")
                            if (stats.backgroundRestored) {
                                append("\n- Background image restored")
                            }
                        }
                        android.widget.Toast.makeText(
                                        this@performImport,
                                        message,
                                        android.widget.Toast.LENGTH_LONG
                                )
                                .show()
                    } else {
                        android.widget.Toast.makeText(
                                        this@performImport,
                                        "Import failed: ${result.exceptionOrNull()?.message}",
                                        android.widget.Toast.LENGTH_LONG
                                )
                                .show()
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                                this@performImport,
                                "Import failed: ${e.message}",
                                android.widget.Toast.LENGTH_LONG
                        )
                        .show()
            }
        }
    }
}
