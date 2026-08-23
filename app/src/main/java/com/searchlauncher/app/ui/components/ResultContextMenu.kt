package com.searchlauncher.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.data.ContactChatAction
import com.searchlauncher.app.data.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Web pages in the index: passively recorded history, and explicitly saved bookmarks. */
internal val SearchResult.isWebPage: Boolean
  get() = namespace == "web_bookmarks" || namespace == "web_saved"

/**
 * Everything a result can be asked to do, gathered in one place so that every list showing results
 * can offer the same menu.
 *
 * It exists because the favourites bar used to build its own, much shorter menu: long-pressing an
 * app in the results offered a dozen actions and long-pressing the same app in the favourites bar
 * offered two. Which actions apply is already decided per result by which of these is non-null, so
 * the answer should not also depend on which list you happened to press.
 */
@Immutable
class ResultMenuActions(
  val onToggleFavorite: (() -> Unit)? = null,
  val onEditSnippet: (() -> Unit)? = null,
  val onCreateSnippet: (() -> Unit)? = null,
  val onEditShortcut: (() -> Unit)? = null,
  val onDeleteShortcut: (() -> Unit)? = null,
  val onRemoveFromIndex: (() -> Unit)? = null,
  val onRemoveBookmark: (() -> Unit)? = null,
  val onEditBookmark: (() -> Unit)? = null,
  val onAddBookmark: (() -> Unit)? = null,
  val onClearSearchResults: (() -> Unit)? = null,
  val onOpenTab: (() -> Unit)? = null,
  val onOpenPrivate: (() -> Unit)? = null,
  val onCloseTab: (() -> Unit)? = null,
  val onCloseAllTabs: (() -> Unit)? = null,
  val onCopyUrl: (() -> Unit)? = null,
  val onContactChatAction: ((SearchResult.Contact, ContactChatAction) -> Unit)? = null,
)

/**
 * The chat apps this contact can be reached on, or empty when
 * [ResultMenuActions.onContactChatAction] is absent and there would be nothing to do with them.
 * Loaded off the index, so it arrives a frame or two after the result does.
 */
@Composable
internal fun rememberContactChatActions(
  result: SearchResult,
  actions: ResultMenuActions,
): List<ContactChatAction> {
  val context = LocalContext.current
  val searchRepository = remember {
    (context.applicationContext as SearchLauncherApp).searchRepository
  }
  var chatActions by remember(result.id) { mutableStateOf<List<ContactChatAction>>(emptyList()) }
  LaunchedEffect(result.id, actions.onContactChatAction) {
    chatActions =
      if (result is SearchResult.Contact && actions.onContactChatAction != null) {
        searchRepository.getContactChatActions(result)
      } else {
        emptyList()
      }
  }
  return chatActions
}

/** Whether [result] has anything to offer, and so whether a menu should open for it at all. */
internal fun ResultMenuActions.hasItemsFor(
  result: SearchResult,
  contactChatActions: List<ContactChatAction>,
): Boolean =
  result !is SearchResult.IndexingIndicator &&
    (result is SearchResult.Snippet ||
      (result is SearchResult.App && !result.isPrivate) ||
      contactChatActions.isNotEmpty() ||
      onToggleFavorite != null ||
      onEditShortcut != null ||
      onDeleteShortcut != null ||
      onRemoveFromIndex != null ||
      onOpenTab != null ||
      onOpenPrivate != null ||
      (result.isWebPage &&
        (onEditBookmark != null || onAddBookmark != null || onRemoveBookmark != null)) ||
      (result is SearchResult.BrowserTab &&
        (onAddBookmark != null ||
          onCopyUrl != null ||
          onCloseTab != null ||
          onCloseAllTabs != null)))

/**
 * The body of a result's context menu — the caller supplies the `DropdownMenu` around it, so the
 * same list can hang off a row in the results or an icon in the favourites bar.
 *
 * The favourite entry is the one thing that legitimately reads differently between the two: in the
 * results it usually offers to add, in the favourites bar it always offers to remove. That falls
 * out of [isFavorite] rather than being a difference in the menus.
 */
