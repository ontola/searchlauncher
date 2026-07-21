package com.searchlauncher.app.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.flow.first

internal data class BrowserSiteSettings(
  val javaScriptEnabled: Boolean = true,
  val popupsEnabled: Boolean = false,
  val thirdPartyCookiesEnabled: Boolean = false,
)

@Composable
internal fun BrowserOverflowButton(
  desktopMode: Boolean,
  showFavorites: Boolean,
  tabCount: Int,
  hasPreviousTab: Boolean,
  hasNextTab: Boolean,
  menuColor: Color,
  menuContentColor: Color,
  openRequest: Long = 0L,
  onOpenRequestConsumed: () -> Unit = {},
  onShare: () -> Unit,
  onCopyUrl: () -> Unit,
  onToggleDesktopMode: () -> Unit,
  onOpenDownloads: () -> Unit,
  onFindInPage: () -> Unit,
  onPageSettings: () -> Unit,
  onToggleFavorites: () -> Unit,
  onNewTab: () -> Unit,
  onCloseTab: () -> Unit,
  onPreviousTab: () -> Unit,
  onNextTab: () -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }

  // Consume the request once handled — otherwise any later recomposition of the chrome (for
  // example after a search-overlay round trip) would see the stale value and reopen the menu.
  LaunchedEffect(openRequest) {
    if (openRequest != 0L) {
      expanded = true
      onOpenRequestConsumed()
    }
  }

  Box {
    IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp).padding(4.dp)) {
      Icon(
        imageVector = Icons.Default.MoreVert,
        contentDescription = "Browser menu",
        tint = LocalContentColor.current,
      )
    }
    if (tabCount > 1) {
      Badge(modifier = Modifier.align(Alignment.TopEnd)) { Text(tabCount.toString()) }
    }

    AnchoredDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      color = menuColor,
      contentColor = menuContentColor,
    ) {
      DropdownMenuItem(
        text = { Text("New tab") },
        onClick = {
          expanded = false
          onNewTab()
        },
        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
      )
      DropdownMenuItem(
        text = { Text("Close tab") },
        onClick = {
          expanded = false
          onCloseTab()
        },
        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
      )
      if (hasPreviousTab) {
        DropdownMenuItem(
          text = { Text("Previous tab") },
          onClick = {
            expanded = false
            onPreviousTab()
          },
          leadingIcon = { Icon(Icons.Default.SwipeRight, contentDescription = null) },
        )
      }
      if (hasNextTab) {
        DropdownMenuItem(
          text = { Text("Next tab") },
          onClick = {
            expanded = false
            onNextTab()
          },
          leadingIcon = { Icon(Icons.Default.SwipeLeft, contentDescription = null) },
        )
      }
      DropdownMenuItem(
        text = { Text("Share") },
        onClick = {
          expanded = false
          onShare()
        },
        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
      )
      DropdownMenuItem(
        text = { Text("Copy URL") },
        onClick = {
          expanded = false
          onCopyUrl()
        },
        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
      )
      DropdownMenuItem(
        text = { Text(if (desktopMode) "Phone view" else "Desktop view") },
        onClick = {
          expanded = false
          onToggleDesktopMode()
        },
        leadingIcon = {
          Icon(
            if (desktopMode) Icons.Default.PhoneAndroid else Icons.Default.DesktopWindows,
            contentDescription = null,
          )
        },
      )
      DropdownMenuItem(
        text = { Text("Open downloads") },
        onClick = {
          expanded = false
          onOpenDownloads()
        },
        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
      )
      DropdownMenuItem(
        text = { Text("Find in page") },
        onClick = {
          expanded = false
          onFindInPage()
        },
        leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null) },
      )
      DropdownMenuItem(
        text = { Text("Page settings") },
        onClick = {
          expanded = false
          onPageSettings()
        },
        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
      )
      DropdownMenuItem(
        text = { Text(if (showFavorites) "Hide favorites row" else "Show favorites row") },
        onClick = {
          expanded = false
          onToggleFavorites()
        },
        leadingIcon = {
          Icon(
            if (showFavorites) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = null,
          )
        },
      )
    }
  }
}

/**
 * Menu popup that always sits directly on top of its anchor. Material3's DropdownMenu keeps a 48dp
 * margin from screen edges, which floats the menu well above a bottom-bar anchor.
 */
@Composable
private fun AnchoredDropdownMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  color: Color = MaterialTheme.colorScheme.surface,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (!expanded) return
  val positionProvider = remember {
    object : PopupPositionProvider {
      override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
      ): IntOffset {
        val preferredX =
          if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - popupContentSize.width
          } else {
            anchorBounds.left
          }
        val x = preferredX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
      }
    }
  }
  Popup(
    popupPositionProvider = positionProvider,
    onDismissRequest = onDismissRequest,
    properties = PopupProperties(focusable = true),
  ) {
    // Dismiss when the popup's window loses focus (another activity opening over the browser),
    // instead of lingering until the user returns and taps outside.
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(Unit) {
      snapshotFlow { windowInfo.isWindowFocused }.first { it }
      snapshotFlow { windowInfo.isWindowFocused }.first { !it }
      onDismissRequest()
    }
    Surface(
      shape = MaterialTheme.shapes.extraSmall,
      color = color,
      contentColor = contentColor,
      shadowElevation = 3.dp,
    ) {
      // DropdownMenuItem reads onSurface/onSurfaceVariant from the theme rather than
      // LocalContentColor, so override those locally to match the page-derived colors.
      MaterialTheme(
        colorScheme =
          MaterialTheme.colorScheme.copy(
            onSurface = contentColor,
            onSurfaceVariant = contentColor.copy(alpha = 0.8f),
          )
      ) {
        Column(
          modifier =
            Modifier.width(IntrinsicSize.Max)
              .padding(vertical = 8.dp)
              .verticalScroll(rememberScrollState()),
          content = content,
        )
      }
    }
  }
}

