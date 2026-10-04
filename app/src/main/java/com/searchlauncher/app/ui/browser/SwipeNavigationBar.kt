package com.searchlauncher.app.ui.browser

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Read progress in this small scope, without recomposing the page or keyboard each frame. */
@Composable
internal fun SwipeNavigationBar(
  modifier: Modifier = Modifier,
  manageWindow: Boolean = true,
  color: () -> Color,
) {
  val background = color()
  val view = LocalView.current
  if (manageWindow && !view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      // Older Android paints this window color; edge-to-edge Android paints the Box below.
      window.navigationBarColor = background.toArgb()
      window.isNavigationBarContrastEnforced = false
      WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
        browserChromeUsesDarkIcons(background)
    }
  }
  Box(
    modifier
      .fillMaxWidth()
      .windowInsetsBottomHeight(WindowInsets.navigationBars)
      .background(background)
  )
}
