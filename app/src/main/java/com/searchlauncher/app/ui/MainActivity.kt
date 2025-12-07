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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
              remember {
                        dataStore.data.map { it[PreferencesKeys.THEME_COLOR] ?: 0xFF00639B.toInt() }
                      }
                      .collectAsState(initial = 0xFF00639B.toInt())

      val themeSaturation =
              remember { dataStore.data.map { it[PreferencesKeys.THEME_SATURATION] ?: 50f } }
                      .collectAsState(initial = 50f)

      val darkMode =
              remember { dataStore.data.map { it[PreferencesKeys.DARK_MODE] ?: 0 } }
                      .collectAsState(initial = 0)

      SearchLauncherTheme(
              themeColor = themeColor.value,
              darkThemeMode = darkMode.value,
              chroma = themeSaturation.value,
      ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainScreen()
        }
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

    // Hoist wallpaper state
    val backgroundFolderUriString by
            remember { context.dataStore.data.map { it[PreferencesKeys.BACKGROUND_FOLDER_URI] } }
                    .collectAsState(initial = null)
    var folderImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(backgroundFolderUriString) {
      if (backgroundFolderUriString != null) {
        withContext(Dispatchers.IO) {
          try {
            val folderUri = Uri.parse(backgroundFolderUriString)
            val documentFile =
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
            val files = documentFile?.listFiles()
            val images =
                    files?.filter { file ->
                      val mimeType = file.type
                      mimeType != null && mimeType.startsWith("image/")
                    }
            val uris = images?.sortedBy { it.name }?.map { it.uri } ?: emptyList()
            withContext(Dispatchers.Main) { folderImages = uris }
          } catch (e: Exception) {
            e.printStackTrace()
          }
        }
      } else {
        folderImages = emptyList()
      }
    }

    val lastImageUriString by
            remember {
                      context.dataStore.data.map { it[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI] }
                    }
                    .collectAsState(initial = null)

    val onboardingComplete =
            remember {
                      context.dataStore.data.map {
                        it[booleanPreferencesKey("onboarding_complete")] ?: false
                      }
                    }
                    .collectAsState(initial = false)

    val showHistory =
            remember {
                      context.dataStore.data.map {
                        it[booleanPreferencesKey("show_history")] ?: true
                      }
                    }
                    .collectAsState(initial = true)

    // Handle back press
    BackHandler(enabled = currentScreenState != Screen.Search) {
      if (currentScreenState == Screen.CustomShortcuts) {
        currentScreenState = Screen.Settings
      } else {
        currentScreenState = Screen.Search
      }
    }

    val swipeGestureEnabled =
            remember {
                      context.dataStore.data.map {
                        it[PreferencesKeys.SWIPE_GESTURE_ENABLED] ?: false
                      }
                    }
                    .collectAsState(initial = false)

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
                    // Don't auto-enable swipe gesture on fresh install, let user decide in settings
                    // or maybe enable it by default? User asked for "not auto-enable".
                    // So we do nothing here regarding the service.
                  }
                }
        )
      } else {
        // Auto-start service if enabled in settings AND permissions are granted
        LaunchedEffect(swipeGestureEnabled.value) {
          if (swipeGestureEnabled.value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)
            ) {
              startOverlayService()
            }
          } else {
            stopOverlayService()
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
                    showBackgroundImage = true,
                    folderImages = folderImages,
                    lastImageUriString = lastImageUriString,
            )
          }
          Screen.Settings -> {
            HomeScreen(
                    onStartService = { startOverlayService() },
                    onStopService = { stopOverlayService() },
                    onOpenPractice = { showPractice = true },
                    onOpenCustomShortcuts = { currentScreenState = Screen.CustomShortcuts },
                    onBack = { currentScreenState = Screen.Search },
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
    val BACKGROUND_FOLDER_URI =
            androidx.datastore.preferences.core.stringPreferencesKey("background_folder_uri")
    val BACKGROUND_LAST_IMAGE_URI =
            androidx.datastore.preferences.core.stringPreferencesKey("background_last_image_uri")
    val SWIPE_GESTURE_ENABLED = booleanPreferencesKey("swipe_gesture_enabled")
  }
}

