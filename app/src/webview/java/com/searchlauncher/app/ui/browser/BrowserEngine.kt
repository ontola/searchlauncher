package com.searchlauncher.app.ui.browser

import androidx.compose.runtime.Composable

internal object BrowserEngine {
  const val isGecko = false

  @Composable
  fun Content(
    navigationRequest: NavigationRequest?,
    privateMode: Boolean,
    showLauncherChrome: Boolean,
    browserMenuRequest: Long,
    onBrowserMenuShown: () -> Unit,
    tabActivationRequest: TabActivationRequest?,
    pinnedTabId: Long?,
    onOpenSearch: (Boolean, Int, String) -> Unit,
    onClose: () -> Unit,
    inPictureInPicture: Boolean,
  ) =
    BrowserScreen(
      navigationRequest,
      privateMode,
      showLauncherChrome,
      browserMenuRequest,
      onBrowserMenuShown,
      tabActivationRequest,
      pinnedTabId,
      onOpenSearch,
      onClose,
      inPictureInPicture,
    )
}
