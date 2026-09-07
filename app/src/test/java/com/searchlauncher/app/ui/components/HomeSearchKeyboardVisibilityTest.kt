package com.searchlauncher.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSearchKeyboardVisibilityTest {

  @Test
  fun staysUpWhenTheSearchScreenIsCoveredBySettings() {
    assertTrue(
      builtInHomeKeyboardVisible(
        useBuiltInKeyboard = true,
        openingTab = false,
        browserShowing = false,
        inPip = false,
      )
    )
  }

  @Test
  fun hidesWhenLeavingHome() {
    assertFalse(
      builtInHomeKeyboardVisible(
        useBuiltInKeyboard = true,
        openingTab = true,
        browserShowing = false,
        inPip = false,
      )
    )
    assertFalse(
      builtInHomeKeyboardVisible(
        useBuiltInKeyboard = true,
        openingTab = false,
        browserShowing = true,
        inPip = false,
      )
    )
    assertFalse(
      builtInHomeKeyboardVisible(
        useBuiltInKeyboard = true,
        openingTab = false,
        browserShowing = false,
        inPip = true,
      )
    )
    assertFalse(
      builtInHomeKeyboardVisible(
        useBuiltInKeyboard = false,
        openingTab = false,
        browserShowing = false,
        inPip = false,
      )
    )
  }
}
