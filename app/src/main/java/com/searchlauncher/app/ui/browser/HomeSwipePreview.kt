package com.searchlauncher.app.ui.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/** One in-memory home frame shared by tab activities; never persisted to disk. */
internal object HomeSwipePreview {
  var image: ImageBitmap? by mutableStateOf(null)
}
