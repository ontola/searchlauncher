package com.searchlauncher.app.ui.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HangVerdictTest {
  private fun verdict() = HangVerdict(thresholdMs = 4_000, requiredSamples = 3)

  @Test
  fun `a long stall that stays in the engine is a hang`() {
    val verdict = verdict()
    assertFalse(verdict.sample(1_000, inEngine = true))
    assertFalse(verdict.sample(2_000, inEngine = true))
    assertFalse(verdict.sample(3_000, inEngine = true))
    assertTrue(verdict.sample(4_000, inEngine = true))
  }

  @Test
  fun `a long stall outside the engine is not blamed on it`() {
    val verdict = verdict()
    for (ms in 500L..10_000L step 500) assertFalse(verdict.sample(ms, inEngine = false))
  }

  @Test
  fun `a stall that only just entered the engine needs more samples`() {
    val verdict = verdict()
    for (ms in 500L..5_000L step 500) verdict.sample(ms, inEngine = false)
    assertFalse(verdict.sample(5_500, inEngine = true))
    assertFalse(verdict.sample(6_000, inEngine = true))
    assertTrue(verdict.sample(6_500, inEngine = true))
  }

  @Test
  fun `the main thread answering resets the count`() {
    val verdict = verdict()
    verdict.sample(3_000, inEngine = true)
    verdict.sample(3_500, inEngine = true)
    verdict.sample(0, inEngine = false)
    assertFalse(verdict.sample(4_000, inEngine = true))
  }

  @Test
  fun `a short engine call is not a hang`() {
    val verdict = verdict()
    assertFalse(verdict.sample(500, inEngine = true))
    assertFalse(verdict.sample(1_000, inEngine = true))
    assertFalse(verdict.sample(1_500, inEngine = true))
    assertFalse(verdict.sample(2_000, inEngine = true))
  }
}
