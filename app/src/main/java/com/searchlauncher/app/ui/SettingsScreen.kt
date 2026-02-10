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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.searchlauncher.app.R
import com.searchlauncher.app.SearchLauncherApp
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  onStartService: () -> Unit,
  onStopService: () -> Unit,
  onOpenPractice: () -> Unit,
  onBack: () -> Unit,
  initialHighlightSection: String? = null,
  onExportBackup: () -> Unit,
) {
  val context = LocalContext.current
  var showPermissionDialog by remember { mutableStateOf(false) }
  val listState = rememberLazyListState()

  // Check if this app is the default launcher
  val isDefaultLauncher = remember {
    val intent = Intent(Intent.ACTION_MAIN)
    intent.addCategory(Intent.CATEGORY_HOME)
    val resolveInfo = context.packageManager.resolveActivity(intent, 0)
    resolveInfo?.activityInfo?.packageName == context.packageName
  }

  // Use LaunchedEffect to scroll to the requested section
  LaunchedEffect(initialHighlightSection) {
    if (initialHighlightSection != null) {
      val index =
        when (initialHighlightSection) {
          "wallpaper" -> 1
          "shortcuts" -> 2
          "snippets" -> 3
          "history" -> 5
          "privacy" -> 9
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

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp),
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

    item { WallpaperManagementCard() }
    item { CustomShortcutsCard() }
    item { SnippetsCard() }

    item { ThemeSettingsCard() }

    item {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(text = "History", style = MaterialTheme.typography.titleMedium)

          val scope = rememberCoroutineScope()
          val storeWebHistory =
            remember {
              context.dataStore.data.map { it[PreferencesKeys.STORE_WEB_HISTORY] ?: true }
            }
              .collectAsState(initial = true)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(text = "Store Web History", style = MaterialTheme.typography.bodyMedium)
              Text(
                text = "Automatically bookmark visited websites from search",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = storeWebHistory.value,
              onCheckedChange = { enabled ->
                scope.launch {
                  context.dataStore.edit { preferences ->
                    preferences[PreferencesKeys.STORE_WEB_HISTORY] = enabled
                  }
                }
              },
            )
          }

          val historyLimit =
            remember { context.dataStore.data.map { it[PreferencesKeys.HISTORY_LIMIT] ?: -1 } }
              .collectAsState(initial = -1)

          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Show App History Icons", style = MaterialTheme.typography.bodyMedium)

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              listOf("None", "Auto", "Fixed").forEach { mode ->
                val isSelected =
                  when (mode) {
                    "None" -> historyLimit.value == 0
                    "Auto" -> historyLimit.value == -1
                    "Fixed" -> historyLimit.value > 0
                    else -> false
                  }

                if (isSelected) {
                  Button(
                    onClick = { /* Already selected */ },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                  ) {
                    Text(mode, style = MaterialTheme.typography.labelMedium)
                  }
                } else {
                  OutlinedButton(
                    onClick = {
                      scope.launch {
                        val newValue =
                          when (mode) {
                            "None" -> 0
                            "Auto" -> -1
                            "Fixed" -> 5
                            else -> -1
                          }
                        context.dataStore.edit { it[PreferencesKeys.HISTORY_LIMIT] = newValue }
                      }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                  ) {
                    Text(mode, style = MaterialTheme.typography.labelMedium)
                  }
                }
              }
            }

            AnimatedVisibility(visible = historyLimit.value != 0) {
              val minIconSize =
                remember {
                  context.dataStore.data.map {
                    it[PreferencesKeys.MIN_ICON_SIZE]
                      ?: PreferencesKeys.getDefaultIconSize(context)
                  }
                }
                  .collectAsState(initial = PreferencesKeys.getDefaultIconSize(context))

              val appIcon = remember {
                try {
                  context.packageManager.getApplicationIcon(context.packageName).toImageBitmap()
                } catch (e: Exception) {
                  null
                }
              }

              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "Icon Size: ${minIconSize.value}dp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )

                  // Live Preview Icon (Matches FavoritesRow style)
                  Box(
                    modifier = Modifier.size(minIconSize.value.dp),
                    contentAlignment = Alignment.Center,
                  ) {
                    if (appIcon != null) {
                      Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(minIconSize.value.dp * 0.8f),
                      )
                    } else {
                      // Fallback if icon can't be loaded
                      Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = null,
                        modifier = Modifier.size(minIconSize.value.dp * 0.6f),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                      )
                    }
                  }
                }
                androidx.compose.material3.Slider(
                  value = minIconSize.value.toFloat(),
                  onValueChange = { value ->
                    scope.launch {
                      context.dataStore.edit {
                        it[PreferencesKeys.MIN_ICON_SIZE] = value.toInt().coerceIn(16, 64)
                      }
                    }
                  },
                  valueRange = 16f..64f,
                  steps = 15,
                  modifier = Modifier.fillMaxWidth(),
                )
                Text(
                  text =
                    if (historyLimit.value == -1) "Smaller icons allow more history items to fit."
                    else "Adjust the maximum size for your favorite and history icons.",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
              }
            }

            AnimatedVisibility(visible = historyLimit.value > 0) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.Slider(
                  value = historyLimit.value.toFloat(),
                  onValueChange = { value ->
                    scope.launch {
                      context.dataStore.edit {
                        it[PreferencesKeys.HISTORY_LIMIT] = value.toInt().coerceIn(1, 15)
                      }
                    }
                  },
                  valueRange = 1f..15f,
                  steps = 14,
                  modifier = Modifier.fillMaxWidth(),
                )
                Text(
                  text = "Icons will shrink if necessary to fit this many items.",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
              }
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

    item { PrivacyCard() }
    item { BackupRestoreCard(onExportBackup) }
    item { AboutCard() }
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
            Text(text = "Snippets", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "Quick access to frequently used text snippets",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Button(
          onClick = {
            editingItem = null
            showDialog = true
          }
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Add")
        }
      }

      AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (snippetItems.value.isNotEmpty()) {
            snippetItems.value.forEach { item ->
              Card(
                modifier =
                  Modifier.fillMaxWidth().clickable {
                    editingItem = item
                    showDialog = true
                  },
                colors =
                  CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                  ),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = item.alias,
                      style = MaterialTheme.typography.bodyLarge,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text = item.content.take(50) + if (item.content.length > 50) "..." else "",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
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
          } else {
            Text(
              text = "No snippets added yet.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 8.dp),
            )
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
private fun CustomShortcutsCard() {
  val context = LocalContext.current
  val app = context.applicationContext as SearchLauncherApp
  val shortcuts by app.searchShortcutRepository.items.collectAsState()
  val scope = rememberCoroutineScope()

  var showDialog by remember { mutableStateOf(false) }
  var editingShortcut by remember {
    mutableStateOf<com.searchlauncher.app.data.SearchShortcut?>(null)
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
            Text(text = "Custom Shortcuts", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "Keywords for search (e.g., 'y' for YouTube)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Button(
          onClick = {
            editingShortcut = null
            showDialog = true
          }
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Add")
        }
      }

      AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(
            onClick = { scope.launch { app.searchShortcutRepository.resetToDefaults() } },
            modifier = Modifier.align(Alignment.End),
          ) {
            Text("Reset Defaults")
          }

          if (shortcuts.isNotEmpty()) {
            shortcuts.forEach { shortcut ->
              Card(
                modifier =
                  Modifier.fillMaxWidth().clickable {
                    editingShortcut = shortcut
                    showDialog = true
                  },
                colors =
                  CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                  ),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = shortcut.description,
                      style = MaterialTheme.typography.bodyLarge,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text = "Alias: ${shortcut.alias}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  IconButton(
                    onClick = {
                      scope.launch { app.searchShortcutRepository.removeShortcut(shortcut.id) }
                    }
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
          } else {
            Text(
              text = "No custom shortcuts yet.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 8.dp),
            )
          }
        }
      }
    }
  }

  if (showDialog) {
    com.searchlauncher.app.ui.components.ShortcutDialog(
      shortcut = editingShortcut,
      existingAliases =
        shortcuts.map { it.alias } +
                com.searchlauncher.app.data.DefaultShortcuts.searchShortcuts.map { it.alias },
      onDismiss = { showDialog = false },
      onSave = { newShortcut ->
        scope.launch {
          if (editingShortcut != null) {
            app.searchShortcutRepository.updateShortcut(newShortcut)
          } else {
            app.searchShortcutRepository.addShortcut(newShortcut)
          }
          showDialog = false
          app.searchRepository.indexCustomShortcuts()
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
        scope.launch {
          var lastAddedUri: Uri? = null
          uris.forEach { uri ->
            val added = app.wallpaperRepository.addWallpaper(uri)
            if (added != null) lastAddedUri = added
          }
          lastAddedUri?.let { newUri ->
            context.dataStore.edit { prefs ->
              prefs[PreferencesKeys.BACKGROUND_LAST_IMAGE_URI] = newUri.toString()
            }
          }
        }
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
          Text("Add")
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
private fun PrivacyCard() {
  val context = LocalContext.current
  val app = context.applicationContext as SearchLauncherApp
  val scope = rememberCoroutineScope()
  var showPrivacyPolicy by remember { mutableStateOf(false) }

  val searchShortcutsEnabled =
    remember {
      context.dataStore.data.map { it[PreferencesKeys.SEARCH_SHORTCUTS_ENABLED] ?: true }
    }
      .collectAsState(initial = true)

  var crashReportingEnabled by remember { mutableStateOf(app.isConsentGranted()) }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(text = "Privacy", style = MaterialTheme.typography.titleMedium)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = "Search suggestions", style = MaterialTheme.typography.bodyMedium)
          Text(
            text =
              "Show autocomplete suggestions from third-party services (Google, YouTube, etc.) as you type",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
          checked = searchShortcutsEnabled.value,
          onCheckedChange = { enabled ->
            scope.launch {
              context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.SEARCH_SHORTCUTS_ENABLED] = enabled
              }
            }
          },
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = "Crash reporting", style = MaterialTheme.typography.bodyMedium)
          Text(
            text = "Send anonymous error logs to GlitchTip to help us fix bugs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
          checked = crashReportingEnabled,
          onCheckedChange = { enabled ->
            app.setConsent(enabled)
            crashReportingEnabled = enabled
          },
        )
      }

      TextButton(
        onClick = { showPrivacyPolicy = true },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("View Privacy Policy")
      }
    }
  }

  if (showPrivacyPolicy) {
    val policyText = remember {
      try {
        context.assets.open("PRIVACY.md").bufferedReader().use { it.readText() }
      } catch (e: Exception) {
        "Privacy policy not found."
      }
    }
    com.searchlauncher.app.ui.components.PrivacyPolicyDialog(
      onDismiss = { showPrivacyPolicy = false },
      policyText = policyText,
    )
  }
}

@Composable
private fun BackupRestoreCard(onExportBackup: () -> Unit) {
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
        Button(onClick = onExportBackup, modifier = Modifier.weight(1f)) { Text("Export") }

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

@Composable
private fun AboutCard() {
  val context = LocalContext.current
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = "Made with love by Ontola",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Text(
        text = "${com.searchlauncher.app.BuildConfig.VERSION_NAME} · ${com.searchlauncher.app.BuildConfig.GIT_HASH} · ${com.searchlauncher.app.BuildConfig.BUILD_DATE}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      )

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = {
            val intent =
              Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.searchlauncher.app"),
              )
            context.startActivity(intent)
          },
          modifier = Modifier.weight(1f),
        ) {
          Text("Play Store")
        }
        OutlinedButton(
          onClick = {
            val intent =
              Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/joepio/searchlauncher"))
            context.startActivity(intent)
          },
          modifier = Modifier.weight(1f),
        ) {
          Text("Source code")
        }
      }
    }
  }
}
