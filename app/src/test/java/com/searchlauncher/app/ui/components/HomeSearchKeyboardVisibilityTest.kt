package com.searchlauncher.app.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSearchKeyboardVisibilityTest {
  @Test
  fun staysDrawnForHomeAndSidewaysTabHandoffs() {
    assertTrue(builtInHomeKeyboardVisible(true, openingOverviewTab = false, inPip = false))
  }

  @Test
  fun hidesForOverviewExpansionSystemKeyboardAndPip() {
    assertFalse(builtInHomeKeyboardVisible(true, openingOverviewTab = true, inPip = false))
    assertFalse(builtInHomeKeyboardVisible(false, openingOverviewTab = false, inPip = false))
    assertFalse(builtInHomeKeyboardVisible(true, openingOverviewTab = false, inPip = true))
  }

  @Test
  fun numberRowGrowsKeyboardWithoutShrinkingLetters() {
    assertEquals(243.dp, builtInHomeKeyboardHeight(800, numberRow = false))
    assertEquals(292.dp, builtInHomeKeyboardHeight(800, numberRow = true))
    // Short screens scale both layouts by the same factor.
    assertEquals(180.dp, builtInHomeKeyboardHeight(400, numberRow = false))
    assertEquals(180f * 292f / 243f, builtInHomeKeyboardHeight(400, numberRow = true).value, 0.01f)
  }
}
