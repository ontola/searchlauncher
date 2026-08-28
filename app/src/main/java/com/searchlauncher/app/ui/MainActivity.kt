package com.searchlauncher.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import com.searchlauncher.app.data.Prefs
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.ui.theme.SearchLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The stored wallpaper choice, where a null [uri] means none was saved rather than not yet read.
 */
private data class SavedWallpaper(val uri: String?)

class MainActivity : ComponentActivity(), KeyShortcutHost, PipCapable {

  // Export state
  var exportIncludeWallpapers = true
  var exportWallpaperSize = 0L
  var showExportDialog by mutableStateOf(false)
  var showImportConfirmation by mutableStateOf(false)
  var pendingImportUri: Uri? = null
  override var keyShortcutHandler: ((android.view.KeyEvent) -> Boolean)? = null
  override var pipVideoView: android.view.View? = null
  override var inPictureInPicture by mutableStateOf(false)

  /**
   * The opening offer of the optional permissions, and which of them are worth offering.
   *
   * They are asked for here rather than where they happen to be needed, because they were not
   * reachable before: photo access arrived as a bare system dialog in the first seconds, with
   * nothing on screen to say what it was for, and contact or calendar access was never requested at
   * all — the app only ever checked them and dropped a hint in the search bar, so the one way to
   * turn those searches on was to find SearchLauncher in Android's settings. Whatever is missing is
   * explained on screen first, and the system prompts follow only on yes.
   */
  var showOnboardingPermissions by mutableStateOf(false)
  var onboardingOffersContacts by mutableStateOf(false)
  var onboardingOffersPhotos by mutableStateOf(false)
  var onboardingOffersCalendar by mutableStateOf(false)

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
  private val REQUEST_CONFIGURE_APPWIDGET = 10

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