@Composable
internal fun LinkContextMenuDialog(
  linkUrl: String?,
  imageUrl: String?,
  onOpenInNewTab: (String) -> Unit,
  onOpenPrivate: (String) -> Unit,
  onCopyUrl: (String) -> Unit,
  onShareUrl: (String) -> Unit,
  onDownloadImage: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val primaryUrl = linkUrl ?: imageUrl
  if (primaryUrl == null) {
    onDismiss()
    return
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = primaryUrl,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
    },
    text = {
      Column {
        DropdownMenuItem(
          text = { Text("Open in new tab") },
          onClick = {
            onDismiss()
            onOpenInNewTab(primaryUrl)
          },
          leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
        )
        DropdownMenuItem(
          text = { Text("Open incognito") },
          onClick = {
            onDismiss()
            onOpenPrivate(primaryUrl)
          },
          leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
        )
        DropdownMenuItem(
          text = { Text("Copy URL") },
          onClick = {
            onDismiss()
            onCopyUrl(primaryUrl)
          },
          leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
        )
        DropdownMenuItem(
          text = { Text("Share URL") },
          onClick = {
            onDismiss()
            onShareUrl(primaryUrl)
          },
          leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
        )
        if (imageUrl != null) {
          DropdownMenuItem(
            text = { Text("Download image") },
            onClick = {
              onDismiss()
              onDownloadImage(imageUrl)
            },
            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
internal fun FindInPageBar(
  query: String,
  activeMatch: Int,
  matchCount: Int,
  onQueryChange: (String) -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  Surface(
    modifier = modifier.fillMaxWidth().padding(8.dp),
    shape = MaterialTheme.shapes.large,
    tonalElevation = 6.dp,
    shadowElevation = 6.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.weight(1f).focusRequester(focusRequester),
        placeholder = { Text("Find in page") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
          KeyboardActions(
            onSearch = {
              onNext()
              keyboardController?.hide()
            }
          ),
      )
      Text(
        text = if (matchCount == 0) "0/0" else "${activeMatch + 1}/$matchCount",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 4.dp),
      )
      IconButton(onClick = onPrevious, enabled = matchCount > 0) {
        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
      }
      IconButton(onClick = onNext, enabled = matchCount > 0) {
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
      }
      IconButton(
        onClick = {
          keyboardController?.hide()
          onClose()
        }
      ) {
        Icon(Icons.Default.Close, contentDescription = "Close find in page")
      }
    }
  }
}

@Composable
internal fun BrowserPageSettingsDialog(
  siteLabel: String,
  settings: BrowserSiteSettings,
  onSettingsChange: (BrowserSiteSettings) -> Unit,
  onClearSiteData: () -> Unit,
  onDismiss: () -> Unit,
) {
  var confirmClearData by remember { mutableStateOf(false) }

  if (confirmClearData) {
    AlertDialog(
      onDismissRequest = { confirmClearData = false },
      title = { Text("Clear site data?") },
      text = {
        Text(
          "Cookies, local storage, databases, caches, and permissions saved for $siteLabel will be cleared."
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            confirmClearData = false
            onClearSiteData()
          }
        ) {
          Text("Clear", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = { TextButton(onClick = { confirmClearData = false }) { Text("Cancel") } },
    )
    return
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Page settings") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = siteLabel,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SiteSettingRow(
          label = "JavaScript",
          description = "Allow this site to run interactive scripts",
          checked = settings.javaScriptEnabled,
          onCheckedChange = { onSettingsChange(settings.copy(javaScriptEnabled = it)) },
        )
        SiteSettingRow(
          label = "Pop-ups",
          description = "Allow pages to open windows automatically",
          checked = settings.popupsEnabled,
          onCheckedChange = { onSettingsChange(settings.copy(popupsEnabled = it)) },
        )
        SiteSettingRow(
          label = "Third-party cookies",
          description = "Allow embedded services to store cookies",
          checked = settings.thirdPartyCookiesEnabled,
          onCheckedChange = { onSettingsChange(settings.copy(thirdPartyCookiesEnabled = it)) },
        )
        OutlinedButton(
          onClick = { confirmClearData = true },
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
          Icon(
            Icons.Default.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
          )
          Text(
            "Clear site data",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 8.dp),
          )
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
  )
}

@Composable
private fun SiteSettingRow(
  label: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(label, style = MaterialTheme.typography.bodyLarge)
      Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
