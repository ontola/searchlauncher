package com.searchlauncher.app.util

import kotlinx.coroutines.CancellationException

/**
 * Rethrows coroutine cancellation. Call this first in a catch-all (`catch (e: Exception)`) inside a
 * coroutine, so a cancelled job stops instead of falling through to the error path, which would
 * otherwise keep doing work, write fallback state, or report the cancellation as an error.
 */
fun Throwable.rethrowIfCancellation() {
  if (this is CancellationException) throw this
}
