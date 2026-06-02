package com.searchlauncher.app.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.searchlauncher.app.data.SearchResult

@Composable
fun AppActionsMenuItems(
  result: SearchResult,
  isFavorite: Boolean,
  onToggleFavorite: (() -> Unit)?,
  onCloseMenu: () -> Unit,
  favoriteAddLabel: String = "Add to Favorites",
  favoriteRemoveLabel: String = "Remove from Favorites",
  showAppInfo: Boolean = true,
  showUninstall: Boolean = false,
  onClearSearchResults: (() -> Unit)? = null,
) {
  val context = LocalContext.current

  if (onToggleFavorite != null) {
    DropdownMenuItem(
      text = { Text(if (isFavorite) favoriteRemoveLabel else favoriteAddLabel) },
      onClick = {
        onToggleFavorite()
        onCloseMenu()
      },
      leadingIcon = {
        Icon(
          imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
          contentDescription = null,
        )
      },
    )
  }

  if (result is SearchResult.App && showAppInfo) {
    DropdownMenuItem(
      text = { Text("App Info") },
      onClick = {
        try {
          val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
          intent.data = Uri.parse("package:${result.packageName}")
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          context.startActivity(intent)
        } catch (e: Exception) {
          Toast.makeText(context, "Cannot open App Info", Toast.LENGTH_SHORT).show()
        }
        onCloseMenu()
      },
      leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
    )
  }

  if (result is SearchResult.App && showUninstall) {
    DropdownMenuItem(
      text = { Text("Uninstall") },
      onClick = {
        try {
          val packageUri = Uri.fromParts("package", result.packageName, null)
          val intent = Intent(Intent.ACTION_DELETE, packageUri)
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          context.startActivity(intent)
          onClearSearchResults?.invoke()
        } catch (e: Exception) {
          Toast.makeText(context, "Cannot start uninstall: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        onCloseMenu()
      },
      leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
    )
  }
}
