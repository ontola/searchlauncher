package com.searchlauncher.app.ui

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.searchlauncher.app.ui.onboarding.resolveSeparateShade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An installed app that a gesture can open. */
private data class GestureApp(val packageName: String, val label: String)

private val HomeGesture.icon: ImageVector
  get() =
    when (this) {
      HomeGesture.SwipeUp -> Icons.Filled.ArrowUpward
      HomeGesture.SwipeDownLeft,
      HomeGesture.SwipeDownRight -> Icons.Filled.ArrowDownward
      HomeGesture.SwipeLeft -> Icons.AutoMirrored.Filled.ArrowBack
      HomeGesture.SwipeRight -> Icons.AutoMirrored.Filled.ArrowForward
    }

/** Settings card for issue #161: one row per home-screen swipe, each opening an action picker. */
@Composable
fun GestureSettingsCard() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val bindings by
    remember(context) {
        context.dataStore.data.map { prefs ->
          val separateShade =
            resolveSeparateShade(
              prefs[PreferencesKeys.SEPARATE_QUICK_SETTINGS],
              Build.MANUFACTURER,
              Build.BRAND,
            )
          resolveGestureBindings(prefs, separateShade)
        }
      }
      .collectAsState(initial = null)
  var editing by remember { mutableStateOf<HomeGesture?>(null) }
  var pickingAppFor by remember { mutableStateOf<HomeGesture?>(null) }

  fun save(gesture: HomeGesture, action: GestureAction, appPackage: String? = null) {
    scope.launch {
      context.dataStore.edit { prefs ->
        prefs[gesture.actionKey] = action.id
        if (appPackage != null) prefs[gesture.appKey] = appPackage else prefs.remove(gesture.appKey)
      }
    }
  }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(vertical = 16.dp)) {
      Text(
        "Gestures",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
      Text(
        "What a swipe on the home screen does. Set one to Nothing if you trigger it by accident.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
      )
      val current = bindings
      HomeGesture.entries.forEach { gesture ->
        val binding = current?.get(gesture)
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .heightIn(min = 64.dp)
              .clickable(enabled = binding != null) { editing = gesture }
              .padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(40.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                gesture.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
              )
            }
          }
          Column(Modifier.weight(1f)) {
            Text(gesture.label, style = MaterialTheme.typography.bodyLarge)
            Text(
              binding?.let { actionSummary(context, it) } ?: "",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Text(
        "Swipes on the built-in keyboard follow the same choices.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
      )
    }
  }

  val editingGesture = editing
  val editingBinding = editingGesture?.let { bindings?.get(it) }
  if (editingGesture != null && editingBinding != null) {
    GestureActionDialog(
      gesture = editingGesture,
      selected = editingBinding.action,
      onPick = { action ->
        editing = null
        if (action == GestureAction.OpenApp) pickingAppFor = editingGesture
        else save(editingGesture, action)
      },
      onDismiss = { editing = null },
    )
  }

  val appGesture = pickingAppFor
  if (appGesture != null) {
    AppPickerDialog(
      title = appGesture.label,
      onPick = { app ->
        pickingAppFor = null
        save(appGesture, GestureAction.OpenApp, app.packageName)
      },
      onDismiss = { pickingAppFor = null },
    )
  }
}

private fun actionSummary(context: android.content.Context, binding: GestureBinding): String {
  if (binding.action != GestureAction.OpenApp) return binding.action.label
  val pkg = binding.appPackage ?: return binding.action.label
  val label =
    try {
      val pm = context.packageManager
      pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
      pkg
    }
  return "Open $label"
}

@Composable
private fun GestureActionDialog(
  gesture: HomeGesture,
  selected: GestureAction,
  onPick: (GestureAction) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(gesture.label) },
    text = {
      Column(Modifier.selectableGroup()) {
        GestureAction.entries.forEach { action ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 52.dp)
                .selectable(
                  selected = action == selected,
                  onClick = { onPick(action) },
                  role = Role.RadioButton,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            RadioButton(selected = action == selected, onClick = null)
            Column {
              Text(
                if (action == GestureAction.OpenApp) "Open an app…" else action.label,
                style = MaterialTheme.typography.bodyLarge,
              )
              val hint =
                when (action) {
                  GestureAction.Nothing -> "The swipe is ignored"
                  GestureAction.OpenApp -> "Pick any installed app"
                  else -> null
                }
              if (hint != null) {
                Text(
                  hint,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun AppPickerDialog(title: String, onPick: (GestureApp) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val apps by
    remember(context) {
        kotlinx.coroutines.flow.flow {
          emit(withContext(Dispatchers.IO) { loadLaunchableApps(context) })
        }
      }
      .collectAsState(initial = null)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      val list = apps
      if (list == null) {
        Text("Loading apps…", color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
          items(list, key = { it.packageName }) { app ->
            Text(
              app.label,
              style = MaterialTheme.typography.bodyLarge,
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { onPick(app) }
                  .heightIn(min = 48.dp)
                  .padding(vertical = 12.dp),
            )
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

private fun loadLaunchableApps(context: android.content.Context): List<GestureApp> {
  val pm = context.packageManager
  val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  return pm
    .queryIntentActivities(intent, 0)
    .map { GestureApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
    .filter { it.packageName != context.packageName }
    .distinctBy { it.packageName }
    .sortedBy { it.label.lowercase() }
}
