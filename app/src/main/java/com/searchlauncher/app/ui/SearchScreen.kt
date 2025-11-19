package com.searchlauncher.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.searchlauncher.app.util.CustomActionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(
        query: String,
        onQueryChange: (String) -> Unit,
        onDismiss: () -> Unit,
        onOpenSettings: () -> Unit,
        searchRepository: SearchRepository
) {
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(query) {
        if (query.isEmpty()) {
            searchResults = searchRepository.searchApps("", limit = 50)
        } else {
            delay(50) // Debounce
            searchResults = searchRepository.searchApps(query)
        }
    }

    val darkTheme = isSystemInDarkTheme()
    val colorScheme =
            if (darkTheme) {
                darkColorScheme(
                        surface = Color(0xFF1E1E1E),
                        onSurface = Color.White,
                        surfaceVariant = Color(0xFF2C2C2C),
                        onSurfaceVariant = Color(0xFFCCCCCC)
                )
            } else {
                lightColorScheme(
                        surface = Color.White,
                        onSurface = Color.Black,
                        surfaceVariant = Color(0xFFF0F0F0),
                        onSurfaceVariant = Color(0xFF444444)
                )
            }

    MaterialTheme(colorScheme = colorScheme) {
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable { onDismiss() }
                                .windowInsetsPadding(WindowInsets.statusBars) // Respect status bar
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
                // Results (displayed above search bar)
                if (searchResults.isNotEmpty() || (query.isNotEmpty() && !isLoading)) {
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
                                items(searchResults) { result ->
                                    SearchResultItem(
                                            result = result,
                                            onClick = {
                                                if (result is SearchResult.SearchIntent) {
                                                    onQueryChange(result.trigger + " ")
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

                                if (searchResults.isEmpty() && query.isNotEmpty()) {
                                    item {
                                        Text(
                                                text = "No results found",
                                                modifier = Modifier.padding(32.dp),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Search bar (at bottom)
                Surface(
                        modifier =
                                Modifier.fillMaxWidth().clickable(
                                                indication = null,
                                                interactionSource =
                                                        remember { MutableInteractionSource() }
                                        ) {},
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                ) {
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Detect shortcut
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
                                leadingIcon =
                                        if (activeShortcut != null) {
                                            {
                                                Surface(
                                                        color =
                                                                Color(
                                                                        activeShortcut.color
                                                                                ?: 0xFF808080
                                                                ),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.padding(4.dp)
                                                ) {
                                                    Text(
                                                            text = activeShortcut.trigger,
                                                            color = Color.White,
                                                            modifier =
                                                                    Modifier.padding(
                                                                            horizontal = 8.dp,
                                                                            vertical = 4.dp
                                                                    ),
                                                            style =
                                                                    MaterialTheme.typography
                                                                            .labelMedium,
                                                            fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        } else null,
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions =
                                        KeyboardActions(
                                                onGo = {
                                                    val topResult = searchResults.firstOrNull()
                                                    if (topResult != null) {
                                                        if (topResult is SearchResult.SearchIntent
                                                        ) {
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

                        // Custom Chip rendering over TextField?
                        // Previously we had a complex Box overlapping.
                        // For simplicity, let's stick to standard TextField for now,
                        // OR implement the visual transformation / overlay if critical.
                        // The user liked the chips.
                        // I'll omit the chip logic for this iteration to ensure basic functionality
                        // first,
                        // or I can implement it by putting the chip ROW above the text field?
                        // No, it was inline.
                        // I'll skip the complex chip overlay for a second and just use text.
                        // Wait, I should keep it if possible.
                        // But `BasicTextField` with custom decoration is complex.
                        // I'll use standard TextField. It's easier.

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
                        }

                        IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.size(32.dp).padding(4.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(result: SearchResult, onClick: () -> Unit) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp)) {
            if (result.icon != null) {
                Image(
                        bitmap = result.icon!!.toImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                )
            }

            if (result is SearchResult.Shortcut && result.appIcon != null) {
                Image(
                        bitmap = result.appIcon!!.toImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).align(Alignment.TopStart)
                )
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
    }

    // Usage reporting
    scope.launch { searchRepository.reportUsage(result.namespace, result.id) }
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    val bitmap =
            if (this is BitmapDrawable) {
                this.bitmap
            } else {
                val bitmap =
                        Bitmap.createBitmap(
                                intrinsicWidth.takeIf { it > 0 } ?: 1,
                                intrinsicHeight.takeIf { it > 0 } ?: 1,
                                Bitmap.Config.ARGB_8888
                        )
                val canvas = Canvas(bitmap)
                setBounds(0, 0, canvas.width, canvas.height)
                draw(canvas)
                bitmap
            }
    return bitmap.asImageBitmap()
}
