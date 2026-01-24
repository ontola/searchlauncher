package com.searchlauncher.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.service.OverlayService
import com.searchlauncher.app.ui.theme.SearchLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

  // Export state
  var exportIncludeWallpapers = true
  var exportWallpaperSize = 0L
  var showExportDialog by mutableStateOf(false)
  var showImportConfirmation by mutableStateOf(false)
  var pendingImportUri: Uri? = null

  private val exportBackupLauncher =
    registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
      uri ->
      if (uri != null) {
        lifecycleScope.launch { performExport(uri) }
      }
    }

  private val importBackupLauncher =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let { lifecycleScope.launch { performImport(it) } }
    }

  lateinit var appWidgetManager: android.appwidget.AppWidgetManager
  lateinit var appWidgetHost: android.appwidget.AppWidgetHost
  private val APPWIDGET_HOST_ID = 1002
  private val REQUEST_CREATE_APPWIDGET = 5
  private val REQUEST_PICK_APPWIDGET = 9

  private val createWidgetLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        val data = result.data
        val extras = data?.extras
        val appWidgetId =
          extras?.getInt(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        if (appWidgetId != -1) {
          lifecycleScope.launch {
            (application as SearchLauncherApp).widgetRepository.addWidgetId(appWidgetId)
            dataStore.edit { prefs -> prefs[PreferencesKeys.SHOW_WIDGETS] = true }
          }
        }
      } else {
        // If cancelled, delete the allocated ID
        val data = result.data
        val extras = data?.extras
        val appWidgetId =
          extras?.getInt(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        if (appWidgetId != -1) {
          appWidgetHost.deleteAppWidgetId(appWidgetId)
        }
      }
    }

  private val pickWallpapersLauncher =
    registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
      if (uris.isNotEmpty()) {
        val app = application as SearchLauncherApp
        lifecycleScope.launch {
          uris.forEach { uri -> app.wallpaperRepository.addWallpaper(uri) }
          Toast.makeText(this@MainActivity, "Added ${uris.size} wallpapers", Toast.LENGTH_SHORT)
            .show()
        }
      }
    }

  private val requestReadMediaImagesLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
      android.util.Log.d("MainActivity", "READ_MEDIA_IMAGES permission granted: $isGranted")
      if (isGranted) {
        val app = application as SearchLauncherApp
        lifecycleScope.launch {
          val result = app.wallpaperRepository.addSystemWallpaper()
          if (result != null) {
            android.util.Log.d("MainActivity", "System wallpaper imported: $result")
            Toast.makeText(this@MainActivity, "Imported system wallpaper", Toast.LENGTH_SHORT)
              .show()
          } else {
            android.util.Log.w("MainActivity", "Failed to import system wallpaper")
          }
        }
      }
    }

  private var pendingAppWidgetId = -1

  private val bindWidgetLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      android.util.Log.d("MainActivity", "bindWidgetLauncher result: ${result.resultCode}")

      val data = result.data
      val extras = data?.extras
      var appWidgetId =
        extras?.getInt(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1

      // Fallback to pending ID if result stripped it
      if (appWidgetId == -1 && pendingAppWidgetId != -1) {
        android.util.Log.d("MainActivity", "Recovered pending ID: $pendingAppWidgetId")
        appWidgetId = pendingAppWidgetId
      }

      pendingAppWidgetId = -1 // Reset pending ID

      if (result.resultCode == RESULT_OK || appWidgetId != -1) {
        // Check if bound (handling both OK result and False Negative cancelled result)
        if (appWidgetId != -1) {
          val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
          if (appWidgetInfo != null) {
            android.util.Log.d(
              "MainActivity",
              "Widget $appWidgetId verified bound. Configuring/Adding.",
            )
            // Post to handler to ensure ActivityResult state is settled before launching another
            // activity
            android.os.Handler(android.os.Looper.getMainLooper()).post {
              try {
                configureWidget(appWidgetId)
              } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Config failed, force adding", e)
                lifecycleScope.launch {
                  (application as SearchLauncherApp).widgetRepository.addWidgetId(appWidgetId)
                  dataStore.edit { prefs -> prefs[PreferencesKeys.SHOW_WIDGETS] = true }
                }
                Toast.makeText(this, "Configuration skipped.", Toast.LENGTH_SHORT).show()
              }
            }
          } else {
            android.util.Log.e("MainActivity", "Widget $appWidgetId NOT bound.")
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            // Only show error if we really expected it to work (OK result) or user cancelled
            if (result.resultCode == RESULT_OK) {
              Toast.makeText(this, "Binding verification failed.", Toast.LENGTH_SHORT).show()
            }
          }
        } else {
          Toast.makeText(this, "Binding failed (No ID).", Toast.LENGTH_SHORT).show()
        }
      } else {
        // Explicit cancellation with no recovered ID (unlikely if pending logic works)
        android.util.Log.d("MainActivity", "bindWidgetLauncher cancelled/failed")
      }
    }

  fun requestWidgetPick() {
    queryState = "widgets "
    focusTrigger = System.currentTimeMillis()
  }

  private fun onWidgetProviderSelected(providerInfo: android.appwidget.AppWidgetProviderInfo) {
    var appWidgetId = -1
    try {
      appWidgetId = appWidgetHost.allocateAppWidgetId()
      val allowed = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, providerInfo.provider)
      if (allowed) {
        configureWidget(appWidgetId)
      } else {
        val intent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND)
        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        intent.putExtra(
          android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,
          providerInfo.provider,
        )
        bindWidgetLauncher.launch(intent)
      }
    } catch (e: SecurityException) {
      android.util.Log.e("MainActivity", "SecurityException adding widget", e)
      if (appWidgetId != -1) {
        Toast.makeText(
            this,
            "Restricted Settings detected. Attempting manual bind...",
            Toast.LENGTH_SHORT,
          )
          .show()

        // Fallback: Try to launch the system bind dialog
        try {
          val intent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND)
          intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
          intent.putExtra(
            android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,
            providerInfo.provider,
          )
          pendingAppWidgetId = appWidgetId // Track ID in case result strips it
          bindWidgetLauncher.launch(intent)
        } catch (innerE: Exception) {
          android.util.Log.e("MainActivity", "Fallback failed", innerE)
          Toast.makeText(
              this,
              "Permission denied. Enable 'Restricted Settings' in App Info, then FORCE STOP the app.",
              Toast.LENGTH_LONG,
            )
            .show()
        }
      } else {
        Toast.makeText(this, "Allocation failed due to restriction.", Toast.LENGTH_SHORT).show()
      }
    } catch (e: Exception) {
      android.util.Log.e("MainActivity", "Error adding widget", e)
      Toast.makeText(this, "Error adding widget: ${e.message}", Toast.LENGTH_SHORT).show()
    }
    showWidgetPicker.value = false
  }

  // State to control custom picker visibility
  private val showWidgetPicker = androidx.compose.runtime.mutableStateOf(false)

  private fun configureWidget(appWidgetId: Int) {
    val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
    if (appWidgetInfo?.configure != null) {
      val intent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
      intent.component = appWidgetInfo.configure
      intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
      createWidgetLauncher.launch(intent)
    } else {
      lifecycleScope.launch {
        (application as SearchLauncherApp).widgetRepository.addWidgetId(appWidgetId)
        dataStore.edit { prefs -> prefs[PreferencesKeys.SHOW_WIDGETS] = true }
      }
    }
  }

  private var queryState by mutableStateOf("")
  private var currentScreenState by mutableStateOf(Screen.Search)
  private var pendingSettingsSection by mutableStateOf<String?>(null)
  private var focusTrigger by mutableStateOf(0L)

  private val screenOnReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        if (currentScreenState == Screen.Search) {
          lifecycleScope.launch {
            kotlinx.coroutines.delay(100)
            focusTrigger = System.currentTimeMillis()
          }
        }
      }
    }

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

    val settingPage = intent.getStringExtra("open_setting_page")
    if (settingPage != null) {
      when (settingPage) {
        "custom_shortcuts",
        "shortcuts" -> {
          currentScreenState = Screen.Settings
          pendingSettingsSection = "shortcuts"
        }
        "history",
        "wallpaper" -> {
          currentScreenState = Screen.Settings
          pendingSettingsSection = settingPage
        }
        "add_wallpaper" -> {
          pickWallpapersLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        }
        "remove_current_wallpaper" -> {
          lifecycleScope.launch {
            val lastUri =
              dataStore.data.map { it[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI] }.first()
            if (lastUri != null) {
              val appInstance = application as SearchLauncherApp
              appInstance.wallpaperRepository.removeWallpaper(Uri.parse(lastUri))
              Toast.makeText(this@MainActivity, "Wallpaper removed", Toast.LENGTH_SHORT).show()
            }
          }
        }
      }
      return
    }

    if (intent.action == "com.searchlauncher.action.EXPORT_BACKUP") {
      initiateExportBackup()
      return
    }

    if (intent.action == "com.searchlauncher.action.IMPORT_BACKUP") {
      importBackupLauncher.launch(arrayOf("*/*")) // Allow user to pick file
      return
    }

    if (intent.action == "com.searchlauncher.action.REFRESH_ICONS") {
      val app = application as SearchLauncherApp
      lifecycleScope.launch {
        app.searchRepository.clearIconCache()
        withContext(Dispatchers.Main) {
          Toast.makeText(this@MainActivity, "Icons Refreshed", Toast.LENGTH_SHORT).show()
        }
      }
      // Force UI refresh if needed, but clearing cache + eventual reload should suffice
      return
    }

    if (intent.action == Intent.ACTION_VIEW) {
      val uri = intent.data
      if (uri != null) {
        pendingImportUri = uri
        showImportConfirmation = true
        return
      }
    }

    if (intent.hasCategory(Intent.CATEGORY_HOME) && intent.action == Intent.ACTION_MAIN) {
      queryState = ""
      currentScreenState = Screen.Search
      pendingSettingsSection = null
      focusTrigger = System.currentTimeMillis()
    }
  }

  fun initiateExportBackup() {
    val app = application as SearchLauncherApp
    lifecycleScope.launch(Dispatchers.IO) {
      val size = app.wallpaperRepository.getWallpapersTotalSize()
      withContext(Dispatchers.Main) {
        exportWallpaperSize = size
        exportIncludeWallpapers = true // Default to true
        showExportDialog = true
      }
    }
  }

  fun performExportAction() {
    val timestamp =
      java.text
        .SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
        .format(java.util.Date())
    val fileName = "searchlauncher_backup_$timestamp.searchlauncher"

    exportBackupLauncher.launch(fileName)
  }

  fun importBackup() {
    importBackupLauncher.launch(arrayOf("*/*"))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    appWidgetManager = android.appwidget.AppWidgetManager.getInstance(applicationContext)
    appWidgetHost = android.appwidget.AppWidgetHost(applicationContext, APPWIDGET_HOST_ID)
    enableEdgeToEdge()
    // Keep keyboard always visible
    window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

    setContent {
      val themeColor =
        remember { dataStore.data.map { it[PreferencesKeys.THEME_COLOR] ?: 0xFF5E6D4E.toInt() } }
          .collectAsState(initial = 0xFF5E6D4E.toInt())

      val themeSaturation =
        remember { dataStore.data.map { it[PreferencesKeys.THEME_SATURATION] ?: 50f } }
          .collectAsState(initial = 50f)

      val darkMode =
        remember { dataStore.data.map { it[PreferencesKeys.DARK_MODE] ?: 0 } }
          .collectAsState(initial = 0)

      val isOled =
        remember { dataStore.data.map { it[PreferencesKeys.OLED_MODE] ?: false } }
          .collectAsState(initial = false)

      SearchLauncherTheme(
        themeColor = themeColor.value,
        darkThemeMode = darkMode.value,
        chroma = themeSaturation.value,
        isOled = isOled.value,
      ) {
        val context = LocalContext.current
        val lastImageUriString by
          remember { context.dataStore.data.map { it[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI] } }
            .collectAsState(initial = null)

        val app = context.applicationContext as SearchLauncherApp
        val managedWallpapers by app.wallpaperRepository.wallpapers.collectAsState()

        val backgroundColor =
          if (lastImageUriString.isNullOrEmpty() && managedWallpapers.isEmpty()) {
            android.util.Log.d("MainActivity", "Setting background to Transparent")
            Color.Transparent
          } else {
            android.util.Log.d(
              "MainActivity",
              "Setting background to Theme background (lastUri=$lastImageUriString)",
            )
            MaterialTheme.colorScheme.background
          }

        Surface(modifier = Modifier.fillMaxSize(), color = backgroundColor) { MainScreen() }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    appWidgetHost.startListening()

    // Register for screen on events
    val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(screenOnReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(screenOnReceiver, filter)
    }
  }

  override fun onResume() {
    super.onResume()
    if (currentScreenState == Screen.Search) {
      focusTrigger = System.currentTimeMillis()
      // Trigger again after delay for screen unlock scenarios
      lifecycleScope.launch {
        kotlinx.coroutines.delay(200)
        focusTrigger = System.currentTimeMillis()
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus && currentScreenState == Screen.Search) {
      focusTrigger = System.currentTimeMillis()
    }
  }

  override fun onStop() {
    super.onStop()
    try {
      appWidgetHost.stopListening()
    } catch (e: Exception) {
      // Ignore
    }

    // Unregister screen on receiver
    try {
      unregisterReceiver(screenOnReceiver)
    } catch (e: Exception) {
      // Receiver might not be registered
    }
  }

  private enum class Screen {
    Search,
    Settings,
    AppList,
  }

  @Composable
  private fun MainScreen() {
    val context = LocalContext.current
    var showPractice by remember { mutableStateOf(false) }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Hoist wallpaper state
    val lastImageUriString by
      remember { context.dataStore.data.map { it[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI] } }
        .collectAsState(initial = null)

    val app = context.applicationContext as SearchLauncherApp
    val managedWallpapers by app.wallpaperRepository.wallpapers.collectAsState()

    var folderImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(managedWallpapers) { folderImages = managedWallpapers }

    // Handle back press
    BackHandler(enabled = currentScreenState != Screen.Search) {
      if (currentScreenState == Screen.Settings || currentScreenState == Screen.AppList) {
        currentScreenState = Screen.Search
        focusTrigger = System.currentTimeMillis()
      }
    }

    val swipeGestureEnabled =
      remember { context.dataStore.data.map { it[PreferencesKeys.SWIPE_GESTURE_ENABLED] ?: false } }
        .collectAsState(initial = false)

    val isFirstRun by
      remember { context.dataStore.data.map { it[PreferencesKeys.IS_FIRST_RUN] ?: true } }
        .collectAsState(initial = false)

    LaunchedEffect(isFirstRun, managedWallpapers.isEmpty()) {
      if (isFirstRun) {
        context.dataStore.edit { it[PreferencesKeys.IS_FIRST_RUN] = false }
      }
      // Import system wallpaper if none are loaded (e.g., on first run or after reset)
      if (managedWallpapers.isEmpty()) {
        android.util.Log.d(
          "MainActivity",
          "No wallpapers loaded, requesting permission or importing system wallpaper",
        )
        // On Android 13+ (API 33), we need to request READ_MEDIA_IMAGES at runtime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          val permission = android.Manifest.permission.READ_MEDIA_IMAGES
          if (
            context.checkSelfPermission(permission) ==
              android.content.pm.PackageManager.PERMISSION_GRANTED
          ) {
            android.util.Log.d("MainActivity", "Permission already granted, importing wallpaper")
            val result = app.wallpaperRepository.addSystemWallpaper()
            android.util.Log.d("MainActivity", "System wallpaper import result: $result")
          } else {
            android.util.Log.d("MainActivity", "Requesting READ_MEDIA_IMAGES permission")
            (context as? MainActivity)?.requestReadMediaImagesLauncher?.launch(permission)
          }
        } else {
          // On older versions, try directly
          val result = app.wallpaperRepository.addSystemWallpaper()
          android.util.Log.d("MainActivity", "System wallpaper import result (legacy): $result")
        }
      }
    }

    // Auto-extract theme color from wallpaper when enabled
    val autoThemeFromWallpaper by
      remember {
          context.dataStore.data.map { it[PreferencesKeys.AUTO_THEME_FROM_WALLPAPER] ?: true }
        }
        .collectAsState(initial = true)

    // Get current wallpaper URI (either last viewed or first in list)
    val currentWallpaperUri =
      remember(lastImageUriString, managedWallpapers) {
        managedWallpapers.find { it.toString() == lastImageUriString }
          ?: managedWallpapers.firstOrNull()
      }

    // Auto-update theme color when wallpaper changes and auto mode is enabled
    LaunchedEffect(currentWallpaperUri, autoThemeFromWallpaper) {
      if (autoThemeFromWallpaper && currentWallpaperUri != null) {
        android.util.Log.d("MainActivity", "Auto-extracting theme color from: $currentWallpaperUri")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          val dominantColor = app.wallpaperRepository.extractDominantColor(currentWallpaperUri)
          if (dominantColor != null) {
            android.util.Log.d(
              "MainActivity",
              "Extracted color: ${Integer.toHexString(dominantColor)}, updating theme",
            )
            withContext(kotlinx.coroutines.Dispatchers.Main) {
              context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.THEME_COLOR] = dominantColor
                preferences[PreferencesKeys.THEME_SATURATION] = 50f // Default saturation
              }
            }
          }
        }
      }
    }

    if (showPractice) {
      PracticeGestureScreen(onBack = { showPractice = false })
    } else {
      // Auto-start service if enabled in settings AND permissions are granted
      LaunchedEffect(swipeGestureEnabled.value) {
        if (swipeGestureEnabled.value) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
            startOverlayService()
          }
        } else {
          stopOverlayService()
        }
      }

      // Hide keyboard when opening App List
      LaunchedEffect(currentScreenState) {
        if (currentScreenState == Screen.AppList) {
          keyboardController?.hide()
          focusManager.clearFocus()
        }
      }

      if (showImportConfirmation) {
        AlertDialog(
          onDismissRequest = {
            showImportConfirmation = false
            pendingImportUri = null
          },
          title = { Text("Import Backup?") },
          text = {
            Text(
              "This will overwrite your existing shortcuts, favorites, history, and settings. Are you sure?"
            )
          },
          confirmButton = {
            TextButton(
              onClick = {
                showImportConfirmation = false
                pendingImportUri?.let { uri -> lifecycleScope.launch { performImport(uri) } }
                pendingImportUri = null
              }
            ) {
              Text("Import")
            }
          },
          dismissButton = {
            TextButton(
              onClick = {
                showImportConfirmation = false
                pendingImportUri = null
              }
            ) {
              Text("Cancel")
            }
          },
        )
      }

      if (showExportDialog) {
        AlertDialog(
          onDismissRequest = { showExportDialog = false },
          title = { Text("Export Backup") },
          text = {
            Column {
              Text("Create a backup of your data.")
              Spacer(modifier = Modifier.height(16.dp))
              Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(
                  checked = exportIncludeWallpapers,
                  onCheckedChange = { exportIncludeWallpapers = it },
                )
                val sizeMb = exportWallpaperSize / (1024.0 * 1024.0)
                Text("Include wallpapers (%.2f MB)".format(sizeMb))
              }
            }
          },
          confirmButton = {
            TextButton(
              onClick = {
                showExportDialog = false
                performExportAction()
              }
            ) {
              Text("Export")
            }
          },
          dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel") } },
        )
      }

      Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Base Content (Search, Settings, etc.)
        // When AppList is open, we want the base layer to be Search
        val baseState =
          if (currentScreenState == Screen.AppList) Screen.Search else currentScreenState

        androidx.compose.animation.AnimatedContent(
          targetState = baseState,
          transitionSpec = {
            androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()
          },
          label = "BaseScreenTransition",
        ) { targetState ->
          when (targetState) {
            Screen.Search -> {
              SearchScreen(
                query = queryState,
                onQueryChange = { queryState = it },
                onDismiss = { queryState = "" },
                onOpenSettings = {
                  keyboardController?.hide()
                  currentScreenState = Screen.Settings
                },
                onOpenAppDrawer = { currentScreenState = Screen.AppList },
                searchRepository = app.searchRepository,
                focusTrigger = focusTrigger,
                showBackgroundImage = true,
                folderImages = folderImages,
                lastImageUriString = lastImageUriString,
                onAddWidget = { requestWidgetPick() },
                isActive = currentScreenState == Screen.Search,
              )
            }
            Screen.Settings -> {
              SettingsScreen(
                onStartService = { startOverlayService() },
                onStopService = { stopOverlayService() },
                onOpenPractice = { showPractice = true },
                onBack = { currentScreenState = Screen.Search },
                initialHighlightSection = pendingSettingsSection,
                onExportBackup = { initiateExportBackup() },
              )
            }
            else -> {
              /* No-op */
            }
          }
        }

        // Layer 2: App List Overlay
        androidx.compose.animation.AnimatedVisibility(
          visible = currentScreenState == Screen.AppList,
          enter =
            androidx.compose.animation.slideInVertically { height -> height } +
              androidx.compose.animation.fadeIn(),
          exit =
            androidx.compose.animation.slideOutVertically { height -> height } +
              androidx.compose.animation.fadeOut(),
          modifier = Modifier.fillMaxSize(),
        ) {
          AppListScreen(
            searchRepository = app.searchRepository,
            onAppClick = { result ->
              launchApp(context, result)
              currentScreenState = Screen.Search
              queryState = ""
            },
            onBack = {
              currentScreenState = Screen.Search
              focusTrigger = System.currentTimeMillis()
            },
          )
        }
      }

      if (showWidgetPicker.value) {
        WidgetPicker(
          appWidgetManager = appWidgetManager,
          onWidgetSelected = { info -> onWidgetProviderSelected(info) },
          onDismiss = { showWidgetPicker.value = false },
        )
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

  private fun launchApp(context: Context, result: com.searchlauncher.app.data.SearchResult) {
    try {
      if (result is com.searchlauncher.app.data.SearchResult.Shortcut) {
        val intent = Intent.parseUri(result.intentUri, Intent.URI_INTENT_SCHEME)

        // Intercept Widget Binding
        if (intent.action == "com.searchlauncher.action.BIND_WIDGET") {
          val componentNameStr = intent.getStringExtra("component")
          if (componentNameStr != null) {
            val component = android.content.ComponentName.unflattenFromString(componentNameStr)
            if (component != null) {
              val providers =
                appWidgetManager.getInstalledProvidersForProfile(android.os.Process.myUserHandle())
              val providerInfo = providers.find { it.provider == component }
              if (providerInfo != null) {
                onWidgetProviderSelected(providerInfo)
                return
              }
            }
          }
        }

        // Intercept Widget Add Intent
        if (intent.action == "com.searchlauncher.action.ADD_WIDGET") {
          queryState = "widgets "
          focusTrigger = System.currentTimeMillis()
          return
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
      } else if (result is com.searchlauncher.app.data.SearchResult.App) {
        val intent = context.packageManager.getLaunchIntentForPackage(result.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
      }
    } catch (e: Exception) {
      Toast.makeText(context, "Cannot launch app", Toast.LENGTH_SHORT).show()
    }
  }

  fun handleWidgetIntent(intent: Intent) {
    if (intent.action == "com.searchlauncher.action.BIND_WIDGET") {
      val componentStr = intent.getStringExtra("component") ?: return
      val component = android.content.ComponentName.unflattenFromString(componentStr) ?: return
      val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this)
      val providerInfo = appWidgetManager.installedProviders.find { it.provider == component }
      if (providerInfo != null) {
        onWidgetProviderSelected(providerInfo)
      }
    }
  }
}

private suspend fun MainActivity.performExport(uri: android.net.Uri) {
  withContext(Dispatchers.Main) {
    Toast.makeText(this@performExport, "Exporting backup...", Toast.LENGTH_SHORT).show()
  }
  withContext(Dispatchers.IO) {
    try {
      val app = applicationContext as SearchLauncherApp
      val backupManager =
        com.searchlauncher.app.data.BackupManager(
          context = this@performExport,
          snippetsRepository = app.snippetsRepository,
          searchShortcutRepository = app.searchShortcutRepository,
          favoritesRepository = app.favoritesRepository,
          historyRepository = app.historyRepository,
          wallpaperRepository = app.wallpaperRepository,
          widgetRepository = app.widgetRepository,
        )

      contentResolver.openOutputStream(uri)?.use { outputStream ->
        val result = backupManager.exportBackup(outputStream, exportIncludeWallpapers)

        // Calculate file size
        var sizeString = ""
        if (result.isSuccess) {
          try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
              if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                  val size = cursor.getLong(sizeIndex)
                  val units = arrayOf("B", "KB", "MB", "GB")
                  var fileSize = size.toDouble()
                  var i = 0
                  while (fileSize > 1024 && i < units.size - 1) {
                    fileSize /= 1024
                    i++
                  }
                  sizeString = String.format("%.1f %s", fileSize, units[i])
                }
              }
            }
          } catch (e: Exception) {
            e.printStackTrace()
          }
        }

        withContext(Dispatchers.Main) {
          if (result.isSuccess) {
            android.widget.Toast.makeText(
                this@performExport,
                "Backup exported successfully (${result.getOrNull()} items, $sizeString)",
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
  withContext(Dispatchers.Main) {
    Toast.makeText(this@performImport, "Restoring backup...", Toast.LENGTH_SHORT).show()
  }
  withContext(Dispatchers.IO) {
    try {
      val app = applicationContext as SearchLauncherApp

      val backupManager =
        com.searchlauncher.app.data.BackupManager(
          context = this@performImport,
          snippetsRepository = app.snippetsRepository,
          searchShortcutRepository = app.searchShortcutRepository,
          favoritesRepository = app.favoritesRepository,
          historyRepository = app.historyRepository,
          wallpaperRepository = app.wallpaperRepository,
          widgetRepository = app.widgetRepository,
        )

      contentResolver.openInputStream(uri)?.use { inputStream ->
        val result = backupManager.importBackup(inputStream)
        withContext(Dispatchers.Main) {
          if (result.isSuccess) {
            android.widget.Toast.makeText(
                this@performImport,
                "Import successful!",
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