@Composable
fun HomeScreen(
        onStartService: () -> Unit,
        onStopService: () -> Unit,
        onOpenPractice: () -> Unit,
        onOpenCustomShortcuts: () -> Unit,
        onBack: () -> Unit,
) {
  val context = LocalContext.current
  var showPermissionDialog by remember { mutableStateOf(false) }

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
          verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = "SearchLauncher", style = MaterialTheme.typography.headlineLarge)
    }

    ThemeSettingsCard(onNavigateToHome = onBack)

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val scope = rememberCoroutineScope()
        val swipeGestureEnabled =
                remember {
                          context.dataStore.data.map {
                            it[MainActivity.PreferencesKeys.SWIPE_GESTURE_ENABLED] ?: false
                          }
                        }
                        .collectAsState(initial = false)

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = "Side swipe gesture", style = MaterialTheme.typography.titleMedium)
            Text(
                    text = "Swipe from the edge of the screen and back to open search",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
                  checked = swipeGestureEnabled.value,
                  onCheckedChange = { enabled ->
                    if (enabled) {
                      // Check permissions before enabling
                      if (hasOverlayPermission && hasAccessibilityPermission) {
                        scope.launch {
                          context.dataStore.edit { preferences ->
                            preferences[MainActivity.PreferencesKeys.SWIPE_GESTURE_ENABLED] = true
                          }
                          onStartService()
                        }
                      } else {
                        showPermissionDialog = true
                      }
                    } else {
                      scope.launch {
                        context.dataStore.edit { preferences ->
                          preferences[MainActivity.PreferencesKeys.SWIPE_GESTURE_ENABLED] = false
                        }
                        onStopService()
                      }
                    }
                  },
          )
        }

        OutlinedButton(onClick = onOpenPractice, modifier = Modifier.fillMaxWidth()) {
          Text("Practice Gesture")
        }
      }
    }

    // Show launcher settings if not default launcher
    if (!isDefaultLauncher) {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(text = "Launcher", style = MaterialTheme.typography.titleMedium)
          Text(
                  text = "Set SearchLauncher as your default home screen",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Button(
                  onClick = {
                    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                    context.startActivity(intent)
                  },
                  modifier = Modifier.fillMaxWidth(),
          ) { Text("Set as Default Launcher") }
        }
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Search Settings", style = MaterialTheme.typography.titleMedium)

        val scope = rememberCoroutineScope()
        val showHistory =
                remember {
                          context.dataStore.data.map {
                            it[booleanPreferencesKey("show_history")] ?: true
                          }
                        }
                        .collectAsState(initial = true)

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = "Show History", style = MaterialTheme.typography.bodyMedium)
            Text(
                    text = "Display recently used items when search is empty",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                  },
          )
        }
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Custom Shortcuts", style = MaterialTheme.typography.titleMedium)
        Text(
                text = "Manage your custom search shortcuts (e.g., 'r' for Reddit)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOpenCustomShortcuts, modifier = Modifier.fillMaxWidth()) {
          Text("Manage Shortcuts")
        }
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Permissions", style = MaterialTheme.typography.titleMedium)

        PermissionStatus(
                title = "Display Over Other Apps",
                description =
                        "Required for the side swipe gesture to show the search overlay on top of other apps.",
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
                                    Uri.parse("package:${context.packageName}"),
                            )
                    context.startActivity(intent)
                  }
                },
        )

        PermissionStatus(
                title = "Accessibility Service",
                description =
                        "Required for the side swipe gesture to detect swipes from the edge of the screen.",
                granted = rememberPermissionState { isAccessibilityServiceEnabled(context) }.value,
                onGrant = {
                  val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                  context.startActivity(intent)
                },
        )

        PermissionStatus(
                title = "Usage Access (Optional)",
                description =
                        "Used to rank search results based on your most frequently used apps.",
                granted = rememberPermissionState { hasUsageStatsPermission(context) }.value,
                onGrant = {
                  val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                  context.startActivity(intent)
                },
        )

        PermissionStatus(
                title = "Contacts (Optional)",
                description = "Allows you to search for contacts directly from the launcher.",
                granted =
                        rememberPermissionState {
                                  context.checkSelfPermission(
                                          android.Manifest.permission.READ_CONTACTS
                                  ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                }
                                .value,
                onGrant = {
                  val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                  intent.data = Uri.fromParts("package", context.packageName, null)
                  context.startActivity(intent)
                },
        )

        PermissionStatus(
                title = "Modify System Settings (Rotation)",
                description =
                        "Required if you want the launcher to control screen rotation or other system settings.",
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
                },
        )
      }
    }

    SnippetsCard()

    BackupRestoreCard()

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                      android.widget.Toast.LENGTH_SHORT,
                              )
                              .show()
                    }
                  }
                },
                modifier = Modifier.fillMaxWidth(),
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
                        ),
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
                        style = MaterialTheme.typography.bodyMedium,
                )

                if (!hasOverlayPermission) {
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                            "• Display Over Other Apps",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    Text(
                            "Allows SearchLauncher to show the search interface on top of other apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }

                if (!hasAccessibilityPermission) {
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                            "• Accessibility Service",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    Text(
                            "Detects swipe gestures from the edge of your screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }

                Text(
                        "You can grant these permissions in the Permissions section below.",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
              }
            },
            confirmButton = {
              Button(onClick = { showPermissionDialog = false }) { Text("Got it") }
            },
    )
  }
}

