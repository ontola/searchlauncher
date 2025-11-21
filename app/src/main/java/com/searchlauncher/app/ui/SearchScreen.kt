package com.searchlauncher.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import coil.compose.AsyncImage
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.data.SearchResult
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
        showBackgroundImage: Boolean = false
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
    var showQuickCopyDialog by remember { mutableStateOf(false) }
    var quickCopyEditMode by remember { mutableStateOf(false) }
    var quickCopyItemToEdit by remember { mutableStateOf<SearchResult.QuickCopy?>(null) }
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
                    .map {
                        if (showBackgroundImage) it[MainActivity.PreferencesKeys.BACKGROUND_URI]
                        else null
                    }
                    .collectAsState(initial = null)

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
            context.dataStore
                    .data
                    .map { it[intPreferencesKey("keyboard_height")] ?: 0 }
                    .collectAsState(initial = 0)

    LaunchedEffect(imeHeightPx) {
        if (imeHeightPx > storedKeyboardHeight.value) {
            context.dataStore.edit { prefs ->
                prefs[intPreferencesKey("keyboard_height")] = imeHeightPx
            }
        }
    }

    // The effective padding is the max of current IME or stored IME height
    val bottomPadding =
            with(density) { kotlin.math.max(imeHeightPx, storedKeyboardHeight.value).toDp() }

    SearchLauncherTheme(
            themeColor = themeColor,
            darkThemeMode = darkMode,
            chroma = themeSaturation
    ) {
        Box(modifier = Modifier.fillMaxSize().clickable { onDismiss() }) {
            // Background: either image or solid color
            // Modified to only fill the space ABOVE the keyboard
            // Use the FULL available size for the background, not padded by bottomPadding
            // This ensures the background extends behind the keyboard area if needed,
            // or at least covers the "gap" if there is a mismatch.
            // But user asked: "bg to fit the space above the keyboard"

            // Re-evaluating the screenshot:
            // The user is seeing a big black void where the keyboard is supposed to be.
            // The "Background fitting space above keyboard" logic is doing exactly that:
            // it stops drawing the background at 'bottomPadding'.

            // If the keyboard is NOT actually visible or has a different height than stored,
            // we get a black bar.
            // The black bar is the window background because our Surface/Box doesn't cover it.

            // To fix the "black void" issue while keeping "bg fits space above keyboard":
            // 1. We should probably draw a solid color background (window background) behind
            // everything
            //    to ensure no transparency leaks to system black.
            // 2. OR, better yet: The user's request "bg to fit space above keyboard"
            //    probably meant the BACKGROUND IMAGE should be cropped/positioned there,
            //    but the app background color should likely extend or be handled gracefully.

            // Given the screenshot showing a black bottom half, it seems the 'bottomPadding'
            // is reserving space but nothing is drawing there.

            // Let's make the root Box fill the entire screen including behind nav bar/ime
            // and only pad the *content* (Columns).
            // BUT for the background IMAGE, we apply the padding so it doesn't get covered.

            // If no image is set (solid color), we probably want that solid color to fill the whole
            // screen
            // or at least not leave a black void.

            // Strategy:
            // 1. Root Box fills max size.
            // 2. If Background Image -> Apply bottom padding to it so it squats above keyboard.
            //    Underneath (behind) it, draw a solid surface color so no black void if gaps.
            // 3. If Solid Color -> Fill max size (behind keyboard too) so it looks seamless?
            //    OR if user strictly wants it to stop above keyboard, we must ensure what's behind
            // is acceptable.

            // Current implementation:
            // val contentModifier = Modifier.fillMaxSize().padding(bottom = bottomPadding)
            // This is leaving the bottom area transparent -> Activity window background (Black).

            // For overlay mode, we want transparency, so don't draw an opaque base background

            val contentModifier = Modifier.fillMaxSize().padding(bottom = bottomPadding)

            if (backgroundUriString != null) {
                AsyncImage(
                        model = android.net.Uri.parse(backgroundUriString),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = contentModifier
                )
            } else {
                // Solid background when no image - semi-transparent so app underneath shows through
                Box(
                        modifier =
                                contentModifier.background(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
                                )
                )
            }

            Column(
                    modifier =
                            Modifier.fillMaxSize()
                                    .padding(
                                            bottom = bottomPadding
                                    ) // Push content up by reserved space
                                    .padding(top = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.Bottom
            ) {
                if (searchResults.isNotEmpty()) {
                    Surface(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .weight(1f, fill = false)
                                            .padding(horizontal = 16.dp)
                                            .clickable(
                                                    indication = null,
                                                    interactionSource =
                                                            remember { MutableInteractionSource() }
                                            ) {},
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                    ) {
                        if (isLoading) {
                            Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        } else {
                            LazyColumn(
                                    state = listState,
                                    reverseLayout = true,
                                    contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(searchResults, key = { "${it.namespace}/${it.id}" }) { result
                                    ->
                                    SearchResultItem(
                                            result = result,
                                            isFavorite = favoriteIds.contains(result.id),
                                            onToggleFavorite =
                                                    if (result is SearchResult.App) {
                                                        {
                                                            app.favoritesRepository.toggleFavorite(
                                                                    result.id
                                                            )
                                                        }
                                                    } else null,
                                            onEditQuickCopy =
                                                    if (result is SearchResult.QuickCopy) {
                                                        {
                                                            quickCopyItemToEdit = result
                                                            quickCopyEditMode = true
                                                            showQuickCopyDialog = true
                                                        }
                                                    } else null,
                                            onCreateQuickCopy = {
                                                quickCopyEditMode = false
                                                showQuickCopyDialog = true
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
                                                    if (query.isNotEmpty() &&
                                                                    !result.trigger.equals(
                                                                            query.trim(),
                                                                            ignoreCase = true
                                                                    ) &&
                                                                    !result.title.contains(
                                                                            query.trim(),
                                                                            ignoreCase = true
                                                                    )
                                                    ) {
                                                        // Perform Search
                                                        val shortcut =
                                                                com.searchlauncher.app.data
                                                                        .CustomShortcuts.shortcuts
                                                                        .filterIsInstance<
                                                                                com.searchlauncher.app.data.CustomShortcut.Search>()
                                                                        .find {
                                                                            it.trigger ==
                                                                                    result.trigger
                                                                        }

                                                        if (shortcut != null) {
                                                            try {
                                                                val url =
                                                                        shortcut.urlTemplate
                                                                                .replace(
                                                                                        "%s",
                                                                                        java.net
                                                                                                .URLEncoder
                                                                                                .encode(
                                                                                                        query,
                                                                                                        "UTF-8"
                                                                                                )
                                                                                )
                                                                val intent =
                                                                        Intent(
                                                                                Intent.ACTION_VIEW,
                                                                                Uri.parse(url)
                                                                        )
                                                                intent.addFlags(
                                                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                                                )
                                                                context.startActivity(intent)
                                                                scope.launch {
                                                                    searchRepository.reportUsage(
                                                                            result.namespace,
                                                                            result.id
                                                                    )
                                                                }
                                                                onDismiss()
                                                            } catch (e: Exception) {
                                                                Toast.makeText(
                                                                                context,
                                                                                "Cannot open: ${result.title}",
                                                                                Toast.LENGTH_SHORT
                                                                        )
                                                                        .show()
                                                            }
                                                        }
                                                    } else {
                                                        // Enter sub-search mode (append trigger)
                                                        onQueryChange(result.trigger + " ")
                                                    }
                                                } else {
                                                    if (result is SearchResult.Content &&
                                                                    result.deepLink ==
                                                                            "intent:#Intent;action=com.searchlauncher.action.CREATE_QUICK_COPY;end"
                                                    ) {
                                                        quickCopyEditMode = false
                                                        showQuickCopyDialog = true
                                                    } else {
                                                        launchResult(
                                                                context,
                                                                result,
                                                                searchRepository,
                                                                scope
                                                        )
                                                        onDismiss()
                                                    }
                                                }
                                            }
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
                            onRemoveFavorite = { result ->
                                app.favoritesRepository.toggleFavorite(result.id)
                            }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Surface(
                        modifier =
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(
                                                indication = null,
                                                interactionSource =
                                                        remember { MutableInteractionSource() }
                                        ) {},
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                ) {
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .heightIn(min = 40.dp)
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeShortcut =
                                remember(query) {
                                    com.searchlauncher.app.data.CustomShortcuts.shortcuts
                                            .filterIsInstance<
                                                    com.searchlauncher.app.data.CustomShortcut.Search>()
                                            .find {
                                                query.startsWith(
                                                        "${it.trigger} ",
                                                        ignoreCase = true
                                                )
                                            }
                                }

                        if (activeShortcut != null) {
                            val iconDrawable =
                                    remember(activeShortcut) {
                                        searchRepository.getColoredSearchIcon(
                                                activeShortcut.color,
                                                activeShortcut.trigger
                                        )
                                    }

                            if (iconDrawable != null) {
                                Image(
                                        bitmap = iconDrawable.toBitmap().asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        val displayQuery =
                                if (activeShortcut != null) {
                                    query.substring("${activeShortcut.trigger} ".length)
                                } else {
                                    query
                                }

                        BasicTextField(
                                value = displayQuery,
                                onValueChange = { newText ->
                                    if (activeShortcut != null) {
                                        onQueryChange("${activeShortcut.trigger} $newText")
                                    } else {
                                        onQueryChange(newText)
                                    }
                                },
                                modifier =
                                        Modifier.weight(1f)
                                                .focusRequester(focusRequester)
                                                .onKeyEvent { event ->
                                                    if (event.nativeKeyEvent.keyCode ==
                                                                    android.view.KeyEvent
                                                                            .KEYCODE_DEL &&
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
                                                color = MaterialTheme.colorScheme.onSurface
                                        ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions =
                                        KeyboardActions(
                                                onGo = {
                                                    val topResult = searchResults.firstOrNull()
                                                    if (topResult != null) {
                                                        if (topResult is SearchResult.SearchIntent
                                                        ) {
                                                            if (isFallbackMode && query.isNotEmpty()
                                                            ) {
                                                                // In fallback mode (e.g. random
                                                                // text), 'Go' should perform the
                                                                // search
                                                                // using the top shortcut, instead
                                                                // of just expanding the filter.
                                                                val shortcut =
                                                                        com.searchlauncher.app.data
                                                                                .CustomShortcuts
                                                                                .shortcuts
                                                                                .filterIsInstance<
                                                                                        com.searchlauncher.app.data.CustomShortcut.Search>()
                                                                                .find {
                                                                                    it.trigger ==
                                                                                            topResult
                                                                                                    .trigger
                                                                                }

                                                                if (shortcut != null) {
                                                                    try {
                                                                        val url =
                                                                                shortcut.urlTemplate
                                                                                        .replace(
                                                                                                "%s",
                                                                                                java.net
                                                                                                        .URLEncoder
                                                                                                        .encode(
                                                                                                                query,
                                                                                                                "UTF-8"
                                                                                                        )
                                                                                        )
                                                                        val intent =
                                                                                Intent(
                                                                                        Intent.ACTION_VIEW,
                                                                                        Uri.parse(
                                                                                                url
                                                                                        )
                                                                                )
                                                                        intent.addFlags(
                                                                                Intent.FLAG_ACTIVITY_NEW_TASK
                                                                        )
                                                                        context.startActivity(
                                                                                intent
                                                                        )
                                                                        scope.launch {
                                                                            searchRepository
                                                                                    .reportUsage(
                                                                                            topResult
                                                                                                    .namespace,
                                                                                            topResult
                                                                                                    .id
                                                                                    )
                                                                        }
                                                                        onDismiss()
                                                                    } catch (e: Exception) {
                                                                        Toast.makeText(
                                                                                        context,
                                                                                        "Cannot open: ${topResult.title}",
                                                                                        Toast.LENGTH_SHORT
                                                                                )
                                                                                .show()
                                                                    }
                                                                } else {
                                                                    // Should not happen if data
                                                                    // integrity is good, but
                                                                    // fallback:
                                                                    onQueryChange(
                                                                            topResult.trigger + " "
                                                                    )
                                                                }
                                                            } else {
                                                                // Normal mode: pressing enter on a
                                                                // shortcut expands it (sub-search)
                                                                onQueryChange(
                                                                        topResult.trigger + " "
                                                                )
                                                            }
                                                        } else {
                                                            launchResult(
                                                                    context,
                                                                    topResult,
                                                                    searchRepository,
                                                                    scope
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
                                            Text(
                                                    "Search apps and content…",
                                                    color =
                                                            MaterialTheme.colorScheme
                                                                    .onSurfaceVariant,
                                                    fontSize = 16.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                        )

                        if (query.isNotEmpty()) {
                            IconButton(
                                    onClick = { onQueryChange("") },
                                    modifier = Modifier.size(32.dp).padding(4.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            IconButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier.size(32.dp).padding(4.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQuickCopyDialog) {
        QuickCopyDialog(
                initialAlias =
                        if (quickCopyEditMode && quickCopyItemToEdit != null)
                                quickCopyItemToEdit!!.alias
                        else "",
                initialContent =
                        if (quickCopyEditMode && quickCopyItemToEdit != null)
                                quickCopyItemToEdit!!.content
                        else "",
                isEditMode = quickCopyEditMode,
                onDismiss = { showQuickCopyDialog = false },
                onConfirm = { alias, content ->
                    scope.launch(Dispatchers.IO) {
                        if (quickCopyEditMode && quickCopyItemToEdit != null) {
                            app.quickCopyRepository.updateItem(
                                    quickCopyItemToEdit!!.alias,
                                    alias,
                                    content
                            )
                        } else {
                            app.quickCopyRepository.addItem(alias, content)
                        }
                    }
                    showQuickCopyDialog = false
                }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SearchResultItem(
        result: SearchResult,
        isFavorite: Boolean = false,
        onToggleFavorite: (() -> Unit)? = null,
        onEditQuickCopy: (() -> Unit)? = null,
        onCreateQuickCopy: (() -> Unit)? = null,
        onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .then(
                                        if (onToggleFavorite != null) {
                                            Modifier.combinedClickable(
                                                    onClick = onClick,
                                                    onLongClick = { showMenu = true }
                                            )
                                        } else if (result is SearchResult.QuickCopy) {
                                            Modifier.combinedClickable(
                                                    onClick = onClick,
                                                    onLongClick = { showMenu = true }
                                            )
                                        } else {
                                            Modifier.clickable(onClick = onClick)
                                        }
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp)) {
                if (result.icon != null) {
                    val iconModifier =
                            if (result is SearchResult.Contact ||
                                            result is SearchResult.QuickCopy ||
                                            result is SearchResult.SearchIntent
                            ) {
                                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                            } else {
                                Modifier.size(40.dp)
                            }
                    val imageBitmap = result.icon?.toImageBitmap()
                    if (imageBitmap != null) {
                        Image(
                                bitmap = imageBitmap,
                                contentDescription = null,
                                modifier = iconModifier
                        )
                    }
                }

                if (result is SearchResult.Shortcut && result.appIcon != null) {
                    val appIconBitmap = result.appIcon.toImageBitmap()
                    if (appIconBitmap != null) {
                        Image(
                                bitmap = appIconBitmap,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).align(Alignment.TopStart)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                        text = result.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        if (showMenu) {
            val context = LocalContext.current
            DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    properties = PopupProperties(focusable = false)
            ) {
                if (result is SearchResult.QuickCopy) {
                    DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                onEditQuickCopy?.invoke()
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                val app =
                                        context.applicationContext as
                                                com.searchlauncher.app.SearchLauncherApp
                                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                    app.quickCopyRepository.removeItem(result.alias)
                                }
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                    DropdownMenuItem(
                            text = { Text("Create New") },
                            onClick = {
                                onCreateQuickCopy?.invoke()
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                    )
                }

                if (onToggleFavorite != null) {
                    DropdownMenuItem(
                            text = {
                                Text(
                                        if (isFavorite) "Remove from Favorites"
                                        else "Add to Favorites"
                                )
                            },
                            onClick = {
                                onToggleFavorite()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                        imageVector =
                                                if (isFavorite) {
                                                    androidx.compose.material.icons.Icons.Default
                                                            .Star
                                                } else {
                                                    androidx.compose.material.icons.Icons.Default
                                                            .StarBorder
                                                },
                                        contentDescription = null
                                )
                            }
                    )
                }

                if (result is SearchResult.App) {
                    DropdownMenuItem(
                            text = { Text("App Info") },
                            onClick = {
                                try {
                                    val intent =
                                            Intent(
                                                    android.provider.Settings
                                                            .ACTION_APPLICATION_DETAILS_SETTINGS
                                            )
                                    intent.data = Uri.parse("package:${result.packageName}")
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                                    context,
                                                    "Cannot open App Info",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                }
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                    )
                    DropdownMenuItem(
                            text = { Text("Uninstall") },
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_DELETE)
                                    intent.data = Uri.parse("package:${result.packageName}")
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                                    context,
                                                    "Cannot start uninstall",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                }
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }
        }
    }
}

private fun launchResult(
        context: Context,
        result: SearchResult,
        searchRepository: SearchRepository,
        scope: kotlinx.coroutines.CoroutineScope
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
                                Toast.makeText(context, "Search Index Reset", Toast.LENGTH_SHORT)
                                        .show()
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
                                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as
                                        android.content.pm.LauncherApps
                        launcherApps.startShortcut(
                                pkg,
                                id,
                                null,
                                null,
                                android.os.Process.myUserHandle()
                        )
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
                                result.lookupKey
                        )
                intent.data = uri
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error opening contact", Toast.LENGTH_SHORT).show()
            }
        }
        is SearchResult.QuickCopy -> {
            val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as
                            android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(result.alias, result.content)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied ${result.content}", Toast.LENGTH_SHORT).show()
        }
    }

    // Usage reporting
    scope.launch { searchRepository.reportUsage(result.namespace, result.id) }
}

private fun Drawable.toImageBitmap(): ImageBitmap? {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoritesRow(
        favorites: List<SearchResult>,
        onLaunch: (SearchResult) -> Unit,
        onRemoveFavorite: (SearchResult) -> Unit
) {
    // Calculate spacing based on count? Standard spacedBy is usually fine.
    // "If there are a lot of icons, make them smaller"
    // We can check the count and adjust size/spacing.
    val isCrowded = favorites.size > 5
    val iconSize = if (isCrowded) 40.dp else 48.dp
    // Minimal spacing/padding requested
    val spacing = 4.dp
    val padding = 4.dp

    androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.Start),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        items(favorites) { result ->
            var showMenu by remember { mutableStateOf(false) }

            Box(
                    modifier =
                            Modifier.size(iconSize)
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                            onClick = { onLaunch(result) },
                                            onLongClick = { showMenu = true }
                                    ),
                    contentAlignment = Alignment.Center
            ) {
                val imageBitmap = result.icon?.toImageBitmap()
                if (imageBitmap != null) {
                    // Use slightly smaller image inside the touch target
                    val imageSize = if (isCrowded) 32.dp else 40.dp
                    Image(
                            bitmap = imageBitmap,
                            contentDescription = result.title,
                            modifier = Modifier.size(imageSize)
                    )
                }

                DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                        properties = PopupProperties(focusable = false)
                ) {
                    DropdownMenuItem(
                            text = { Text("Remove from Favorites") },
                            onClick = {
                                onRemoveFavorite(result)
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Star, contentDescription = null)
                            }
                    )

                    if (result is SearchResult.App) {
                        val context = LocalContext.current
                        DropdownMenuItem(
                                text = { Text("App Info") },
                                onClick = {
                                    try {
                                        val intent =
                                                Intent(
                                                        android.provider.Settings
                                                                .ACTION_APPLICATION_DETAILS_SETTINGS
                                                )
                                        intent.data = Uri.parse("package:${result.packageName}")
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                                        context,
                                                        "Cannot open App Info",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    }
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null)
                                }
                        )
                        DropdownMenuItem(
                                text = { Text("Uninstall") },
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_DELETE)
                                        intent.data = Uri.parse("package:${result.packageName}")
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                                        context,
                                                        "Cannot start uninstall",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    }
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickCopyDialog(
        initialAlias: String,
        initialContent: String,
        isEditMode: Boolean,
        onDismiss: () -> Unit,
        onConfirm: (String, String) -> Unit
) {
    var alias by remember { mutableStateOf(initialAlias) }
    var content by remember { mutableStateOf(initialContent) }
    var aliasError by remember { mutableStateOf(false) }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (isEditMode) "Edit Quick Copy" else "New Quick Copy") },
            text = {
                Column {
                    OutlinedTextField(
                            value = alias,
                            onValueChange = {
                                alias = it
                                aliasError = false
                            },
                            label = { Text("Alias") },
                            isError = aliasError,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                    )
                    if (aliasError) {
                        Text(
                                text = "Alias cannot be empty",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("Content") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                        onClick = {
                            if (alias.isBlank()) {
                                aliasError = true
                            } else {
                                onConfirm(alias, content)
                            }
                        }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