@Composable
internal fun ResultContextMenuItems(
  result: SearchResult,
  isFavorite: Boolean,
  actions: ResultMenuActions,
  contactChatActions: List<ContactChatAction>,
  onCloseMenu: () -> Unit,
) {
  val context = LocalContext.current

  if (result is SearchResult.Contact && actions.onContactChatAction != null) {
    contactChatActions.forEach { action ->
      DropdownMenuItem(
        text = { Text(action.label) },
        onClick = {
          actions.onContactChatAction.invoke(result, action)
          onCloseMenu()
        },
        leadingIcon = { ContactChatActionIcon(action = action) },
      )
    }
  }

  if (actions.onOpenTab != null) {
    DropdownMenuItem(
      text = { Text("Open tab") },
      onClick = {
        onCloseMenu()
        actions.onOpenTab.invoke()
      },
    )
  }

  if (actions.onOpenPrivate != null) {
    DropdownMenuItem(
      text = { Text("Open private") },
      onClick = {
        onCloseMenu()
        actions.onOpenPrivate.invoke()
      },
    )
  }

  if (result is SearchResult.Snippet) {
    DropdownMenuItem(
      text = { Text("Edit") },
      onClick = {
        actions.onEditSnippet?.invoke()
        onCloseMenu()
      },
      leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
    )
    DropdownMenuItem(
      text = { Text("Delete") },
      onClick = {
        val app = context.applicationContext as SearchLauncherApp
        CoroutineScope(Dispatchers.IO).launch { app.snippetsRepository.deleteItem(result.alias) }
        onCloseMenu()
      },
      leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
    )
    DropdownMenuItem(
      text = { Text("Create New") },
      onClick = {
        actions.onCreateSnippet?.invoke()
        onCloseMenu()
      },
      leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
    )
  }

  if (actions.onEditShortcut != null) {
    DropdownMenuItem(
      text = { Text("Edit Shortcut") },
      onClick = {
        actions.onEditShortcut.invoke()
        onCloseMenu()
      },
      leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
    )
  }

  if (actions.onDeleteShortcut != null) {
    DropdownMenuItem(
      text = { Text("Remove Shortcut") },
      onClick = {
        actions.onDeleteShortcut.invoke()
        onCloseMenu()
      },
      leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
    )
  }

  if (actions.onToggleFavorite != null) {
    AppActionsMenuItems(
      result = result,
      isFavorite = isFavorite,
      onToggleFavorite = actions.onToggleFavorite,
      onCloseMenu = onCloseMenu,
      showAppInfo = false,
    )
  }

  if (result is SearchResult.App && !result.isPrivate) {
    AppActionsMenuItems(
      result = result,
      isFavorite = isFavorite,
      onToggleFavorite = null,
      onCloseMenu = onCloseMenu,
      showUninstall = true,
      onClearSearchResults = actions.onClearSearchResults,
    )
  }

  WebPageMenuItems(
    result = result,
    onEditBookmark = actions.onEditBookmark,
    onAddBookmark = actions.onAddBookmark,
    onRemoveBookmark = actions.onRemoveBookmark,
    onCloseMenu = onCloseMenu,
  )

  BrowserTabMenuItems(
    result = result,
    onAddBookmark = actions.onAddBookmark,
    onCopyUrl = actions.onCopyUrl,
    onCloseTab = actions.onCloseTab,
    onCloseAllTabs = actions.onCloseAllTabs,
    onCloseMenu = onCloseMenu,
  )

  if (actions.onRemoveFromIndex != null) {
    DropdownMenuItem(
      text = { Text("Remove from Index") },
      onClick = {
        actions.onRemoveFromIndex.invoke()
        onCloseMenu()
      },
      leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
    )
  }
}

/**
 * Actions for a page open in the browser. Ordered so the two that discard something sit at the
 * bottom, away from the ones that do not.
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
 * Bookmark actions for indexed web pages; which items appear depends on whether the page is saved
 * or just history.
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
