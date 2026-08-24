package com.searchlauncher.app.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WidgetPicker(
  appWidgetManager: android.appwidget.AppWidgetManager,
  onWidgetSelected: (AppWidgetProviderInfo) -> Unit,
  onDismiss: () -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  var allWidgets by remember { mutableStateOf<List<AppWidgetProviderInfo>>(emptyList()) }
  val context = LocalContext.current

  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) { allWidgets = appWidgetManager.installedProviders }
  }

  val filteredWidgets =
    remember(searchQuery, allWidgets) {
      if (searchQuery.isBlank()) {
        allWidgets
      } else {
        allWidgets.filter {
          it.label.contains(searchQuery, ignoreCase = true) ||
            it.provider.packageName.contains(searchQuery, ignoreCase = true)
        }
      }
    }

  Column(
    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).padding(16.dp)
  ) {
    // List takes up available space
    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      items(filteredWidgets) { widgetInfo ->
        WidgetListItem(widgetInfo, context.packageManager) { onWidgetSelected(widgetInfo) }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Search Bar at the Bottom
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onDismiss) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
      }

      TextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        modifier = Modifier.weight(1f),
        placeholder = { Text("Search widgets...", color = Color.Gray) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
        colors =
          TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
          ),
        singleLine = true,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
      )
    }
  }
}

@Composable
fun WidgetListItem(
  info: AppWidgetProviderInfo,
  packageManager: PackageManager,
  onClick: () -> Unit,
) {
  var icon by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
  var label by remember { mutableStateOf("") }

  val context = LocalContext.current
  LaunchedEffect(info) {
    withContext(Dispatchers.IO) {
      label = info.loadLabel(packageManager)
      val drawable = info.loadIcon(context, 0) // 0 for required density, or generic
      icon = drawable?.toBitmap()?.asImageBitmap()
    }
  }

  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Image(bitmap = icon!!, contentDescription = null, modifier = Modifier.size(48.dp))
    } else {
      Box(modifier = Modifier.size(48.dp).background(Color.Gray))
    }
    Spacer(modifier = Modifier.width(16.dp))
    Column {
      Text(text = label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
      // minWidth/minHeight arrive in pixels, so they have to come back down to dp before being
      // labelled as such — Chrome's Dino is a 110dp square, not the 358 it reports here.
      val density = LocalDensity.current.density
      Text(
        text =
          "${(info.minWidth / density).roundToInt()}x${(info.minHeight / density).roundToInt()} dp",
        color = Color.Gray,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}