@Composable
fun PermissionStatus(
        title: String,
        description: String? = null,
        granted: Boolean,
        onGrant: () -> Unit,
) {
  Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(text = title, style = MaterialTheme.typography.bodyMedium)
      if (description != null) {
        Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (granted) {
      Icon(
              imageVector = androidx.compose.material.icons.Icons.Default.Check,
              contentDescription = "Granted",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(32.dp),
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
                  Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
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
                      context.packageName,
              )
            } else {
              @Suppress("DEPRECATION")
              appOps.checkOpNoThrow(
                      AppOpsManager.OPSTR_GET_USAGE_STATS,
                      android.os.Process.myUid(),
                      context.packageName,
              )
            }
    mode == AppOpsManager.MODE_ALLOWED
  } catch (e: Exception) {
    false
  }
}

@Composable
private fun SnippetsCard() {
  val context = LocalContext.current
  val app = context.applicationContext as SearchLauncherApp
  val snippetItems = app.snippetsRepository.items.collectAsState()
  val scope = rememberCoroutineScope()

  var showDialog by remember { mutableStateOf(false) }
  var editingItem by remember { mutableStateOf<com.searchlauncher.app.data.SnippetItem?>(null) }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = "Snippets", style = MaterialTheme.typography.titleMedium)
          Text(
                  text = "Quick access to frequently used text snippets",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
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
      if (snippetItems.value.isNotEmpty()) {
        Text(
                text =
                        "${snippetItems.value.size} item${if (snippetItems.value.size != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        snippetItems.value.forEach { item ->
          Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = item.alias,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                Text(
                        text = item.content.take(50) + if (item.content.length > 50) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                          imageVector = androidx.compose.material.icons.Icons.Default.Edit,
                          contentDescription = "Edit",
                  )
                }
                IconButton(
                        onClick = { scope.launch { app.snippetsRepository.deleteItem(item.alias) } }
                ) {
                  Icon(
                          imageVector = Icons.Default.Close,
                          contentDescription = "Delete",
                          tint = MaterialTheme.colorScheme.error,
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
    SnippetDialog(
            item = editingItem,
            onDismiss = { showDialog = false },
            onSave = { alias, content ->
              scope.launch {
                if (editingItem != null) {
                  app.snippetsRepository.updateItem(editingItem!!.alias, alias, content)
                } else {
                  app.snippetsRepository.addItem(alias, content)
                }
                showDialog = false
              }
            },
    )
  }
}

@Composable
private fun SnippetDialog(
        item: com.searchlauncher.app.data.SnippetItem?,
        onDismiss: () -> Unit,
        onSave: (String, String) -> Unit,
) {
  var alias by remember { mutableStateOf(item?.alias ?: "") }
  var content by remember { mutableStateOf(item?.content ?: "") }

  AlertDialog(
          onDismissRequest = onDismiss,
          title = { Text(if (item != null) "Edit Snippet" else "Add Snippet") },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                      value = alias,
                      onValueChange = { alias = it },
                      label = { Text("Alias") },
                      placeholder = { Text("e.g., 'bank', 'meet'") },
                      modifier = Modifier.fillMaxWidth(),
                      singleLine = true,
              )

              OutlinedTextField(
                      value = content,
                      onValueChange = { content = it },
                      label = { Text("Content") },
                      placeholder = { Text("The text to copy") },
                      modifier = Modifier.fillMaxWidth(),
                      minLines = 3,
                      maxLines = 6,
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
                    enabled = alias.isNotBlank() && content.isNotBlank(),
            ) { Text("Save") }
          },
          dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun BackupRestoreCard() {
  val context = LocalContext.current
  val activity = context as? MainActivity

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(text = "Backup & Restore", style = MaterialTheme.typography.titleMedium)
      Text(
              text =
                      "Export all your data (Snippets, Shortcuts, Favorites, Background) to a .searchlauncher file",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { activity?.exportBackup() }, modifier = Modifier.weight(1f)) {
          Text("Export")
        }

        OutlinedButton(onClick = { activity?.importBackup() }, modifier = Modifier.weight(1f)) {
          Text("Import")
        }
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
                      snippetsRepository = app.snippetsRepository,
                      searchShortcutRepository = app.searchShortcutRepository,
                      favoritesRepository = app.favoritesRepository,
              )

      contentResolver.openOutputStream(uri)?.use { outputStream ->
        val result = backupManager.exportBackup(outputStream, backgroundUri)
        withContext(Dispatchers.Main) {
          if (result.isSuccess) {
            android.widget.Toast.makeText(
                            this@performExport,
                            "Backup exported successfully (${result.getOrNull()} items)",
                            android.widget.Toast.LENGTH_LONG,
                    )
                    .show()
          } else {
            android.widget.Toast.makeText(
                            this@performExport,
                            "Export failed: ${result.exceptionOrNull()?.message}",
                            android.widget.Toast.LENGTH_LONG,
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
                        android.widget.Toast.LENGTH_LONG,
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
                      snippetsRepository = app.snippetsRepository,
                      searchShortcutRepository = app.searchShortcutRepository,
                      favoritesRepository = app.favoritesRepository,
              )

      contentResolver.openInputStream(uri)?.use { inputStream ->
        val result = backupManager.importBackup(inputStream)
        withContext(Dispatchers.Main) {
          if (result.isSuccess) {
            val stats = result.getOrNull()!!
            val message = buildString {
              append("Import successful:\n")
              append("- ${stats.snippetsCount} Snippets\n")
              append("- ${stats.shortcutsCount} Custom Shortcuts\n")
              append("- ${stats.favoritesCount} Favorites")
              if (stats.backgroundRestored) {
                append("\n- Background image restored")
              }
            }
            android.widget.Toast.makeText(
                            this@performImport,
                            message,
                            android.widget.Toast.LENGTH_LONG,
                    )
                    .show()
          } else {
            android.widget.Toast.makeText(
                            this@performImport,
                            "Import failed: ${result.exceptionOrNull()?.message}",
                            android.widget.Toast.LENGTH_LONG,
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
                        android.widget.Toast.LENGTH_LONG,
                )
                .show()
      }
    }
  }
}
