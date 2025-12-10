package com.searchlauncher.app.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
) {
  val context = LocalContext.current
  var showPermissionDialog by remember { mutableStateOf(false) }

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

  Column(
    modifier =
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PaddingValues(16.dp)),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
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

    ThemeSettingsCard(onNavigateToHome = onBack)

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

    // Show launcher settings if not default launcher
    if (!isDefaultLauncher) {
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

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Home Screen", style = MaterialTheme.typography.titleMedium)

        val scope = rememberCoroutineScope()
        val homeToAppList =
          remember { context.dataStore.data.map { it[PreferencesKeys.HOME_TO_APPLIST] ?: false } }
            .collectAsState(initial = false)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = "Home opens App List", style = MaterialTheme.typography.bodyMedium)
            Text(
              text = "Open App List instead of Search when pressing Home",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = homeToAppList.value,
            onCheckedChange = { enabled ->
              scope.launch {
                context.dataStore.edit { preferences ->
                  preferences[PreferencesKeys.HOME_TO_APPLIST] = enabled
                }
              }
            },
          )
        }
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
      }
    }

    BackupRestoreCard()
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
