package com.searchlauncher.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.searchlauncher.app.data.SearchOptions
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.data.favoriteKey
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.ThemedIcons
import com.searchlauncher.app.ui.dataStore
import com.searchlauncher.app.ui.toImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Query-time shortcut bar. Swipe sideways to reach shortcuts that do not fit, and the last cell
 * opens custom shortcut settings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchShortcutStrip(
  entries: List<SearchOptions.QueryBarEntry>,
  results: Map<String, SearchResult>,
  minIconSizeSetting: Int,
  onLaunch: (SearchResult) -> Unit,
  onOpenSettings: () -> Unit,
  menuActions: ((SearchResult) -> ResultMenuActions)? = null,
  isItemFavorite: (SearchResult) -> Boolean = { false },
) {
  val iconSize = minIconSizeSetting.dp
  val haptic = LocalHapticFeedback.current
  var menuKey by remember { mutableStateOf<String?>(null) }
  val optionResults =
    remember(entries, results) {
      entries.mapNotNull { entry ->
        (entry as? SearchOptions.QueryBarEntry.Option)?.let { results[it.shortcut.id] }
      }
    }
  val iconBitmaps = rememberShortcutIcons(optionResults)

  LazyRow(
    modifier = Modifier.fillMaxWidth(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    items(
      items = entries,
      key = { entry ->
        when (entry) {
          is SearchOptions.QueryBarEntry.Option -> entry.shortcut.id
          SearchOptions.QueryBarEntry.Settings -> SearchOptions.QUERY_BAR_SETTINGS
        }
      },
    ) { entry ->
      when (entry) {
        is SearchOptions.QueryBarEntry.Option -> {
          val result = results[entry.shortcut.id] ?: return@items
          ShortcutCell(
            result = result,
            icon = iconBitmaps[result.favoriteKey],
            iconSize = iconSize,
            menuExpanded = menuKey == result.favoriteKey,
            onLaunch = { onLaunch(result) },
            onLongPress = {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              menuKey = result.favoriteKey
            },
            onDismissMenu = { menuKey = null },
            isFavorite = isItemFavorite(result),
            menuActions = menuActions?.invoke(result),
          )
        }
        SearchOptions.QueryBarEntry.Settings ->
          SettingsCell(iconSize = iconSize, onOpenSettings = onOpenSettings)
      }
    }
  }
}

@Composable
private fun rememberShortcutIcons(results: List<SearchResult>): Map<String, ImageBitmap?> {
  val context = LocalContext.current
  val themedIcons by
    remember { context.dataStore.data.map { it[PreferencesKeys.THEMED_ICONS] ?: false } }
      .collectAsState(initial = false)
  val themeBg = MaterialTheme.colorScheme.primary.toArgb()
  val themeFg = MaterialTheme.colorScheme.onPrimary.toArgb()
  val plain = remember(results) { results.associate { it.favoriteKey to it.icon?.toImageBitmap() } }
  val themed by
    produceState<Map<String, ImageBitmap?>?>(null, results, themedIcons, themeBg, themeFg) {
      value =
        if (!themedIcons) null
        else
          results.associate { result ->
            result.favoriteKey to
              withContext(Dispatchers.IO) {
                val source =
                  ThemedIcons.resolveThemeable(
                    context,
                    result.icon,
                    (result as? SearchResult.App)?.packageName,
                  )
                ThemedIcons.apply(source, themeBg, themeFg)?.toImageBitmap()
              }
          }
    }
  return themed ?: plain
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutCell(
  result: SearchResult,
  icon: ImageBitmap?,
  iconSize: Dp,
  menuExpanded: Boolean,
  onLaunch: () -> Unit,
  onLongPress: () -> Unit,
  onDismissMenu: () -> Unit,
  isFavorite: Boolean,
  menuActions: ResultMenuActions?,
) {
  Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
    Box(
      modifier =
        Modifier.size(iconSize)
          .clip(RoundedCornerShape(12.dp))
          .combinedClickable(role = Role.Button, onClick = onLaunch, onLongClick = onLongPress),
      contentAlignment = Alignment.Center,
    ) {
      if (icon != null) {
        Image(
          bitmap = icon,
          contentDescription = result.title,
          contentScale = ContentScale.Fit,
          modifier = Modifier.size(iconSize * 0.8f),
        )
      } else {
        Box(
          modifier =
            Modifier.size(iconSize * 0.7f)
              .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
              ),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = result.title.firstOrNull()?.toString() ?: "?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    DropdownMenu(
      expanded = menuExpanded,
      onDismissRequest = onDismissMenu,
      modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
      properties = PopupProperties(focusable = false),
    ) {
      val actions = menuActions ?: ResultMenuActions()
      ResultContextMenuItems(
        result = result,
        isFavorite = isFavorite,
        actions = actions,
        contactChatActions = rememberContactChatActions(result, actions),
        onCloseMenu = onDismissMenu,
      )
    }
  }
}

@Composable
private fun SettingsCell(iconSize: Dp, onOpenSettings: () -> Unit) {
  Box(
    modifier =
      Modifier.size(iconSize)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
        .clickable(role = Role.Button, onClick = onOpenSettings),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Default.Settings,
      contentDescription = "Custom shortcut settings",
      modifier = Modifier.size(iconSize * 0.55f),
      tint = MaterialTheme.colorScheme.onSurface,
    )
  }
}
