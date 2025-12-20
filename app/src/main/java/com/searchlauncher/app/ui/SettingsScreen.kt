package com.searchlauncher.app.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.ui.MainActivity.PreferencesKeys
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  onStartService: () -> Unit,
  onStopService: () -> Unit,
  onOpenPractice: () -> Unit,
  onOpenCustomShortcuts: () -> Unit,
  onBack: () -> Unit,
  initialHighlightSection: String? = null,
) {
  val context = LocalContext.current
  var showPermissionDialog by remember { mutableStateOf(false) }
  val listState = rememberLazyListState()

  // Use LaunchedEffect to scroll to the requested section
  LaunchedEffect(initialHighlightSection) {
    if (initialHighlightSection != null) {
      val index =
        when (initialHighlightSection) {
          "wallpaper" -> 2 // WallpaperManagementCard
          "history" -> 5 // Search Settings
          "snippets" -> 7 // SnippetsCard
          else -> -1
        }
      if (index >= 0) {
        listState.animateScrollToItem(index)
      }
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

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    contentPadding = PaddingValues(bottom = 32.dp),
  ) {
    item {
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
    }

    item { ThemeSettingsCard(onNavigateToHome = onBack) }

    item { WallpaperManagementCard() }

    item {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val scope = rememberCoroutineScope()
          val swipeGestureEnabled =
            remember {
                context.dataStore.data.map { it[PreferencesKeys.SWIPE_GESTURE_ENABLED] ?: false }
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
                        preferences[PreferencesKeys.SWIPE_GESTURE_ENABLED] = true
                      }
                      onStartService()
                    }
                  } else {
                    showPermissionDialog = true
                  }
                } else {
                  scope.launch {
                    context.dataStore.edit { preferences ->
                      preferences[PreferencesKeys.SWIPE_GESTURE_ENABLED] = false
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
    }

    // Show launcher settings if not default launcher
    if (!isDefaultLauncher) {
      item {
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
            ) {
              Text("Set as Default Launcher")
            }
          }
        }
      }
    }

    item {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(text = "Search Settings", style = MaterialTheme.typography.titleMedium)

          val scope = rememberCoroutineScope()
          val showHistory =
            remember {
                context.dataStore.data.map { it[booleanPreferencesKey("show_history")] ?: true }
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
    }

    item {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
    }

    item { SnippetsCard() }

    item {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            title = "Usage Access",
            description = "Required to accurately detect when the keyboard is open.",
            granted = rememberPermissionState { hasUsageStatsPermission(context) }.value,
            onGrant = {
              val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
              context.startActivity(intent)
            },
          )

          PermissionStatus(
            title = "Read Contacts",
            description = "Required to search your contacts.",
            granted =
              rememberPermissionState {
                  ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                    PackageManager.PERMISSION_GRANTED
                }
                .value,
            onGrant = {
              val intent =
                Intent(
                  Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                  Uri.parse("package:${context.packageName}"),
                )
              context.startActivity(intent)
            },
          )
        }
      }
    }

    item { BackupRestoreCard() }
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
                fontWeight = FontWeight.Bold,
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
                fontWeight = FontWeight.Bold,
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
            fontStyle = FontStyle.Italic,
          )
        }
      },
      confirmButton = { Button(onClick = { showPermissionDialog = false }) { Text("Got it") } },
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
        imageVector = Icons.Default.Check,
        contentDescription = "Granted",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(32.dp),
      )
    } else {
      Button(onClick = onGrant) { Text("Grant") }
    }
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
        ) {
          Text("Add")
        }
      }

      // List existing items
      if (snippetItems.value.isNotEmpty()) {
        Text(
          text = "${snippetItems.value.size} item${if (snippetItems.value.size != 1) "s" else ""}",
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
                  Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
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
      ) {
        Text("Save")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun WallpaperManagementCard() {
  val context = LocalContext.current
  val app = context.applicationContext as SearchLauncherApp
  val wallpapers by app.wallpaperRepository.wallpapers.collectAsState()
  val scope = rememberCoroutineScope()

  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
      if (uris.isNotEmpty()) {
        scope.launch { uris.forEach { uri -> app.wallpaperRepository.addWallpaper(uri) } }
      }
    }

  var isExpanded by remember { mutableStateOf(false) }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f).clickable { isExpanded = !isExpanded },
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector =
              if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
          )
          Spacer(Modifier.width(8.dp))
          Column {
            Text(text = "Wallpapers", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "Manage your launcher background collection",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Button(
          onClick = {
            launcher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          }
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Add Wallpapers")
        }
      }

      AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          if (wallpapers.isNotEmpty()) {
            wallpapers.chunked(3).forEach { rowWallpapers: List<Uri> ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                rowWallpapers.forEach { wallpaperUri: Uri ->
                  Box(
                    modifier =
                      Modifier.weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                  ) {
                    AsyncImage(
                      model = wallpaperUri,
                      contentDescription = null,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize(),
                    )

                    IconButton(
                      onClick = {
                        scope.launch { app.wallpaperRepository.removeWallpaper(wallpaperUri) }
                      },
                      modifier =
                        Modifier.align(Alignment.TopEnd)
                          .padding(4.dp)
                          .size(24.dp)
                          .background(
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp),
                          ),
                    ) {
                      Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                  }
                }
                repeat(3 - rowWallpapers.size) { Spacer(modifier = Modifier.weight(1f)) }
              }
            }
          } else {
            Text(
              text = "No custom wallpapers added. Using defaults.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 8.dp),
            )
          }
        }
      }
    }
  }
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
fun rememberPermissionState(check: () -> Boolean): State<Boolean> {
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  val state = remember { mutableStateOf(check()) }
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        state.value = check()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  return state
}
