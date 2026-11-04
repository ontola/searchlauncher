package com.searchlauncher.app.util

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellationTest {
  @Test
  fun rethrowIfCancellation_ignoresOrdinaryErrors() {
    IOException("disk full").rethrowIfCancellation()
  }

  @Test(expected = CancellationException::class)
  fun rethrowIfCancellation_rethrowsCancellation() {
    CancellationException("cancelled").rethrowIfCancellation()
  }

  @Test
  fun catchAll_letsCancelledCoroutineStop() = runBlocking {
    var fellThrough = false
    val job =
      async(start = CoroutineStart.UNDISPATCHED) {
        try {
          awaitCancellation()
        } catch (e: Exception) {
          e.rethrowIfCancellation()
          fellThrough = true
        }
        "finished"
      }
    job.cancel()
    val result = runCatching { job.await() }
    assertTrue(result.exceptionOrNull() is CancellationException)
    assertEquals(false, fellThrough)
  }
}