  /**
   * Asks for whichever optional permissions were accepted in the opening offer, then puts each one
   * straight to use: the wallpaper is imported and contacts are indexed on the spot, so saying yes
   * visibly does something rather than leaving the user to guess whether it took.
   */
  private val requestOnboardingPermissionsLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
      val app = application as SearchLauncherApp
      lifecycleScope.launch {
        if (results[android.Manifest.permission.READ_MEDIA_IMAGES] == true) {
          app.wallpaperRepository.addSystemWallpaper()
          app.searchRepository.indexDownloads()
        }
        if (results[android.Manifest.permission.READ_CONTACTS] == true) {
          app.searchRepository.indexContacts()
        }
        if (results[android.Manifest.permission.READ_CALENDAR] == true) {
          app.searchRepository.indexCalendar()
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
              } catch (e: android.content.ActivityNotFoundException) {
                android.util.Log.e("MainActivity", "Configuration activity not found", e)
                // Widget's configure activity doesn't exist - just add the widget without config
                persistAddedWidget(appWidgetId)
                Toast.makeText(
                    this,
                    "Widget added (no configuration available)",
                    Toast.LENGTH_SHORT,
                  )
                  .show()
              } catch (e: SecurityException) {
                android.util.Log.e("MainActivity", "Security exception launching config", e)
                // Permission issue - just add the widget
                persistAddedWidget(appWidgetId)
                Toast.makeText(this, "Widget added (configuration not allowed)", Toast.LENGTH_SHORT)
                  .show()
              } catch (e: Exception) {
                android.util.Log.e(
                  "MainActivity",
                  "Config failed: ${e.javaClass.simpleName}: ${e.message}",
                  e,
                )
                persistAddedWidget(appWidgetId)
                Toast.makeText(
                    this,
                    "Widget added (config error: ${e.message})",
                    Toast.LENGTH_SHORT,
                  )
                  .show()
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
    updateQueryState("widgets ")
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

  // Track widget ID being configured for onActivityResult handling
  private var pendingConfigAppWidgetId = -1

  private fun persistAddedWidget(appWidgetId: Int) {
    lifecycleScope.launch {
      (application as SearchLauncherApp).widgetRepository.addWidgetId(appWidgetId)
      dataStore.edit { prefs -> prefs[PreferencesKeys.SHOW_WIDGETS] = true }
    }
  }

  private fun configureWidget(appWidgetId: Int) {
    val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
    if (appWidgetInfo?.configure != null) {
      android.util.Log.d(
        "MainActivity",
        "Launching widget config for $appWidgetId: ${appWidgetInfo.configure}",
      )
      pendingConfigAppWidgetId = appWidgetId
      // Use AppWidgetHost's method which has proper permissions for widget configuration
      try {
        appWidgetHost.startAppWidgetConfigureActivityForResult(
          this,
          appWidgetId,
          0, // intentFlags
          REQUEST_CONFIGURE_APPWIDGET,
          null, // options bundle
        )
      } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Failed to start widget config activity", e)
        // Fallback: add widget without configuration
        pendingConfigAppWidgetId = -1
        persistAddedWidget(appWidgetId)
        Toast.makeText(this, "Widget added (config error: ${e.message})", Toast.LENGTH_SHORT).show()
      }
    } else {
      android.util.Log.d(
        "MainActivity",
        "Widget $appWidgetId has no configuration, adding directly",
      )
      persistAddedWidget(appWidgetId)
    }
  }

  private var queryState by mutableStateOf("")
  private var currentScreenState by mutableStateOf(Screen.Search)
  private var pendingSettingsSection by mutableStateOf<String?>(null)
  private var focusTrigger by mutableStateOf(0L)

  private fun queryPrefs() = getSharedPreferences(Prefs.ActiveSearch.FILE, Context.MODE_PRIVATE)

  private fun updateQueryState(value: String) {
    queryState = value
    queryPrefs()
      .edit()
      .putString(KEY_ACTIVE_QUERY, value)
      .putLong(KEY_ACTIVE_QUERY_TIME, System.currentTimeMillis())
      .apply()
  }

  private fun clearQueryState() {
    queryState = ""
    queryPrefs().edit().remove(KEY_ACTIVE_QUERY).remove(KEY_ACTIVE_QUERY_TIME).apply()
  }

  private fun restoreRecentQuery(): String {
    val prefs = queryPrefs()
    val query = prefs.getString(KEY_ACTIVE_QUERY, null).orEmpty()
    val savedAt = prefs.getLong(KEY_ACTIVE_QUERY_TIME, 0L)
    val isRecent = System.currentTimeMillis() - savedAt <= ACTIVE_QUERY_RESTORE_WINDOW_MS
    return if (query.isNotBlank() && isRecent) query else ""
  }

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

  /**
   * Bumped only by an actual home intent, unlike [focusTrigger], which also moves whenever the
   * launcher merely regains focus — closing the search overlay, waking the screen.
   */
  private var homeTrigger by mutableStateOf(0L)

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
        "wallpaper",
        "browser" -> {
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

    if (
      intent.getBooleanExtra(EXTRA_FOCUS_SEARCH, false) ||
        (intent.hasCategory(Intent.CATEGORY_HOME) && intent.action == Intent.ACTION_MAIN)
    ) {
      clearQueryState()
      currentScreenState = Screen.Search
      pendingSettingsSection = null
      focusTrigger = System.currentTimeMillis()
      homeTrigger = System.currentTimeMillis()
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
    queryState = savedInstanceState?.getString(KEY_ACTIVE_QUERY) ?: restoreRecentQuery()
    enableEdgeToEdge()
    Ime.applyWindowMode(window)

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
    Ime.installWarmup(this)
  }

  override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
    if (keyShortcutHandler?.invoke(event) == true) return true
    return super.dispatchKeyEvent(event)
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    enterPipIfEligible()
  }

  override fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: android.content.res.Configuration,
  ) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    inPictureInPicture = isInPictureInPictureMode
  }

  override fun enterPipIfEligible(): Boolean {
    val view = pipVideoView ?: return false
    if (isInPictureInPictureMode) return false
    val width = view.width.coerceAtLeast(16)
    val height = view.height.coerceAtLeast(9)
    val params =
      android.app.PictureInPictureParams.Builder()
        .setAspectRatio(android.util.Rational(width, height))
        .build()
    return try {
      enterPictureInPictureMode(params)
    } catch (e: Exception) {
      android.util.Log.w("MainActivity", "PiP failed", e)
      false
    }
  }

  override fun onStart() {
    super.onStart()
    try {
      appWidgetHost.startListening()
    } catch (e: Exception) {
      // Widget host service might be unavailable after system kills our process
      // (e.g., Xiaomi's camera boost). Log but don't crash - widgets will recover on next restart.
      android.util.Log.e("MainActivity", "Failed to start widget host listening: ${e.message}")
    }

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
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus && currentScreenState == Screen.Search && !inPictureInPicture) {
      focusTrigger = System.currentTimeMillis()
      Ime.onWindowFocused(this)
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

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == REQUEST_CONFIGURE_APPWIDGET) {
      val appWidgetId = pendingConfigAppWidgetId
      pendingConfigAppWidgetId = -1

      android.util.Log.d(
        "MainActivity",
        "Widget config result: requestCode=$requestCode, resultCode=$resultCode, widgetId=$appWidgetId",
      )

      if (resultCode == RESULT_OK && appWidgetId != -1) {
        android.util.Log.d(
          "MainActivity",
          "Widget $appWidgetId configured successfully, adding to repository",
        )
        persistAddedWidget(appWidgetId)
      } else if (appWidgetId != -1) {
        android.util.Log.d(
          "MainActivity",
          "Widget $appWidgetId configuration cancelled, deleting ID",
        )
        appWidgetHost.deleteAppWidgetId(appWidgetId)
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(KEY_ACTIVE_QUERY, queryState)
  }

  private enum class Screen {
    Search,
    Settings,
    AppList,
  }

  @Composable
  private fun MainScreen() {
    val context = LocalContext.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Hoist wallpaper state
    // Wrapped so that "DataStore hasn't answered yet" is distinguishable from "no wallpaper
    // saved": both look like null, and treating the former as the latter made the background
    // open on the first image before correcting itself to the saved one.
    val savedWallpaper by
      remember {
          context.dataStore.data
            .map { SavedWallpaper(it[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI]) }
            .distinctUntilChanged()
        }
        .collectAsState(initial = null)
    val lastImageUriString = savedWallpaper?.uri

    val app = context.applicationContext as SearchLauncherApp
    // Passed straight to the background: mirroring this into local state made it start empty and
    // briefly render the no-wallpaper branch before the copying effect had run.
    val managedWallpapers by app.wallpaperRepository.wallpapers.collectAsState()

    // Handle back press
    BackHandler(enabled = currentScreenState != Screen.Search) {
      if (currentScreenState == Screen.Settings || currentScreenState == Screen.AppList) {
        currentScreenState = Screen.Search
        focusTrigger = System.currentTimeMillis()
      }
    }

    val isFirstRun by
      remember { context.dataStore.data.map { it[PreferencesKeys.IS_FIRST_RUN] ?: true } }
        .collectAsState(initial = false)

    // Starts as "already asked" so the offer cannot flash up in the frames before the stored
    // answer has been read back.
    val onboardingPermissionsAsked by
      remember {
          context.dataStore.data.map { it[PreferencesKeys.ONBOARDING_PERMISSIONS_ASKED] ?: false }
        }
        .collectAsState(initial = true)

    LaunchedEffect(isFirstRun, managedWallpapers.isEmpty(), onboardingPermissionsAsked) {
      if (isFirstRun) {
        context.dataStore.edit { it[PreferencesKeys.IS_FIRST_RUN] = false }
      }

      val photosGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
          context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
      val contactsGranted =
        context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
          android.content.pm.PackageManager.PERMISSION_GRANTED
      val calendarGranted =
        context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) ==
          android.content.pm.PackageManager.PERMISSION_GRANTED

      // Nothing to ask about: the wallpaper can just be taken.
      if (managedWallpapers.isEmpty() && photosGranted) {
        android.util.Log.d("MainActivity", "Permission already granted, importing wallpaper")
        val result = app.wallpaperRepository.addSystemWallpaper()
        android.util.Log.d("MainActivity", "System wallpaper import result: $result")
      }

      // Offer only what is actually missing, and only once. Someone who already granted both, or
      // who said no last time, is not asked again.
      val offerPhotos = managedWallpapers.isEmpty() && !photosGranted
      val offerContacts = !contactsGranted
      val offerCalendar = !calendarGranted
      if (!onboardingPermissionsAsked && (offerPhotos || offerContacts || offerCalendar)) {
        (context as? MainActivity)?.let {
          it.onboardingOffersPhotos = offerPhotos
          it.onboardingOffersContacts = offerContacts
          it.onboardingOffersCalendar = offerCalendar
          it.showOnboardingPermissions = true
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

    // Hide keyboard when opening App List
    LaunchedEffect(currentScreenState) {
      if (currentScreenState == Screen.AppList) {
        keyboardController?.hide()
        focusManager.clearFocus()
      }
    }

    if (showOnboardingPermissions) {
      var wantContacts by remember { mutableStateOf(true) }
      var wantPhotos by remember { mutableStateOf(true) }
      var wantCalendar by remember { mutableStateOf(true) }

      // Remembers that the offer was made whichever way it is answered, including a tap outside,
      // so nobody is asked twice.
      val closeOffer: (Boolean) -> Unit = { accepted ->
        showOnboardingPermissions = false
        lifecycleScope.launch {
          context.dataStore.edit { it[PreferencesKeys.ONBOARDING_PERMISSIONS_ASKED] = true }
        }
        val wanted =
          if (!accepted) emptyList()
          else
            buildList {
              if (onboardingOffersContacts && wantContacts) {
                add(android.Manifest.permission.READ_CONTACTS)
              }
              if (onboardingOffersCalendar && wantCalendar) {
                add(android.Manifest.permission.READ_CALENDAR)
              }
              if (onboardingOffersPhotos && wantPhotos) {
                add(android.Manifest.permission.READ_MEDIA_IMAGES)
              }
            }
        if (wanted.isNotEmpty()) {
          requestOnboardingPermissionsLauncher.launch(wanted.toTypedArray())
        }
      }

      AlertDialog(
        onDismissRequest = { closeOffer(false) },
        title = { Text("Search more than apps") },
        text = {
          Column {
            Text(
              "SearchLauncher finds your apps and settings straight away. A few things it can " +
                "only reach if you let it:"
            )
            if (onboardingOffersContacts) {
              Spacer(modifier = Modifier.height(16.dp))
              Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = wantContacts, onCheckedChange = { wantContacts = it })
                Column {
                  Text("Contacts")
                  Text(
                    "Type a name to call, message or mail someone.",
                    style = MaterialTheme.typography.bodySmall,
                  )
                }
              }
            }
            if (onboardingOffersCalendar) {
              Spacer(modifier = Modifier.height(8.dp))
              Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = wantCalendar, onCheckedChange = { wantCalendar = it })
                Column {
                  Text("Calendar")
                  Text(
                    "Find events coming up in the next week.",
                    style = MaterialTheme.typography.bodySmall,
                  )
                }
              }
            }
            if (onboardingOffersPhotos) {
              Spacer(modifier = Modifier.height(8.dp))
              Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = wantPhotos, onCheckedChange = { wantPhotos = it })
                Column {
                  Text("Wallpaper")
                  Text(
                    "Use the wallpaper you already have as the background.",
                    style = MaterialTheme.typography.bodySmall,
                  )
                }
              }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              "Android will ask about each one you tick. Everything stays on your phone, and you " +
                "can change your mind later in Settings.",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        },
        confirmButton = { TextButton(onClick = { closeOffer(true) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { closeOffer(false) }) { Text("Not now") } },
      )
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
      // Layer 1: the search screen, always composed. Settings used to be the other branch of
      // an AnimatedContent, which disposed this screen — and with it the hosted browser's WebView
      // and the wallpaper — on every settings visit. Coming back rebuilt the WebView, a stalled
      // frame during which nothing here draws and the window shows through: the wallpaper going
      // plain for a moment. The app list already worked as an overlay above a live search screen;
      // settings now does the same.
      SearchScreen(
        query = queryState,
        onQueryChange = { updateQueryState(it) },
        onDismiss = { clearQueryState() },
        onOpenSettings = {
          keyboardController?.hide()
          currentScreenState = Screen.Settings
        },
        onOpenAppDrawer = { currentScreenState = Screen.AppList },
        searchRepository = app.searchRepository,
        focusTrigger = focusTrigger,
        showBackgroundImage = true,
        folderImages = managedWallpapers,
        lastImageUriString = lastImageUriString,
        savedUriResolved = savedWallpaper != null,
        onAddWidget = { requestWidgetPick() },
        isActive = currentScreenState == Screen.Search,
        browserTabSwipeEnabled = true,
        homeTrigger = homeTrigger,
      )
      // Layer 1b: Settings overlay. A fade, as the old screen switch was.
      androidx.compose.animation.AnimatedVisibility(
        visible = currentScreenState == Screen.Settings,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
        modifier = Modifier.fillMaxSize(),
      ) {
        // On its own opaque sheet: the settings cards do not paint edge to edge, and the search
        // screen is alive underneath now rather than disposed, so without this the wallpaper and
        // widgets showed through the gaps.
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
        ) {
          SettingsScreen(
            onBack = { currentScreenState = Screen.Search },
            initialHighlightSection = pendingSettingsSection,
            onExportBackup = { initiateExportBackup() },
          )
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
            ResultLauncher(
                context = context,
                searchRepository = app.searchRepository,
                scope = lifecycleScope,
                onBindWidgetIntent = { intent ->
                  handleWidgetIntent(intent)
                  true
                },
                onAddWidgetSearch = {
                  updateQueryState("widgets ")
                  focusTrigger = System.currentTimeMillis()
                },
              )
              .launch(result, reportUsage = false)
            if (result !is SearchResult.PrivateSpace) {
              currentScreenState = Screen.Search
              clearQueryState()
            }
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

  companion object {
    /**
     * Asks for the same clean, focused search screen pressing Home gives. Sent by the browser when
     * the user swipes back here, so the field is focused and the keyboard on its way up as the
     * launcher draws rather than a beat afterwards.
     */
    const val EXTRA_FOCUS_SEARCH = "focus_search"

    private const val KEY_ACTIVE_QUERY = Prefs.ActiveSearch.QUERY
    private const val KEY_ACTIVE_QUERY_TIME = Prefs.ActiveSearch.QUERY_TIME
    private const val ACTIVE_QUERY_RESTORE_WINDOW_MS = 5 * 60 * 1000L
  }
}

private fun MainActivity.createBackupManager(): com.searchlauncher.app.data.BackupManager {
  val app = applicationContext as SearchLauncherApp
  return com.searchlauncher.app.data.BackupManager(
    context = this,
    snippetsRepository = app.snippetsRepository,
    searchShortcutRepository = app.searchShortcutRepository,
    favoritesRepository = app.favoritesRepository,
    historyRepository = app.historyRepository,
    wallpaperRepository = app.wallpaperRepository,
    widgetRepository = app.widgetRepository,
  )
}

private suspend fun MainActivity.performExport(uri: android.net.Uri) {
  withContext(Dispatchers.Main) {
    Toast.makeText(this@performExport, "Exporting backup...", Toast.LENGTH_SHORT).show()
  }
  withContext(Dispatchers.IO) {
    try {
      val backupManager = createBackupManager()

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
      val backupManager = createBackupManager()

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
