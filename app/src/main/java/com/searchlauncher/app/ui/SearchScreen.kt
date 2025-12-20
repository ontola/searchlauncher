package com.searchlauncher.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.service.GestureAccessibilityService
import com.searchlauncher.app.ui.components.FavoritesRow
import com.searchlauncher.app.ui.components.SearchResultItem
import com.searchlauncher.app.ui.components.ShortcutDialog
import com.searchlauncher.app.ui.components.SnippetDialog
import com.searchlauncher.app.ui.components.WallpaperBackground
import com.searchlauncher.app.ui.onboarding.OnboardingManager
import com.searchlauncher.app.ui.onboarding.OnboardingStep
import com.searchlauncher.app.ui.onboarding.TutorialOverlay
import com.searchlauncher.app.ui.theme.SearchLauncherTheme
import com.searchlauncher.app.util.CustomActionHandler
import com.searchlauncher.app.util.MathEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(
  query: String,
  onQueryChange: (String) -> Unit,
  onDismiss: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenAppDrawer: () -> Unit,
  searchRepository: SearchRepository,
  focusTrigger: Long = 0L,
  showHistory: Boolean = true,
  showBackgroundImage: Boolean = false,
  folderImages: List<Uri> = emptyList(),
  lastImageUriString: String? = null,
  onAddWidget: () -> Unit = {},
  isActive: Boolean = true,
) {
  var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
  var isLoading by remember { mutableStateOf(false) }
  var isFallbackMode by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val app = context.applicationContext as com.searchlauncher.app.SearchLauncherApp
  val scope = rememberCoroutineScope()
  val focusRequester = remember { FocusRequester() }
  val favoriteIds by app.favoritesRepository.favoriteIds.collectAsState()
  val isSearchInitialized by searchRepository.isInitialized.collectAsState(initial = false)
  val isIndexing by searchRepository.isIndexing.collectAsState(initial = false)
  val backgroundUriString by
    remember { context.dataStore.data.map { it[MainActivity.PreferencesKeys.BACKGROUND_URI] } }
      .collectAsState(initial = null)
  val backgroundFolderUriString by
    remember {
        context.dataStore.data.map { it[MainActivity.PreferencesKeys.BACKGROUND_FOLDER_URI] }
      }
      .collectAsState(initial = null)

  val favorites by searchRepository.favorites.collectAsState()
  var showSnippetDialog by remember { mutableStateOf(false) }
  var snippetEditMode by remember { mutableStateOf(false) }
  var snippetItemToEdit by remember { mutableStateOf<SearchResult.Snippet?>(null) }
  val searchShortcuts by app.searchShortcutRepository.items.collectAsState()
  var showShortcutDialog by remember { mutableStateOf(false) }
  var editingShortcut by remember {
    mutableStateOf<com.searchlauncher.app.data.SearchShortcut?>(null)
  }
  val listState = androidx.compose.foundation.lazy.rememberLazyListState()

  // Onboarding Logic
  val onboardingManager = remember { OnboardingManager(context) }
  val completedSteps by onboardingManager.completedSteps.collectAsState(initial = emptySet())

  // Determine current step
  val currentOnboardingStep =
    remember(completedSteps, query) {
      if (query.isNotEmpty()) {
        if (!completedSteps.contains(OnboardingStep.AddFavorite) && searchResults.isNotEmpty())
          OnboardingStep.AddFavorite
        else null
      } else {
        if (!completedSteps.contains(OnboardingStep.SwipeBackground) && folderImages.size > 1)
          OnboardingStep.SwipeBackground
        else if (!completedSteps.contains(OnboardingStep.SwipeNotifications))
          OnboardingStep.SwipeNotifications
        else if (!completedSteps.contains(OnboardingStep.SwipeQuickSettings))
          OnboardingStep.SwipeQuickSettings
        else if (!completedSteps.contains(OnboardingStep.SwipeAppDrawer))
          OnboardingStep.SwipeAppDrawer
        else if (!completedSteps.contains(OnboardingStep.LongPressBackground))
          OnboardingStep.LongPressBackground
        else if (!completedSteps.contains(OnboardingStep.SearchYoutube))
          OnboardingStep.SearchYoutube
        else if (!completedSteps.contains(OnboardingStep.SearchGoogle)) OnboardingStep.SearchGoogle
        else if (!completedSteps.contains(OnboardingStep.ReorderFavorites) && favorites.size >= 2)
          OnboardingStep.ReorderFavorites
        // AddFavorite is situational, shown when search results exist
        else null
      }
    }

  // Effect to mark steps complete based on state
  LaunchedEffect(query, completedSteps) {
    if (
      !completedSteps.contains(OnboardingStep.SearchYoutube) &&
        query.trimStart().startsWith("y ", ignoreCase = true)
    ) {
      onboardingManager.markStepComplete(OnboardingStep.SearchYoutube)
    }

    if (
      !completedSteps.contains(OnboardingStep.SearchGoogle) &&
        query.trimStart().startsWith("g ", ignoreCase = true)
    ) {
      onboardingManager.markStepComplete(OnboardingStep.SearchGoogle)
    }
  }

  LaunchedEffect(searchResults) {
    if (searchResults.isNotEmpty()) {
      listState.scrollToItem(0)
    }
  }

  val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
  val isImeVisible =
    WindowInsets.ime.getBottom(LocalDensity.current) >
      0 // Monitor IME visibility and force show if it hides while we are active
  LaunchedEffect(isImeVisible, isActive) {
    if (isActive && !isImeVisible) {
      // If keyboard dismissed, bring it back
      focusRequester.requestFocus()
      keyboardController?.show()
    }
  }

  // Ensure keyboard is open when this screen is active or refocused
  LaunchedEffect(isActive, focusTrigger) {
    if (isActive) {
      focusRequester.requestFocus()
      keyboardController?.show()
    }
  }
  // Refresh favorites from the actual index once it's ready.
  // The UI will already show cached favorites from the StateFlow immediately.
  LaunchedEffect(favoriteIds, isSearchInitialized) {
    if (isSearchInitialized && favoriteIds.isNotEmpty()) {
      searchRepository.getFavorites(favoriteIds)
    }
  }

  LaunchedEffect(query, showHistory, favoriteIds) {
    if (query.isEmpty()) {
      searchResults =
        if (showHistory) {
          searchRepository.getRecentItems(limit = 10, excludedIds = favoriteIds.toSet())
        } else {
          emptyList()
        }
      isFallbackMode = false
    } else {
      // Small debounce to avoid thrashing AppSearch on fast typing
      kotlinx.coroutines.delay(50)
      val results = searchRepository.searchApps(query)
      android.util.Log.d("SearchScreen", "Query: '$query', Results: ${results.size}")

      // Always append search shortcuts to the end of the results
      // Use a higher limit to show all options as requested
      val shortcuts = searchRepository.getSearchShortcuts(limit = 50)

      val resultIds = results.map { it.id }.toSet()
      val uniqueShortcuts = shortcuts.filter { !resultIds.contains(it.id) }
      val baseResults = results + uniqueShortcuts
      isFallbackMode = results.isEmpty()

      // Calculator injection
      if (MathEvaluator.isExpression(query)) {
        val eval = MathEvaluator.evaluate(query)
        if (eval != null) {
          // Round to avoid long decimals if possible, or show as is
          val formattedResult = if (eval % 1.0 == 0.0) eval.toLong().toString() else eval.toString()
          val calcResult =
            SearchResult.Content(
              id = "calculator_result",
              namespace = "calculator",
              title = formattedResult,
              subtitle = "Calculation result (Tap to copy)",
              icon = null,
              packageName = "android",
              deepLink = "calculator://copy?text=$formattedResult",
            )
          searchResults = listOf(calcResult) + baseResults
        } else {
          searchResults = baseResults
        }
      } else {
        searchResults = baseResults
      }
    }
  }

  // Pre-emptive cache warming when idle
  LaunchedEffect(query) {
    if (query.isEmpty()) {
      // Wait for 2 seconds of idleness (user just staring at screen)
      kotlinx.coroutines.delay(2000)
      // Start warming up the cache
      searchRepository.warmupCache()
    }
  }

  // Hint Logic
  val snippetItems by app.snippetsRepository.items.collectAsState()

  // Check if this app is the default launcher
  val isDefaultLauncher = remember {
    val intent = Intent(Intent.ACTION_MAIN)
    intent.addCategory(Intent.CATEGORY_HOME)
    val resolveInfo = context.packageManager.resolveActivity(intent, 0)
    resolveInfo?.activityInfo?.packageName == context.packageName
  }

  // Check if contacts permission is granted
  val hasContactsPermission = remember {
    context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
      android.content.pm.PackageManager.PERMISSION_GRANTED
  }

  val hintManager =
    remember(folderImages, snippetItems, isDefaultLauncher, hasContactsPermission) {
      HintManager(
        isWallpaperFolderSet = { folderImages.isNotEmpty() },
        isSnippetsSet = { snippetItems.isNotEmpty() },
        isDefaultLauncher = { isDefaultLauncher },
        isContactsAccessGranted = { hasContactsPermission },
      )
    }
  var currentHint by remember { mutableStateOf("Search apps and content…") }

  LaunchedEffect(hintManager) { hintManager.getHintsFlow().collect { hint -> currentHint = hint } }

  val themeColor by
    remember {
        context.dataStore.data.map {
          it[MainActivity.PreferencesKeys.THEME_COLOR] ?: 0xFF5E6D4E.toInt()
        }
      }
      .collectAsState(initial = 0xFF5E6D4E.toInt())
  val themeSaturation by
    remember {
        context.dataStore.data.map { it[MainActivity.PreferencesKeys.THEME_SATURATION] ?: 50f }
      }
      .collectAsState(initial = 50f)
  val darkMode by
    remember { context.dataStore.data.map { it[MainActivity.PreferencesKeys.DARK_MODE] ?: 0 } }
      .collectAsState(initial = 0)
  val isOled by
    remember { context.dataStore.data.map { it[MainActivity.PreferencesKeys.OLED_MODE] ?: false } }
      .collectAsState(initial = false)

  // Use SharedPreferences for synchronous read to avoid initial jump
  val sharedPrefs = remember { context.getSharedPreferences("window_prefs", Context.MODE_PRIVATE) }
  val density = LocalDensity.current
  val imeHeightPx = WindowInsets.ime.getBottom(density)

  // Read synchronously for initial value
  var storedKeyboardHeight by remember { mutableStateOf(sharedPrefs.getInt("keyboard_height", 0)) }

  val isMultiWindow = (context as? android.app.Activity)?.isInMultiWindowMode == true

  LaunchedEffect(imeHeightPx) {
    if (imeHeightPx > 100 && !isMultiWindow) {
      // Wait for animation to settle (debounce)
      kotlinx.coroutines.delay(300)
      // If we are still active (didn't get cancelled by new value), save it
      storedKeyboardHeight = imeHeightPx
      sharedPrefs.edit().putInt("keyboard_height", imeHeightPx).apply()
    }
  }

  // The effective padding is the max of current IME or stored IME height
  // In multi-window/floating mode, we ignore stored height to avoid unnecessary gaps
  val bottomPadding =
    with(density) {
      if (isMultiWindow) {
        imeHeightPx.toDp()
      } else {
        kotlin.math.max(imeHeightPx, storedKeyboardHeight).toDp()
      }
    }

  SearchLauncherTheme(
    themeColor = themeColor,
    darkThemeMode = darkMode,
    chroma = themeSaturation,
    isOled = isOled,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      val launcher =
        androidx.activity.compose.rememberLauncherForActivityResult(
          contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
          onResult = { uri: Uri? ->
            uri?.let {
              val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
              context.contentResolver.takePersistableUriPermission(it, flag)
              scope.launch {
                context.dataStore.edit { preferences ->
                  preferences[MainActivity.PreferencesKeys.BACKGROUND_URI] = it.toString()
                  preferences.remove(MainActivity.PreferencesKeys.BACKGROUND_FOLDER_URI)
                }
              }
            }
          },
        )

      val folderLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
          contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
          onResult = { uri: Uri? ->
            uri?.let {
              val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
              context.contentResolver.takePersistableUriPermission(it, flag)
              scope.launch {
                context.dataStore.edit { preferences ->
                  preferences[MainActivity.PreferencesKeys.BACKGROUND_FOLDER_URI] = it.toString()
                  preferences.remove(MainActivity.PreferencesKeys.BACKGROUND_URI)
                }
              }
            }
          },
        )

      var isListening by remember { mutableStateOf(false) }
      val speechRecognizer: android.speech.SpeechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context)
      }

      val permissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
          androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted ->
          if (isGranted) {
            try {
              val intent =
                Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                  putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                  )
                  putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
              speechRecognizer.startListening(intent)
              isListening = true
            } catch (e: Exception) {
              Toast.makeText(context, "Voice search error", Toast.LENGTH_SHORT).show()
              isListening = false
            }
          } else {
            Toast.makeText(context, "Permission needed for voice search", Toast.LENGTH_SHORT).show()
          }
        }

      DisposableEffect(Unit) {
        val listener: android.speech.RecognitionListener =
          object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
              isListening = false
            }

            override fun onError(error: Int) {
              isListening = false
            }

            override fun onResults(results: Bundle?) {
              val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              if (!matches.isNullOrEmpty()) {
                onQueryChange(matches[0])
              }
              isListening = false
            }

            override fun onPartialResults(partialResults: Bundle?) {
              // Optional: update query in real-time
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
          }
        speechRecognizer.setRecognitionListener(listener)
        onDispose { speechRecognizer.destroy() }
      }

      var showBackgroundMenu by remember { mutableStateOf(false) }
      var menuOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

      Box(modifier = Modifier.fillMaxSize()) {
        WallpaperBackground(
          showBackgroundImage = showBackgroundImage,
          bottomPadding = bottomPadding,
          folderImages = folderImages,
          lastImageUriString = lastImageUriString,
          modifier = Modifier.fillMaxSize(),
          onOpenAppDrawer = {
            scope.launch { onboardingManager.markStepComplete(OnboardingStep.SwipeAppDrawer) }
            onOpenAppDrawer()
          },
          onLongPress = { offset ->
            menuOffset = offset
            showBackgroundMenu = true
            scope.launch { onboardingManager.markStepComplete(OnboardingStep.LongPressBackground) }
          },
          onTap = {
            onDismiss()
            // Tapping background also swipes? No, just dismiss.
            // But if we want to complete "Swipe Background", we need to detect the swipe in
            // WallpaperBackground
          },
          onPageChanged = {
            scope.launch { onboardingManager.markStepComplete(OnboardingStep.SwipeBackground) }
          },
          onSwipeDownLeft = {
            scope.launch { onboardingManager.markStepComplete(OnboardingStep.SwipeNotifications) }
            if (!com.searchlauncher.app.service.GestureAccessibilityService.openNotifications()) {
              com.searchlauncher.app.util.SystemUtils.expandNotifications(context)
            }
          },
          onSwipeDownRight = {
            scope.launch { onboardingManager.markStepComplete(OnboardingStep.SwipeQuickSettings) }
            if (!com.searchlauncher.app.service.GestureAccessibilityService.openQuickSettings()) {
              com.searchlauncher.app.util.SystemUtils.expandQuickSettings(context)
            }
          },
        )

        TutorialOverlay(
          currentStep = currentOnboardingStep,
          bottomPadding = bottomPadding,
          onDismissStep = { /* optional manual dismiss */ },
        )
      }

      if (showBackgroundMenu) {
        DropdownMenu(
          expanded = showBackgroundMenu,
          onDismissRequest = { showBackgroundMenu = false },
          offset =
            androidx.compose.ui.unit.DpOffset(
              x = with(LocalDensity.current) { menuOffset.x.toDp() },
              y = with(LocalDensity.current) { menuOffset.y.toDp() },
            ),
        ) {
          DropdownMenuItem(
            text = { Text("Set Image") },
            onClick = {
              showBackgroundMenu = false
              launcher.launch(arrayOf("image/*"))
            },
          )
          DropdownMenuItem(
            text = { Text("Set Folder") },
            onClick = {
              showBackgroundMenu = false
              folderLauncher.launch(null)
            },
          )
          DropdownMenuItem(
            text = { Text("Add Widget") },
            onClick = {
              showBackgroundMenu = false
              onAddWidget()
            },
          )
          val widgets by app.widgetRepository.widgets.collectAsState(initial = emptyList())
          if (widgets.isNotEmpty()) {
            DropdownMenuItem(
              text = { Text("Clear Widgets") },
              onClick = {
                showBackgroundMenu = false
                val idsToClear = widgets.map { it.id } // Copy list of IDs
                scope.launch {
                  // Remove from Repo
                  app.widgetRepository.clearAllWidgets()

                  // Remove from Host
                  val activity = context as? com.searchlauncher.app.ui.MainActivity
                  activity?.let { act ->
                    idsToClear.forEach { id -> act.appWidgetHost.deleteAppWidgetId(id) }
                  }
                  Toast.makeText(context, "Widgets cleared", Toast.LENGTH_SHORT).show()
                }
              },
              leadingIcon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null) },
            )
          }
          if (backgroundUriString != null || backgroundFolderUriString != null) {
            DropdownMenuItem(
              text = { Text("Use default backgrounds") },
              onClick = {
                showBackgroundMenu = false
                scope.launch {
                  context.dataStore.edit { preferences ->
                    preferences.remove(MainActivity.PreferencesKeys.BACKGROUND_URI)
                    preferences.remove(MainActivity.PreferencesKeys.BACKGROUND_FOLDER_URI)
                    preferences.remove(MainActivity.PreferencesKeys.BACKGROUND_LAST_IMAGE_URI)
                  }
                }
              },
            )
          }
        }
      }

      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(bottom = bottomPadding) // Push content up by reserved space
            .padding(top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.Bottom,
      ) {
        if (searchResults.isNotEmpty()) {
          Surface(
            modifier =
              Modifier.fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = 16.dp)
                .clickable(
                  indication = null,
                  interactionSource = remember { MutableInteractionSource() },
                ) {},
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
          ) {
            if (isLoading) {
              Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator()
              }
            } else {
              LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 8.dp),
              ) {
                itemsIndexed(searchResults, key = { _, item -> "${item.namespace}/${item.id}" }) {
                  index,
                  result ->
                  SearchResultItem(
                    result = result,
                    isFavorite = favoriteIds.contains(result.id),
                    onToggleFavorite =
                      if (result is SearchResult.App) {
                        {
                          app.favoritesRepository.toggleFavorite(result.id)
                          onQueryChange("")
                          scope.launch {
                            onboardingManager.markStepComplete(OnboardingStep.AddFavorite)
                          }
                        }
                      } else null,
                    onEditSnippet =
                      if (result is SearchResult.Snippet) {
                        {
                          snippetItemToEdit = result
                          snippetEditMode = true
                          showSnippetDialog = true
                        }
                      } else null,
                    onEditShortcut =
                      if (result is SearchResult.Shortcut) {
                        {
                          val shortcut = searchShortcuts.find { it.id == result.id }
                          if (shortcut != null) {
                            editingShortcut = shortcut
                            showShortcutDialog = true
                          }
                        }
                      } else if (result is SearchResult.SearchIntent) {
                        {
                          val shortcut = searchShortcuts.find { it.alias == result.trigger }
                          if (shortcut != null) {
                            editingShortcut = shortcut
                            showShortcutDialog = true
                          }
                        }
                      } else if (
                        result is SearchResult.Content && result.namespace == "search_shortcuts"
                      ) {
                        {
                          val alias = result.id.removePrefix("shortcut_")
                          val shortcut = searchShortcuts.find { it.alias == alias }
                          if (shortcut != null) {
                            editingShortcut = shortcut
                            showShortcutDialog = true
                          }
                        }
                      } else null,
                    onDeleteShortcut =
                      if (result is SearchResult.Shortcut) {
                        {
                          scope.launch {
                            app.searchShortcutRepository.removeShortcut(result.id)
                            Toast.makeText(context, "Shortcut removed", Toast.LENGTH_SHORT).show()
                          }
                        }
                      } else if (result is SearchResult.SearchIntent) {
                        {
                          scope.launch {
                            // Find the shortcut first to get its ID
                            val shortcut = searchShortcuts.find { it.alias == result.trigger }
                            if (shortcut != null) {
                              app.searchShortcutRepository.removeShortcut(shortcut.id)
                              Toast.makeText(context, "Shortcut removed", Toast.LENGTH_SHORT).show()
                            }
                          }
                        }
                      } else if (
                        result is SearchResult.Content && result.namespace == "search_shortcuts"
                      ) {
                        {
                          scope.launch {
                            val alias = result.id.removePrefix("shortcut_")
                            val shortcut = searchShortcuts.find { it.alias == alias }
                            if (shortcut != null) {
                              app.searchShortcutRepository.removeShortcut(shortcut.id)
                              Toast.makeText(context, "Shortcut removed", Toast.LENGTH_SHORT).show()
                            }
                          }
                        }
                      } else null,
                    onCreateSnippet = {
                      snippetEditMode = false
                      showSnippetDialog = true
                    },
                    onClick = {
                      if (result is SearchResult.SearchIntent) {
                        // If the title implies a direct search (or we
                        // are in fallback mode with query), perform
                        // search
                        // OR if the result title was modified to
                        // include "Search ... on ..."
                        // A better check: if query is not empty AND
                        // it's not just the trigger itself.
                        // The logic below handles both cases.

                        // If it's a "Search X on Y" action (inferred
                        // from query context)
                        if (
                          query.isNotEmpty() &&
                            !result.trigger.equals(query.trim(), ignoreCase = true) &&
                            !result.title.contains(query.trim(), ignoreCase = true)
                        ) {
                          // Perform Search
                          val shortcut =
                            app.searchShortcutRepository.items.value
                              .filterIsInstance<com.searchlauncher.app.data.SearchShortcut>()
                              .find { it.alias == result.trigger }

                          if (shortcut != null) {
                            try {
                              val url =
                                shortcut.urlTemplate.replace(
                                  "%s",
                                  java.net.URLEncoder.encode(query, "UTF-8"),
                                )
                              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                              intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                              context.startActivity(intent)
                              scope.launch {
                                searchRepository.reportUsage(
                                  result.namespace,
                                  result.id,
                                  query,
                                  index == 0,
                                )
                              }
                              onDismiss()
                            } catch (e: Exception) {
                              Toast.makeText(
                                  context,
                                  "Cannot open: ${result.title}",
                                  Toast.LENGTH_SHORT,
                                )
                                .show()
                            }
                          }
                        } else {
                          // Enter sub-search mode (append trigger)
                          onQueryChange(result.trigger + " ")
                        }
                      } else {
                        if (
                          result is SearchResult.Content &&
                            result.deepLink ==
                              "intent:#Intent;action=com.searchlauncher.action.CREATE_SNIPPET;end"
                        ) {
                          snippetEditMode = false
                          showSnippetDialog = true
                        } else if (
                          result is SearchResult.Content &&
                            result.deepLink ==
                              "intent:#Intent;action=com.searchlauncher.action.ADD_WIDGET;end"
                        ) {
                          onAddWidget()
                        } else if (
                          (result is SearchResult.Content &&
                            result.deepLink ==
                              "intent:#Intent;action=com.searchlauncher.action.RESET_ONBOARDING;end") ||
                            (result is SearchResult.Shortcut &&
                              result.intentUri ==
                                "intent:#Intent;action=com.searchlauncher.action.RESET_ONBOARDING;end")
                        ) {
                          scope.launch {
                            onboardingManager.resetOnboarding()
                            onDismiss()
                          }
                        } else {
                          launchResult(
                            context,
                            result,
                            searchRepository,
                            scope,
                            query,
                            index == 0,
                            onQueryChange,
                          )
                          if (
                            result is SearchResult.Content &&
                              result.deepLink ==
                                "intent:#Intent;action=com.searchlauncher.action.APPEND_SPACE;end"
                          ) {
                            // Do nothing (keep search open)
                          } else {
                            onDismiss()
                          }
                        }
                      }
                    },
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
        }

        if (query.isEmpty() && favorites.isNotEmpty()) {
          FavoritesRow(
            favorites = favorites,
            onLaunch = { result ->
              launchResult(context, result, searchRepository, scope, onQueryChange = onQueryChange)
              onDismiss()
            },
            onRemoveFavorite = { result -> app.favoritesRepository.toggleFavorite(result.id) },
            onReorder = { newOrder ->
              app.favoritesRepository.updateOrder(newOrder)
              scope.launch { onboardingManager.markStepComplete(OnboardingStep.ReorderFavorites) }
            },
          )
          Spacer(modifier = Modifier.height(4.dp))
        }

        Surface(
          modifier =
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(
              indication = null,
              interactionSource = remember { MutableInteractionSource() },
            ) {},
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 3.dp,
        ) {
          Box {
            androidx.compose.animation.AnimatedVisibility(
              visible = isIndexing,
              enter = fadeIn(),
              exit = fadeOut(),
              modifier = Modifier.align(Alignment.TopCenter),
            ) {
              LinearProgressIndicator(
                modifier =
                  Modifier.fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = androidx.compose.ui.graphics.Color.Transparent,
              )
            }
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 40.dp)
                  .padding(horizontal = 16.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              val activeShortcut =
                remember(query) {
                  var shortcut =
                    app.searchShortcutRepository.items.value.find {
                      query.startsWith("${it.alias} ", ignoreCase = true)
                    }
                  if (shortcut == null) {
                    shortcut =
                      com.searchlauncher.app.data.DefaultShortcuts.searchShortcuts.find {
                        query.startsWith("${it.alias} ", ignoreCase = true)
                      }
                  }
                  shortcut
                }

              if (activeShortcut != null) {
                Surface(
                  color = androidx.compose.ui.graphics.Color(activeShortcut.color ?: 0xFF808080),
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.padding(end = 8.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  ) {
                    val defaultShortcut =
                      com.searchlauncher.app.data.DefaultShortcuts.searchShortcuts.find {
                        it.alias == activeShortcut.alias
                      }
                    val label =
                      (activeShortcut.shortLabel
                          ?: defaultShortcut?.shortLabel
                          ?: activeShortcut.description)
                        .replace("Search ", "", ignoreCase = true)
                        .replace("Ask ", "", ignoreCase = true)
                        .trim()
                    Text(
                      text = label,
                      color = androidx.compose.ui.graphics.Color.White,
                      fontSize = 14.sp,
                      fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    )
                  }
                }
              }

              val displayQuery =
                if (activeShortcut != null) {
                  query.substring("${activeShortcut.alias} ".length)
                } else {
                  query
                }

              var textFieldValue by remember {
                mutableStateOf(
                  androidx.compose.ui.text.input.TextFieldValue(
                    text = displayQuery,
                    selection = androidx.compose.ui.text.TextRange(displayQuery.length),
                  )
                )
              }

              // Update TextFieldValue when displayQuery changes externally (e.g. from "Add Widget")
              LaunchedEffect(displayQuery) {
                if (textFieldValue.text != displayQuery) {
                  textFieldValue =
                    textFieldValue.copy(
                      text = displayQuery,
                      selection = androidx.compose.ui.text.TextRange(displayQuery.length),
                    )
                }
              }

              BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                  textFieldValue = newValue
                  val newText = newValue.text
                  if (activeShortcut != null) {
                    onQueryChange("${activeShortcut.alias} $newText")
                  } else {
                    onQueryChange(newText)
                  }
                },
                modifier =
                  Modifier.weight(1f).focusRequester(focusRequester).onKeyEvent { event ->
                    if (
                      event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DEL &&
                        displayQuery.isEmpty() &&
                        activeShortcut != null
                    ) {
                      onQueryChange("")
                      true
                    } else {
                      false
                    }
                  },
                textStyle =
                  LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                  ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions =
                  KeyboardActions(
                    onGo = {
                      val topResult = searchResults.firstOrNull()
                      if (topResult != null) {
                        if (topResult is SearchResult.SearchIntent) {
                          if (isFallbackMode && query.isNotEmpty()) {
                            // In fallback mode (e.g. random
                            // text), 'Go' should perform the
                            // search
                            // using the top shortcut, instead
                            // of just expanding the filter.
                            val shortcut =
                              app.searchShortcutRepository.items.value.find {
                                it.alias == topResult.trigger
                              }

                            if (shortcut != null) {
                              try {
                                val url =
                                  shortcut.urlTemplate.replace(
                                    "%s",
                                    java.net.URLEncoder.encode(query, "UTF-8"),
                                  )
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                scope.launch {
                                  searchRepository.reportUsage(
                                    topResult.namespace,
                                    topResult.id,
                                    query,
                                    true,
                                  )
                                }
                                onDismiss()
                              } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Cannot open: ${topResult.title}",
                                    Toast.LENGTH_SHORT,
                                  )
                                  .show()
                              }
                            } else {
                              // Should not happen if data
                              // integrity is good, but
                              // fallback:
                              onQueryChange(topResult.trigger + " ")
                            }
                          } else {
                            // Normal mode: pressing enter on a
                            // shortcut expands it (sub-search)
                            onQueryChange(topResult.trigger + " ")
                          }
                        } else {
                          launchResult(
                            context,
                            topResult,
                            searchRepository,
                            scope,
                            onQueryChange = onQueryChange,
                          )
                          onDismiss()
                        }
                      }
                    }
                  ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                  Box(contentAlignment = Alignment.CenterStart) {
                    if (displayQuery.isEmpty() && activeShortcut == null) {
                      if (isListening) {
                        Text(
                          text = "Listening...",
                          color = MaterialTheme.colorScheme.primary,
                          fontSize = 16.sp,
                          maxLines = 1,
                          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                      } else {
                        AnimatedContent(
                          targetState = currentHint,
                          transitionSpec = { fadeIn() togetherWith fadeOut() },
                          label = "HintAnimation",
                        ) { targetHint ->
                          Text(
                            text = targetHint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                          )
                        }
                      }
                    }
                    innerTextField()
                  }
                },
              )

              if (query.isNotEmpty()) {
                IconButton(
                  onClick = { onQueryChange("") },
                  modifier = Modifier.size(32.dp).padding(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                  )
                }
              } else {
                IconButton(
                  onClick = {
                    if (isListening) {
                      speechRecognizer.stopListening()
                      isListening = false
                    } else {
                      if (
                        context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                          android.content.pm.PackageManager.PERMISSION_GRANTED
                      ) {
                        try {
                          val intent =
                            Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                              putExtra(
                                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                              )
                              putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            }
                          speechRecognizer.startListening(intent)
                          isListening = true
                        } catch (e: Exception) {
                          Toast.makeText(context, "Voice search error", Toast.LENGTH_SHORT).show()
                          isListening = false
                        }
                      } else {
                        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                      }
                    }
                  },
                  modifier = Modifier.size(32.dp).padding(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Search",
                    tint =
                      if (isListening) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurface,
                  )
                }

                IconButton(
                  onClick = onOpenSettings,
                  modifier = Modifier.size(32.dp).padding(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface,
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (showSnippetDialog) {
    SnippetDialog(
      initialAlias =
        if (snippetEditMode && snippetItemToEdit != null) snippetItemToEdit!!.alias else "",
      initialContent =
        if (snippetEditMode && snippetItemToEdit != null) snippetItemToEdit!!.content else "",
      isEditMode = snippetEditMode,
      onDismiss = { showSnippetDialog = false },
      onConfirm = { alias, content ->
        scope.launch(Dispatchers.IO) {
          if (snippetEditMode && snippetItemToEdit != null) {
            app.snippetsRepository.updateItem(snippetItemToEdit!!.alias, alias, content)
          } else {
            app.snippetsRepository.addItem(alias, content)
          }
          app.searchRepository.indexSnippets()
        }
        showSnippetDialog = false
      },
    )
  }

  if (showShortcutDialog) {
    ShortcutDialog(
      shortcut = editingShortcut,
      existingAliases =
        searchShortcuts.map { it.alias } +
          com.searchlauncher.app.data.DefaultShortcuts.searchShortcuts.map { it.alias },
      onDismiss = { showShortcutDialog = false },
      onSave = { newShortcut ->
        scope.launch {
          if (editingShortcut != null) {
            app.searchShortcutRepository.updateShortcut(newShortcut)
          } else {
            app.searchShortcutRepository.addShortcut(newShortcut)
          }
          showShortcutDialog = false
          app.searchRepository.indexCustomShortcuts()
        }
      },
    )
  }
}

private fun launchResult(
  context: Context,
  result: SearchResult,
  searchRepository: SearchRepository,
  scope: kotlinx.coroutines.CoroutineScope,
  query: String = "",
  wasFirstResult: Boolean = false,
  onQueryChange: ((String) -> Unit)? = null,
) {
  when (result) {
    is SearchResult.App -> {
      val launchIntent = context.packageManager.getLaunchIntentForPackage(result.packageName)
      launchIntent?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(it)
      }
    }
    is SearchResult.Content -> {
      if (result.deepLink?.startsWith("calculator://copy") == true) {
        val textToCopy = Uri.parse(result.deepLink).getQueryParameter("text") ?: result.title
        val clipboard =
          context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Calculator Result", textToCopy)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        return
      }
      result.deepLink?.let { deepLink ->
        try {
          val intent =
            if (deepLink.startsWith("intent:")) {
              Intent.parseUri(deepLink, Intent.URI_INTENT_SCHEME)
            } else {
              Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
            }

          if (intent.action == "com.searchlauncher.action.BIND_WIDGET") {
            (context as? MainActivity)?.handleWidgetIntent(intent)
              ?: run {
                Toast.makeText(
                    context,
                    "Cannot bind widget: Activity not found",
                    Toast.LENGTH_SHORT,
                  )
                  .show()
              }
          } else if (intent.action == "com.searchlauncher.action.APPEND_SPACE") {
            onQueryChange?.invoke(query + " ")
          } else if (intent.action == "com.searchlauncher.RESET_INDEX") {
            scope.launch {
              searchRepository.resetIndex()
              withContext(Dispatchers.Main) {
                Toast.makeText(context, "Search Index Reset", Toast.LENGTH_SHORT).show()
              }
            }
          } else if (CustomActionHandler.handleAction(context, intent)) {
            // Handled
          } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
          }
        } catch (e: Exception) {
          e.printStackTrace()
          Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
      }
    }
    is SearchResult.Shortcut -> {
      try {
        val uri = result.intentUri
        if (uri.startsWith("shortcut://")) {
          val parts = uri.substring("shortcut://".length).split("/")
          if (parts.size == 2) {
            val pkg = parts[0]
            val id = parts[1]
            val launcherApps =
              context.getSystemService(Context.LAUNCHER_APPS_SERVICE)
                as android.content.pm.LauncherApps
            launcherApps.startShortcut(pkg, id, null, null, android.os.Process.myUserHandle())
          }
        } else {
          val intent = Intent.parseUri(uri, 0)
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          context.startActivity(intent)
        }
      } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error launching shortcut", Toast.LENGTH_SHORT).show()
      }
    }
    is SearchResult.SearchIntent -> {
      // Handled in UI
    }
    is SearchResult.Contact -> {
      try {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri =
          Uri.withAppendedPath(
            android.provider.ContactsContract.Contacts.CONTENT_LOOKUP_URI,
            result.lookupKey,
          )
        intent.data = uri
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
      } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error opening contact", Toast.LENGTH_SHORT).show()
      }
    }
    is SearchResult.Snippet -> {
      val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
      val clip = android.content.ClipData.newPlainText(result.alias, result.content)
      clipboard.setPrimaryClip(clip)
      Toast.makeText(context, "Copied ${result.content}", Toast.LENGTH_SHORT).show()
    }
  }

  // Usage reporting
  scope.launch { searchRepository.reportUsage(result.namespace, result.id, query, wasFirstResult) }
}

internal fun Drawable.toImageBitmap(): ImageBitmap? {
  try {
    val bitmap =
      if (this is BitmapDrawable) {
        if (this.bitmap != null && !this.bitmap.isRecycled) {
          this.bitmap
        } else {
          null
        }
      } else {
        val width = intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = intrinsicHeight.takeIf { it > 0 } ?: 1
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bitmap
      }
    return bitmap?.asImageBitmap()
  } catch (e: Exception) {
    return null
  }
}

// FavoritesRow extracted to components/FavoritesRow.kt

// SnippetDialog extracted to components/SnippetDialog.kt
