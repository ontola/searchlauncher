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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.searchlauncher.app.data.SearchRepository
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.ui.theme.SearchLauncherTheme
import com.searchlauncher.app.util.CustomActionHandler
import kotlinx.coroutines.Dispatchers
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
        showHistory: Boolean = true
) {
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isFallbackMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val app = context.applicationContext as com.searchlauncher.app.SearchLauncherApp
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val favoriteIds by app.favoritesRepository.favoriteIds.collectAsState()
    var favorites by remember { mutableStateOf<List<SearchResult>>(emptyList()) }

    LaunchedEffect(focusTrigger) { focusRequester.requestFocus() }

    LaunchedEffect(favoriteIds) {
        if (favoriteIds.isNotEmpty()) {
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
            if (results.isEmpty()) {
                val shortcuts = searchRepository.getSearchShortcuts(limit = 10)
                android.util.Log.d("SearchScreen", "Showing ${shortcuts.size} search shortcuts")
                searchResults = shortcuts
                isFallbackMode = true
            } else {
                searchResults = results
                isFallbackMode = false
            }
        }
    }

    SearchLauncherTheme {
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable { onDismiss() }
                                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Column(
                    modifier =
                            Modifier.fillMaxSize()
                                    .padding(16.dp)
                                    .windowInsetsPadding(
                                            WindowInsets.ime.union(WindowInsets.navigationBars)
                                    ),
                    verticalArrangement = Arrangement.Bottom
            ) {
                if (searchResults.isNotEmpty()) {
                    Surface(
                            modifier =
                                    Modifier.fillMaxWidth().weight(1f, fill = false).clickable(
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
                                    reverseLayout = true,
                                    contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(searchResults, key = { "${it.namespace}/${it.id}" }) { result ->
                                    SearchResultItem(
                                            result = result,
                                            isFavorite = favoriteIds.contains(result.id),
                                            onToggleFavorite = if (result is SearchResult.App) {
                                                { app.favoritesRepository.toggleFavorite(result.id) }
                                            } else null,
                                            onClick = {
                                                if (result is SearchResult.SearchIntent) {
                                                    if (isFallbackMode && query.isNotEmpty()) {
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
                                                                        shortcut.urlTemplate.replace(
                                                                                "%s",
                                                                                java.net.URLEncoder
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
                                                        onQueryChange(result.trigger + " ")
                                                    }
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
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
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
                                Modifier.fillMaxWidth().clickable(
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
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val activeShortcut =
                                remember(query) {
                                    com.searchlauncher.app.data.CustomShortcuts.shortcuts
                                            .filterIsInstance<
                                                    com.searchlauncher.app.data.CustomShortcut.Search>()
                                            .find { query.startsWith("${it.trigger} ") }
                                }

                        val displayQuery =
                                if (activeShortcut != null) {
                                    query.substring("${activeShortcut.trigger} ".length)
                                } else {
                                    query
                                }

                        TextField(
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
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent
                                        ),
                                placeholder = {
                                    if (activeShortcut == null) {
                                        Text(
                                                "Search apps and content…",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions =
                                        KeyboardActions(
                                                onGo = {
                                                    val topResult = searchResults.firstOrNull()
                                                    if (topResult != null) {
                                                        if (topResult is SearchResult.SearchIntent) {
                                                            onQueryChange(topResult.trigger + " ")
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
                                        )
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
                                        modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SearchResultItem(
    result: SearchResult,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
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
                            if (result is SearchResult.Contact || result is SearchResult.QuickCopy) {
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
                        color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (showMenu && onToggleFavorite != null) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
                    onClick = {
                        onToggleFavorite()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isFavorite) {
                                androidx.compose.material.icons.Icons.Default.Star
                            } else {
                                androidx.compose.material.icons.Icons.Default.StarBorder
                            },
                            contentDescription = null
                        )
                    }
                )
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
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(result.alias, result.content)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied ${result.alias}", Toast.LENGTH_SHORT).show()
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
                    val bitmap =
                            Bitmap.createBitmap(
                                    width,
                                    height,
                                    Bitmap.Config.ARGB_8888
                            )
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
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = padding, vertical = 4.dp)
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
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Remove from Favorites") },
                        onClick = {
                            onRemoveFavorite(result)
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}
