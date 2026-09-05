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
        keyboardDismissed = false,
        openingTab = false,
        browserShowing = false,
        inPip = false,
      )
    )
  }

  @Test
  fun hidesWhenTheUserDismissesItOrLeavesHome() {
    assertFalse(
      builtInHomeKeyboardVisible(
        useBuiltInKeyboard = true,
        keyboardDismissed = true,
        openingTab = false,
        browserShowing = false,
        inPip = false,
      )
    )
    assertFalse(
      builtInHomeKeyboardVisible(
        useBuiltInKeyboard = true,
        keyboardDismissed = false,
        openingTab = true,
        browserShowing = false,
        inPip = false,
      )
    )
    assertFalse(
      builtInHomeKeyboardVisible(
        useBuiltInKeyboard = true,
        keyboardDismissed = false,
        openingTab = false,
        browserShowing = true,
        inPip = false,
      )
    )
    assertFalse(
      builtInHomeKeyboardVisible(
        useBuiltInKeyboard = true,
        keyboardDismissed = false,
        openingTab = false,
        browserShowing = false,
        inPip = true,
      )
    )
    assertFalse(
      builtInHomeKeyboardVisible(
        useBuiltInKeyboard = false,
        keyboardDismissed = false,
        openingTab = false,
        browserShowing = false,
        inPip = false,
      )
    )
  }
}
