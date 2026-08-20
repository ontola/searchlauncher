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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
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
import com.searchlauncher.app.data.ContactActionGlyph
import com.searchlauncher.app.data.ContactChatAction
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.ui.rememberThemedIconBitmap
import com.searchlauncher.app.ui.toImageBitmap
import com.searchlauncher.app.util.traceSection

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultItem(
  result: SearchResult,
  highlighted: Boolean = false,
  isFavorite: Boolean = false,
  actions: ResultMenuActions = ResultMenuActions(),
  onClick: () -> Unit,
) {
  var showMenu by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val searchRepository = remember {
    (context.applicationContext as SearchLauncherApp).searchRepository
  }
  var iconState by remember(result.id) { mutableStateOf<Drawable?>(result.icon) }
  // Results that arrive without one — a contact's photo is read off the provider, not the index —
  // fetch it here. Without this they render as a blank space where the icon should be.
  LaunchedEffect(result.id) {
    if (iconState == null && result !is SearchResult.IndexingIndicator) {
      iconState =
        traceSection("SL:SearchResultItem.loadIcon:${result.namespace}") {
          searchRepository.loadIcon(result)
        }
    }
  }
  val contactChatActions = rememberContactChatActions(result, actions)

  // One menu per result, reachable two ways: long-pressing the row and tapping the overflow
  // button. Both open the same list, so what a result offers never depends on how you asked.
  val hasMenuItems = actions.hasItemsFor(result, contactChatActions)

  traceSection("SL:SearchResultItem.compose:${result.namespace}") {
    Box {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .then(
              if (highlighted) Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
              else Modifier
            )
            .then(
              if (result is SearchResult.IndexingIndicator) {
                Modifier
              } else if (hasMenuItems) {
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
            val imageBitmap = rememberThemedIconBitmap(iconState)
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

        // The single most likely thing to do with a contact stays on the row as a one-tap
        // button; the rest of its chat apps live in the menu with everything else.
        val onChatAction = actions.onContactChatAction
        if (
          result is SearchResult.Contact && onChatAction != null && contactChatActions.isNotEmpty()
        ) {
          ContactChatActionButton(
            action = contactChatActions.first(),
            onClick = { onChatAction(result, contactChatActions.first()) },
            modifier = Modifier.padding(start = 8.dp),
          )
        }

        if (hasMenuItems) {
          IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.padding(start = 8.dp).size(40.dp),
          ) {
            Icon(
              imageVector = Icons.Default.MoreVert,
              contentDescription = "More actions",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // A zero-size anchor at the trailing edge, where the overflow button is. A DropdownMenu
      // opens from whatever it is nested in, so leaving it at the row level put it a screen-width
      // from the button that opens it. Long-pressing the row lands here too, which is the same
      // menu arriving in the same place however it was asked for.
      Box(modifier = Modifier.align(Alignment.CenterEnd)) {
        if (showMenu) {
          val context = LocalContext.current
          DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            properties = PopupProperties(focusable = false),
          ) {
            ResultContextMenuItems(
              result = result,
              isFavorite = isFavorite,
              actions = actions,
              contactChatActions = contactChatActions,
              onCloseMenu = { showMenu = false },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ContactChatActionButton(
  action: ContactChatAction,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  IconButton(onClick = onClick, modifier = modifier.size(40.dp)) {
    ContactChatActionIcon(action = action)
  }
}

@Composable
internal fun ContactChatActionIcon(action: ContactChatAction) {
  // Call, SMS and email are the phone's own capabilities, so they get a Material glyph tinted to
  // the current content colour — legible on either theme and the same weight as every other icon
  // in the list. An installed app's icon is its own artwork and is drawn untouched.
  val glyph =
    when (action.glyph) {
      ContactActionGlyph.CALL -> Icons.Default.Call
      ContactActionGlyph.MESSAGE -> Icons.AutoMirrored.Filled.Message
      ContactActionGlyph.EMAIL -> Icons.Default.Email
      null -> null
    }
  if (glyph != null) {
    Icon(
      imageVector = glyph,
      contentDescription = action.label,
      modifier = Modifier.size(24.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }

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
