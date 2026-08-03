package com.searchlauncher.app.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
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
import com.searchlauncher.app.data.ContactChatAction
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.ui.toImageBitmap
import com.searchlauncher.app.util.traceSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Web pages in the index: passively recorded history, and explicitly saved bookmarks. */
private val SearchResult.isWebPage: Boolean
  get() = namespace == "web_bookmarks" || namespace == "web_saved"

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
  onEditBookmark: (() -> Unit)? = null,
  onAddBookmark: (() -> Unit)? = null,
  onClearSearchResults: (() -> Unit)? = null,
  onOpenTab: (() -> Unit)? = null,
  onOpenPrivate: (() -> Unit)? = null,
  onCloseTab: (() -> Unit)? = null,
  onCloseAllTabs: (() -> Unit)? = null,
  onCopyUrl: (() -> Unit)? = null,
  onContactChatAction: ((SearchResult.Contact, ContactChatAction) -> Unit)? = null,
  onClick: () -> Unit,
) {
  var showMenu by remember { mutableStateOf(false) }
  var showContactActionsMenu by remember { mutableStateOf(false) }
  var showAppActionsMenu by remember { mutableStateOf(false) }
  var showWebActionsMenu by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val searchRepository = remember {
    (context.applicationContext as SearchLauncherApp).searchRepository
  }
  var iconState by remember(result.id) { mutableStateOf<Drawable?>(result.icon) }
  var contactChatActions by
    remember(result.id) { mutableStateOf<List<ContactChatAction>>(emptyList()) }

  LaunchedEffect(result.id) {
    if (iconState == null && result !is SearchResult.IndexingIndicator) {
      iconState =
        traceSection("SL:SearchResultItem.loadIcon:${result.namespace}") {
          searchRepository.loadIcon(result)
        }
    }
  }

  LaunchedEffect(result.id, onContactChatAction) {
    contactChatActions =
      if (result is SearchResult.Contact && onContactChatAction != null) {
        searchRepository.getContactChatActions(result)
      } else {
        emptyList()
      }
  }

  traceSection("SL:SearchResultItem.compose:${result.namespace}") {
    Box {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .then(
              if (result is SearchResult.IndexingIndicator) {
                Modifier
              } else if (onToggleFavorite != null) {
                Modifier.combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
              } else if (
                result is SearchResult.Snippet ||
                  result is SearchResult.App ||
                  result.isWebPage ||
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
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
          if (result is SearchResult.IndexingIndicator) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.primary,
            )
          } else if (iconState != null) {
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
            val imageBitmap =
              remember(iconState) {
                traceSection("SL:SearchResultItem.toImageBitmap:${result.namespace}") {
                  iconState?.toImageBitmap()
                }
              }
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
            val appIconBitmap =
              remember(result.appIcon) {
                traceSection("SL:SearchResultItem.appIconBitmap") { result.appIcon.toImageBitmap() }
              }
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

        if (
          result is SearchResult.Contact &&
            onContactChatAction != null &&
            contactChatActions.isNotEmpty()
        ) {
          Row(
            modifier = Modifier.padding(start = 8.dp).widthIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
          ) {
            ContactChatActionButton(
              action = contactChatActions.first(),
              onClick = { onContactChatAction(result, contactChatActions.first()) },
            )

            if (contactChatActions.size > 1) {
              Box {
                IconButton(
                  onClick = { showContactActionsMenu = true },
                  modifier = Modifier.size(40.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More contact actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                if (showContactActionsMenu) {
                  DropdownMenu(
                    expanded = showContactActionsMenu,
                    onDismissRequest = { showContactActionsMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    properties = PopupProperties(focusable = false),
                  ) {
                    contactChatActions.forEach { action ->
                      DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = {
                          onContactChatAction(result, action)
                          showContactActionsMenu = false
                        },
                        leadingIcon = { ContactChatActionIcon(action = action) },
                      )
                    }
                  }
                }
              }
            }
          }
        }

        if (result is SearchResult.App) {
          Box(modifier = Modifier.padding(start = 8.dp)) {
            IconButton(onClick = { showAppActionsMenu = true }, modifier = Modifier.size(40.dp)) {
              Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More app actions",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            DropdownMenu(
              expanded = showAppActionsMenu,
              onDismissRequest = { showAppActionsMenu = false },
              modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
              properties = PopupProperties(focusable = false),
            ) {
              AppActionsMenuItems(
                result = result,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onCloseMenu = { showAppActionsMenu = false },
                showUninstall = true,
                onClearSearchResults = onClearSearchResults,
              )
            }
          }
        }

        val hasTabActions =
          result is SearchResult.BrowserTab &&
            (onCloseTab != null || onCloseAllTabs != null || onCopyUrl != null)
        if (onOpenTab != null || onOpenPrivate != null || hasTabActions) {
          Box(modifier = Modifier.padding(start = 8.dp)) {
            IconButton(onClick = { showWebActionsMenu = true }, modifier = Modifier.size(40.dp)) {
              Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More browser actions",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            DropdownMenu(
              expanded = showWebActionsMenu,
              onDismissRequest = { showWebActionsMenu = false },
              modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
              properties = PopupProperties(focusable = false),
            ) {
              if (onOpenTab != null) {
                DropdownMenuItem(
                  text = { Text("Open tab") },
                  onClick = {
                    showWebActionsMenu = false
                    onOpenTab()
                  },
                )
              }
              if (onOpenPrivate != null) {
                DropdownMenuItem(
                  text = { Text("Open private") },
                  onClick = {
                    showWebActionsMenu = false
                    onOpenPrivate()
                  },
                )
              }
              WebPageMenuItems(
                result = result,
                onEditBookmark = onEditBookmark,
                onAddBookmark = onAddBookmark,
                onRemoveBookmark = onRemoveBookmark,
                onCloseMenu = { showWebActionsMenu = false },
              )
              BrowserTabMenuItems(
                result = result,
                onAddBookmark = onAddBookmark,
                onCopyUrl = onCopyUrl,
                onCloseTab = onCloseTab,
                onCloseAllTabs = onCloseAllTabs,
                onCloseMenu = { showWebActionsMenu = false },
              )
            }
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
            AppActionsMenuItems(
              result = result,
              isFavorite = isFavorite,
              onToggleFavorite = onToggleFavorite,
              onCloseMenu = { showMenu = false },
              showAppInfo = false,
            )
          }

          if (result is SearchResult.App) {
            AppActionsMenuItems(
              result = result,
              isFavorite = isFavorite,
              onToggleFavorite = null,
              onCloseMenu = { showMenu = false },
              showUninstall = true,
              onClearSearchResults = onClearSearchResults,
            )
          }

          WebPageMenuItems(
            result = result,
            onEditBookmark = onEditBookmark,
            onAddBookmark = onAddBookmark,
            onRemoveBookmark = onRemoveBookmark,
            onCloseMenu = { showMenu = false },
          )

          BrowserTabMenuItems(
            result = result,
            onAddBookmark = onAddBookmark,
            onCopyUrl = onCopyUrl,
            onCloseTab = onCloseTab,
            onCloseAllTabs = onCloseAllTabs,
            onCloseMenu = { showMenu = false },
          )
        }
      }
    }
  }
}

/**
 * Actions for a page open in the browser. Shared by the overflow button and the long-press menu so
 * both stay in step. Ordered so the two that discard something sit at the bottom, away from the
 * ones that do not.
 */
@Composable
private fun BrowserTabMenuItems(
  result: SearchResult,
  onAddBookmark: (() -> Unit)?,
  onCopyUrl: (() -> Unit)?,
  onCloseTab: (() -> Unit)?,
  onCloseAllTabs: (() -> Unit)?,
  onCloseMenu: () -> Unit,
) {
  if (result !is SearchResult.BrowserTab) return

  if (onAddBookmark != null) {
    DropdownMenuItem(
      text = { Text("Create bookmark") },
      onClick = {
        onCloseMenu()
        onAddBookmark()
      },
      leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
    )
  }
  if (onCopyUrl != null) {
    DropdownMenuItem(
      text = { Text("Copy URL") },
      onClick = {
        onCloseMenu()
        onCopyUrl()
      },
      leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
    )
  }
  if (onCloseTab != null) {
    DropdownMenuItem(
      text = { Text("Close tab") },
      onClick = {
        onCloseMenu()
        onCloseTab()
      },
      leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
    )
  }
  if (onCloseAllTabs != null) {
    DropdownMenuItem(
      text = { Text("Close all tabs") },
      onClick = {
        onCloseMenu()
        onCloseAllTabs()
      },
      leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
    )
  }
}

/**
 * Bookmark actions for indexed web pages. Shared by the overflow button and the long-press menu so
 * both stay in step; which items appear depends on whether the page is saved or just history.
 */
@Composable
private fun WebPageMenuItems(
  result: SearchResult,
  onEditBookmark: (() -> Unit)?,
  onAddBookmark: (() -> Unit)?,
  onRemoveBookmark: (() -> Unit)?,
  onCloseMenu: () -> Unit,
) {
  if (!result.isWebPage) return
  val isSavedBookmark = result.namespace == "web_saved"

  if (isSavedBookmark && onEditBookmark != null) {
    DropdownMenuItem(
      text = { Text("Edit bookmark") },
      onClick = {
        onCloseMenu()
        onEditBookmark()
      },
      leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
    )
  }
  if (!isSavedBookmark && onAddBookmark != null) {
    DropdownMenuItem(
      text = { Text("Add as bookmark") },
      onClick = {
        onCloseMenu()
        onAddBookmark()
      },
      leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
    )
  }
  if (onRemoveBookmark != null) {
    DropdownMenuItem(
      text = { Text(if (isSavedBookmark) "Remove bookmark" else "Remove from history") },
      onClick = {
        onCloseMenu()
        onRemoveBookmark()
      },
      leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
    )
  }
}

@Composable
private fun ContactChatActionButton(action: ContactChatAction, onClick: () -> Unit) {
  IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
    ContactChatActionIcon(action = action)
  }
}

@Composable
private fun ContactChatActionIcon(action: ContactChatAction) {
  val imageBitmap =
    remember(action.icon) {
      traceSection("SL:SearchResultItem.contactChatIcon") { action.icon?.toImageBitmap() }
    }
  if (imageBitmap != null) {
    Image(
      bitmap = imageBitmap,
      contentDescription = action.label,
      modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)),
      contentScale = androidx.compose.ui.layout.ContentScale.Fit,
    )
  } else {
    Icon(
      imageVector = Icons.Default.MoreVert,
      contentDescription = action.label,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
