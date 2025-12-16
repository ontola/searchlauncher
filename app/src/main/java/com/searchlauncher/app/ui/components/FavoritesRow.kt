package com.searchlauncher.app.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.ui.toImageBitmap

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesRow(
  favorites: List<SearchResult>,
  onLaunch: (SearchResult) -> Unit,
  onRemoveFavorite: (SearchResult) -> Unit,
) {
  BoxWithConstraints(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
  ) {
    val totalWidth = maxWidth
    val count = favorites.size
    // Calculate max possible size per item including spacing
    // If count is 0, doesn't matter.
    // We want at least some spacing.
    val minSpacing = 4.dp
    // Width available per item: (Total - (Count-1)*Spacing) / Count
    // But we can just use weight or precise sizing.
    // Simple approach:
    // val itemWidth = (totalWidth - (minSpacing * (count - 1).coerceAtLeast(0))) / count
    // scaledSize = min(48.dp, itemWidth)

    val calculatedSize =
      if (count > 0) {
        (totalWidth - (minSpacing * (count - 1))) / count
      } else {
        48.dp
      }

    val finalIconSize = minOf(48.dp, calculatedSize)

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center, // Center if few items
    ) {
      favorites.forEachIndexed { index, result ->
        if (index > 0) {
          Spacer(modifier = Modifier.width(minSpacing))
        }

        var showMenu by remember { mutableStateOf(false) }

        Box(
          modifier =
            Modifier.size(finalIconSize)
              .clip(RoundedCornerShape(12.dp))
              .combinedClickable(onClick = { onLaunch(result) }, onLongClick = { showMenu = true }),
          contentAlignment = Alignment.Center,
        ) {
          val imageBitmap = result.icon?.toImageBitmap()
          if (imageBitmap != null) {
            // Scale inner image slightly smaller than container
            val imageSize = finalIconSize * 0.8f
            Image(
              bitmap = imageBitmap,
              contentDescription = result.title,
              modifier = Modifier.size(imageSize),
            )
          }

          DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            properties = PopupProperties(focusable = false),
          ) {
            DropdownMenuItem(
              text = { Text("Remove from Favorites") },
              onClick = {
                onRemoveFavorite(result)
                showMenu = false
              },
              leadingIcon = { Icon(imageVector = Icons.Default.Star, contentDescription = null) },
            )

            if (result is SearchResult.App) {
              val context = LocalContext.current
              DropdownMenuItem(
                text = { Text("App Info") },
                onClick = {
                  try {
                    val intent =
                      Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${result.packageName}")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                  } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open App Info", Toast.LENGTH_SHORT).show()
                  }
                  showMenu = false
                },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
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
                    Toast.makeText(context, "Cannot start uninstall", Toast.LENGTH_SHORT).show()
                  }
                  showMenu = false
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
              )
            }
          }
        }
      }
    }
  }
}
