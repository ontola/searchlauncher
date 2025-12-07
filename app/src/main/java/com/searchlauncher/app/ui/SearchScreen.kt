package com.searchlauncher.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.service.GestureAccessibilityService
import com.searchlauncher.app.ui.components.FavoritesRow
import com.searchlauncher.app.ui.components.SearchResultItem
import com.searchlauncher.app.ui.components.SnippetDialog
import com.searchlauncher.app.ui.components.WallpaperBackground
import com.searchlauncher.app.ui.theme.SearchLauncherTheme
import com.searchlauncher.app.util.CustomActionHandler
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
  searchRepository: SearchRepository,
  focusTrigger: Long = 0L,
  showHistory: Boolean = true,
  showBackgroundImage: Boolean = false,
  folderImages: List<Uri> = emptyList(),
  lastImageUriString: String? = null,
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

  var favorites by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
  var showSnippetDialog by remember { mutableStateOf(false) }
  var snippetEditMode by remember { mutableStateOf(false) }
  var snippetItemToEdit by remember { mutableStateOf<SearchResult.Snippet?>(null) }
  val listState = androidx.compose.foundation.lazy.rememberLazyListState()

  LaunchedEffect(searchResults) {
    if (searchResults.isNotEmpty()) {
      listState.scrollToItem(0)
    }
  }

  LaunchedEffect(focusTrigger) { focusRequester.requestFocus() }

  // Wait for the search repository to be initialized before loading favorites.
  // This prevents a race condition where we try to query the index before it's ready.
  LaunchedEffect(favoriteIds, isSearchInitialized) {
    if (isSearchInitialized && favoriteIds.isNotEmpty()) {
      favorites = searchRepository.getFavorites(favoriteIds)
    } else {
      favorites = emptyList()
    }
  }

  LaunchedEffect(query, showHistory, favoriteIds) {
    if (query.isEmpty()) {
      searchResults =
        if (showHistory) {
          searchRepository.getRecentItems(limit = 10, excludedIds = favoriteIds)
        } else {
          emptyList()
        }
      isFallbackMode = false
    } else {
      val results = searchRepository.searchApps(query)
      android.util.Log.d("SearchScreen", "Query: '$query', Results: ${results.size}")

      // Always append search shortcuts to the end of the results
      // Use a higher limit to show all options as requested
      val shortcuts = searchRepository.getSearchShortcuts(limit = 50)

      if (results.isEmpty()) {
        // Only shortcuts
        searchResults = shortcuts
        isFallbackMode = true
      } else {
        // Apps + Shortcuts
        // Filter out shortcuts that are already in results (by id)
        // to avoid duplicate keys in LazyColumn
        val resultIds = results.map { it.id }.toSet()
        val uniqueShortcuts = shortcuts.filter { !resultIds.contains(it.id) }

        searchResults = results + uniqueShortcuts
        isFallbackMode = false
      }
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
    context.dataStore.data
      .map { it[MainActivity.PreferencesKeys.THEME_COLOR] ?: 0xFF00639B.toInt() }
      .collectAsState(initial = 0xFF00639B.toInt())
  val themeSaturation by
    context.dataStore.data
      .map { it[MainActivity.PreferencesKeys.THEME_SATURATION] ?: 50f }
      .collectAsState(initial = 50f)
  val darkMode by
    context.dataStore.data
      .map { it[MainActivity.PreferencesKeys.DARK_MODE] ?: 0 }
      .collectAsState(initial = 0)

  // Remember the max height of the IME observed so far
  var maxImeHeight by remember { mutableStateOf(0) }
  val imeHeight = WindowInsets.ime.getBottom(LocalDensity.current)

  // Update max height if current IME is taller (e.g. first load or different keyboard)
  if (imeHeight > maxImeHeight) {
    maxImeHeight = imeHeight
  }

  // Use the max known height for padding to prevent jank,
  // unless the keyboard is actually closed (height 0 or very small),
  // but here we assume we want it "always visible" style or at least reserved space.
  // However, to be safe, we'll use the larger of the two: current or max.
  // Ideally, we persist this in DataStore, but for now, session memory.
  // To persist across sessions, we'd need to pass a callback or use DataStore here.
  // For this request "stored / persisted / remembered", we should probably save it.

  // Let's read/write to DataStore for persistence
  val density = LocalDensity.current
  val imeHeightPx = WindowInsets.ime.getBottom(density)
  val storedKeyboardHeight =
    context.dataStore.data
      .map { it[intPreferencesKey("keyboard_height")] ?: 0 }
      .collectAsState(initial = 0)

  LaunchedEffect(imeHeightPx) {
    if (imeHeightPx > storedKeyboardHeight.value) {
      context.dataStore.edit { prefs -> prefs[intPreferencesKey("keyboard_height")] = imeHeightPx }
    }
  }

  // The effective padding is the max of current IME or stored IME height
  val bottomPadding =
    with(density) { kotlin.math.max(imeHeightPx, storedKeyboardHeight.value).toDp() }

  SearchLauncherTheme(themeColor = themeColor, darkThemeMode = darkMode, chroma = themeSaturation) {
    Box(
      modifier =
        Modifier.fillMaxSize().pointerInput(Unit) {
          detectDragGestures { change, dragAmount ->
            // Detect vertical swipe down
            if (dragAmount.y > 20) { // Threshold for swipe down
              val isLeft = change.position.x < size.width / 2
              val success =
                if (isLeft) {
                  if (!GestureAccessibilityService.openNotifications()) {
                    com.searchlauncher.app.util.SystemUtils.expandNotifications(context)
                    true
                  } else {
                    true
                  }
                } else {
                  if (!GestureAccessibilityService.openQuickSettings()) {
                    com.searchlauncher.app.util.SystemUtils.expandQuickSettings(context)
                    true
                  } else {
                    true
                  }
                }
            }
          }
        }
    ) {
      WallpaperBackground(
        showBackgroundImage = showBackgroundImage,
        bottomPadding = bottomPadding,
        onDismiss = onDismiss,
        folderImages = folderImages,
        lastImageUriString = lastImageUriString,
      )

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
                        { app.favoritesRepository.toggleFavorite(result.id) }
                      } else null,
                    onEditSnippet =
                      if (result is SearchResult.Snippet) {
                        {
                          snippetItemToEdit = result
                          snippetEditMode = true
                          showSnippetDialog = true
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
                        } else {
                          launchResult(context, result, searchRepository, scope, query, index == 0)
                          onDismiss()
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
              launchResult(context, result, searchRepository, scope)
              onDismiss()
            },
            onRemoveFavorite = { result -> app.favoritesRepository.toggleFavorite(result.id) },
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
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val activeShortcut =
              remember(query) {
                app.searchShortcutRepository.items.value.find {
                  query.startsWith("${it.alias} ", ignoreCase = true)
                }
              }

            if (activeShortcut != null) {
              val iconDrawable =
                remember(activeShortcut) {
                  searchRepository.getColoredSearchIcon(activeShortcut.color, activeShortcut.alias)
                }

              if (iconDrawable != null) {
                Image(
                  bitmap = iconDrawable.toBitmap().asImageBitmap(),
                  contentDescription = null,
                  modifier = Modifier.size(24.dp),
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
            }

            val displayQuery =
              if (activeShortcut != null) {
                query.substring("${activeShortcut.alias} ".length)
              } else {
                query
              }

            BasicTextField(
              value = displayQuery,
              onValueChange = { newText ->
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
                        launchResult(context, topResult, searchRepository, scope)
                        onDismiss()
                      }
                    }
                  }
                ),
              cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                  if (displayQuery.isEmpty() && activeShortcut == null) {
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
                )
              }
            } else {
              IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp).padding(4.dp)) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
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
        }
        showSnippetDialog = false
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
      result.deepLink?.let { deepLink ->
        try {
          val intent =
            if (deepLink.startsWith("intent:")) {
              Intent.parseUri(deepLink, Intent.URI_INTENT_SCHEME)
            } else {
              Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
            }

          if (intent.action == "com.searchlauncher.RESET_INDEX") {
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
