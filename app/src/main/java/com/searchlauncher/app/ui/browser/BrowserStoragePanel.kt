package com.searchlauncher.app.ui.browser

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

@Composable
internal fun BrowserStoragePanel(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val storage = remember { BrowserStorage() }
  val scope = rememberCoroutineScope()
  var sites by remember { mutableStateOf<List<SiteStorage>>(emptyList()) }
  var busy by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var query by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf(false) }
  var target by remember { mutableStateOf<String?>(null) }
  var supported by remember { mutableStateOf(false) }
  suspend fun refresh() {
    busy = true
    error = null
    try {
      supported = storage.canClear
      sites = storage.load()
    } catch (e: Exception) {
      if (e is CancellationException && e !is TimeoutCancellationException) throw e
      error = "Could not read website storage. Try refreshing."
    } finally {
      busy = false
    }
  }
  LaunchedEffect(Unit) { refresh() }
  Dialog(
    onDismissRequest = { if (!busy) onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    val view = LocalView.current
    val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    SideEffect {
      (view.parent as? DialogWindowProvider)?.window?.let { window ->
        WindowCompat.getInsetsController(window, view).apply {
          isAppearanceLightStatusBars = lightSurface
          isAppearanceLightNavigationBars = lightSurface
        }
      }
    }
    Surface(modifier = Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onDismiss, enabled = !busy) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
          }
          Text(
            "Website storage",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = { scope.launch { refresh() } }, enabled = !busy) {
            Icon(Icons.Default.Refresh, "Refresh storage")
          }
        }
        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(vertical = 12.dp),
        ) {
          item {
            Card(
              colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
              Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text("Reported site storage", style = MaterialTheme.typography.labelLarge)
                Text(
                  if (busy) "Measuring…"
                  else if (error != null) "Unavailable"
                  else Formatter.formatFileSize(context, sites.sumOf { it.bytes }),
                  style = MaterialTheme.typography.displaySmall,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  "${sites.size} domains · largest first",
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
            }
          }
          item {
            Text(
              "Sizes cover website databases and offline storage reported by WebView, not cookies, local storage or all cached files. Sites using only those may not appear. Private browsing is separate.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          item {
            OutlinedTextField(
              value = query,
              onValueChange = { query = it },
              label = { Text("Find a domain") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
          if (busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
          error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
          if (!busy && !supported)
            item {
              Text(
                "Update Android System WebView to enable clearing website data.",
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          val filtered = sites.filter { it.domain.contains(query.trim(), ignoreCase = true) }
          if (!busy && error == null && filtered.isEmpty())
            item {
              Text(
                if (sites.isEmpty()) "No website storage reported" else "No matching domains",
                modifier = Modifier.padding(vertical = 20.dp),
              )
            }
          items(filtered, key = { it.domain }) { site ->
            Card(modifier = Modifier.fillMaxWidth()) {
              Row(
                Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(site.domain, style = MaterialTheme.typography.bodyLarge)
                  Text(
                    Formatter.formatFileSize(context, site.bytes),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
                IconButton(
                  onClick = {
                    target = site.domain
                    confirm = true
                  },
                  enabled = supported && !busy,
                ) {
                  Icon(Icons.Default.DeleteOutline, "Clear data for ${site.domain}")
                }
              }
            }
          }
          item {
            OutlinedButton(
              onClick = {
                target = null
                confirm = true
              },
              enabled = supported && !busy,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Clear all website data")
            }
            Text(
              "Includes cookies and cached files, even for sites not listed above. Downloads and browsing history are kept.",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(top = 8.dp),
            )
          }
        }
      }
    }
    if (confirm)
      AlertDialog(
        onDismissRequest = { confirm = false },
        title = {
          Text(if (target == null) "Clear all website data?" else "Clear data for $target?")
        },
        text = {
          Text(
            (if (target == null)
              "This removes cookies, cached files and saved website data for all sites."
            else
              "This removes cookies, cached files and saved website data for this site's entire domain, including its other subdomains.") +
              " You may be signed out and lose offline content. Open pages can store data again."
          )
        },
        confirmButton = {
          TextButton(
            onClick = {
              confirm = false
              scope.launch {
                busy = true
                error = null
                try {
                  storage.clear(target)
                  refresh()
                } catch (e: Exception) {
                  if (e is CancellationException && e !is TimeoutCancellationException) throw e
                  error = "Could not confirm clearing website data. Refresh to check and try again."
                } finally {
                  busy = false
                }
              }
            }
          ) {
            Text("Clear data")
          }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
      )
  }
}
