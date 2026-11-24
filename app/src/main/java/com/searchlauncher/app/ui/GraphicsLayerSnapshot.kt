package com.searchlauncher.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import kotlin.coroutines.cancellation.CancellationException

/**
 * [GraphicsLayer.toImageBitmap], or null when the layer has nothing to copy.
 *
 * A layer's size is whatever it was last recorded at, and that can drop to zero between a caller's
 * size check and the copy: the composable leaves, the layer is released, or a relayout records it
 * empty. The copy then allocates a 0-pixel bitmap, which throws on the main thread and takes the
 * whole launcher down with it (SEARCHLAUNCHER-C). A snapshot is only ever a nicety, so a missing
 * one is the right answer.
 */
suspend fun GraphicsLayer.toImageBitmapOrNull(): ImageBitmap? {
  if (size.width <= 0 || size.height <= 0) return null
  return try {
    toImageBitmap()
  } catch (e: CancellationException) {
    throw e
  } catch (e: IllegalArgumentException) {
    null
  } catch (e: IllegalStateException) {
    // Released layers throw this.
    null
  }
}
