package com.searchlauncher.app.ui

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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.searchlauncher.app.SearchLauncherApp
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
    registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      uri?.let { lifecycleScope.launch { performExport(it) } }
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
        "custom_shortcuts" -> currentScreenState = Screen.CustomShortcuts
        "snippets",
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

    if (intent.hasCategory(Intent.CATEGORY_HOME) && intent.action == Intent.ACTION_MAIN) {
      queryState = ""
      currentScreenState = Screen.Search
      pendingSettingsSection = null
      focusTrigger = System.currentTimeMillis()
    }
  }

  fun exportBackup() {
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
    // Ensure keyboard opens automatically
    @Suppress("DEPRECATION")
    window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

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
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainScreen()
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    appWidgetHost.startListening()
  }

  override fun onStop() {
    super.onStop()
    try {
      appWidgetHost.stopListening()
    } catch (e: Exception) {
      // Ignore
    }
  }

  private enum class Screen {
    Search,
    Settings,
    CustomShortcuts,
    AppList,
  }

  @Composable
  private fun MainScreen() {
    val context = LocalContext.current
    var showPractice by remember { mutableStateOf(false) }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Hoist wallpaper state
    // Hoist wallpaper state
    val lastImageUriString by
      remember { context.dataStore.data.map { it[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI] } }
        .collectAsState(initial = null)

    val app = context.applicationContext as SearchLauncherApp
    val managedWallpapers by app.wallpaperRepository.wallpapers.collectAsState()

    var folderImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(managedWallpapers) { folderImages = managedWallpapers }

    val showHistory =
      remember { context.dataStore.data.map { it[booleanPreferencesKey("show_history")] ?: false } }
        .collectAsState(initial = false)

    // Handle back press
    BackHandler(enabled = currentScreenState != Screen.Search) {
      if (currentScreenState == Screen.CustomShortcuts) {
        currentScreenState = Screen.Settings
      } else {
        currentScreenState = Screen.Search
        focusTrigger = System.currentTimeMillis()
      }
    }

    val swipeGestureEnabled =
      remember { context.dataStore.data.map { it[PreferencesKeys.SWIPE_GESTURE_ENABLED] ?: false } }
        .collectAsState(initial = false)

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
                showHistory = showHistory.value,
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
                onOpenCustomShortcuts = { currentScreenState = Screen.CustomShortcuts },
                onBack = { currentScreenState = Screen.Search },
                initialHighlightSection = pendingSettingsSection,
              )
            }
            Screen.CustomShortcuts -> {
              CustomShortcutsScreen(onBack = { currentScreenState = Screen.Settings })
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

  object PreferencesKeys {
    val THEME_COLOR = intPreferencesKey("theme_color")
    val THEME_SATURATION = floatPreferencesKey("theme_saturation")
    val DARK_MODE = intPreferencesKey("dark_mode")
    val OLED_MODE = booleanPreferencesKey("oled_mode")
    val BACKGROUND_LAST_IMAGE_URI =
      androidx.datastore.preferences.core.stringPreferencesKey("background_last_image_uri")
    val SWIPE_GESTURE_ENABLED = booleanPreferencesKey("swipe_gesture_enabled")

    val SHOW_WIDGETS = booleanPreferencesKey("show_widgets")
  }
}

private suspend fun MainActivity.performExport(uri: android.net.Uri) {
  withContext(Dispatchers.IO) {
    try {
      val app = applicationContext as SearchLauncherApp
      val backupManager =
        com.searchlauncher.app.data.BackupManager(
          context = this@performExport,
          snippetsRepository = app.snippetsRepository,
          searchShortcutRepository = app.searchShortcutRepository,
          favoritesRepository = app.favoritesRepository,
        )

      contentResolver.openOutputStream(uri)?.use { outputStream ->
        val result = backupManager.exportBackup(outputStream)
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
