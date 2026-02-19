package com.searchlauncher.app.ui.components

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.ui.toImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultItem(
  result: SearchResult,
  isFavorite: Boolean = false,
  onToggleFavorite: (() -> Unit)? = null,
  onEditSnippet: (() -> Unit)? = null,
  onCreateSnippet: (() -> Unit)? = null,
  onEditShortcut: (() -> Unit)? = null,
  onDeleteShortcut: (() -> Unit)? = null,
  onRemoveFromIndex: (() -> Unit)? = null,
  onRemoveBookmark: (() -> Unit)? = null,
  onClick: () -> Unit,
) {
  var showMenu by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val searchRepository = remember {
    (context.applicationContext as SearchLauncherApp).searchRepository
  }
  var iconState by remember(result.id) { mutableStateOf<Drawable?>(result.icon) }

  LaunchedEffect(result.id) {
    if (iconState == null) {
      iconState = searchRepository.loadIcon(result)
    }
  }

  Box {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .then(
            if (onToggleFavorite != null) {
              Modifier.combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            } else if (
              result is SearchResult.Snippet ||
                result is SearchResult.App ||
                result.namespace == "web_bookmarks" ||
                onEditShortcut != null ||
                onDeleteShortcut != null
            ) {
              Modifier.combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            } else {
              Modifier.clickable(onClick = onClick)
            }
          )
          .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(modifier = Modifier.size(40.dp)) {
        if (iconState != null) {
          val iconModifier =
            if (
              result is SearchResult.Contact ||
                result is SearchResult.Snippet ||
                result is SearchResult.SearchIntent
            ) {
              Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
            } else {
              Modifier.size(40.dp)
            }
          val imageBitmap = remember(iconState) { iconState?.toImageBitmap() }
          if (imageBitmap != null) {
            Image(
              bitmap = imageBitmap,
              contentDescription = null,
              modifier = iconModifier,
              contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
          }
        } else if (result is SearchResult.Contact) {
          Image(
            painter =
              androidx.compose.ui.res.painterResource(
                id = com.searchlauncher.app.R.drawable.ic_contact_default
              ),
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
          )
        }

        if (result is SearchResult.Shortcut && result.appIcon != null) {
          val appIconBitmap = remember(result.appIcon) { result.appIcon.toImageBitmap() }
          if (appIconBitmap != null) {
            Image(
              bitmap = appIconBitmap,
              contentDescription = null,
              modifier = Modifier.size(16.dp).align(Alignment.TopStart),
            )
          }
        }
      }

      Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
        Text(
          text = result.title,
          fontSize = 16.sp,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!result.subtitle.isNullOrBlank()) {
          Text(
            text = result.subtitle!!,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    if (showMenu) {
      val context = LocalContext.current
      DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        properties = PopupProperties(focusable = false),
      ) {
        if (result is SearchResult.Snippet) {
          DropdownMenuItem(
            text = { Text("Edit") },
            onClick = {
              onEditSnippet?.invoke()
              showMenu = false
            },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
          )
          DropdownMenuItem(
            text = { Text("Delete") },
            onClick = {
              val app = context.applicationContext as com.searchlauncher.app.SearchLauncherApp
              CoroutineScope(Dispatchers.IO).launch {
                app.snippetsRepository.deleteItem(result.alias)
              }
              showMenu = false
            },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
          )
          DropdownMenuItem(
            text = { Text("Create New") },
            onClick = {
              onCreateSnippet?.invoke()
              showMenu = false
            },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
          )
        }

        if (onEditShortcut != null) {
          DropdownMenuItem(
            text = { Text("Edit Shortcut") },
            onClick = {
              onEditShortcut.invoke()
              showMenu = false
            },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
          )
        }

        if (onDeleteShortcut != null) {
          DropdownMenuItem(
            text = { Text("Remove Shortcut") },
            onClick = {
              onDeleteShortcut.invoke()
              showMenu = false
            },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
          )
        }

        if (onRemoveFromIndex != null) {
          DropdownMenuItem(
            text = { Text("Remove from Index") },
            onClick = {
              onRemoveFromIndex()
              showMenu = false
            },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
          )
        }

        if (onToggleFavorite != null) {
          DropdownMenuItem(
            text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
            onClick = {
              onToggleFavorite()
              showMenu = false
            },
            leadingIcon = {
              Icon(
                imageVector =
                  if (isFavorite) {
                    Icons.Default.Star
                  } else {
                    Icons.Default.StarBorder
                  },
                contentDescription = null,
              )
            },
          )
        }

        if (result is SearchResult.App) {
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
              showMenu = false
            },
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
          )
          DropdownMenuItem(
            text = { Text("Uninstall") },
            onClick = {
              try {
                // Use Uri.fromParts for cleaner URI construction
                val packageUri = Uri.fromParts("package", result.packageName, null)
                val intent = Intent(Intent.ACTION_DELETE, packageUri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                // Optional: Feedback to user, in case the system dialog is slow
                // Toast.makeText(context, "Requesting uninstall...", Toast.LENGTH_SHORT).show()
              } catch (e: Exception) {
                Toast.makeText(context, "Cannot start uninstall: ${e.message}", Toast.LENGTH_SHORT)
                  .show()
              }
              showMenu = false
            },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
          )
        }

        if (onRemoveBookmark != null && result.namespace == "web_bookmarks") {
          DropdownMenuItem(
            text = { Text("Remove Bookmark") },
            onClick = {
              onRemoveBookmark()
              showMenu = false
            },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
          )
        }
      }
    }
  }
}
