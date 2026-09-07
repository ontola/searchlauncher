package com.searchlauncher.app.ui.components

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
}
