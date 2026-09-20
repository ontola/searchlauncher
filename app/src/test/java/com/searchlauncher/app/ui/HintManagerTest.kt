package com.searchlauncher.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HintManagerTest {
  @Test
  fun splitShadeMentionsLeftAndRightSwipes() {
    val hints = hints(hasSeparateShade = true)
    assertTrue(hints.contains("Swipe down left to open notifications"))
    assertTrue(hints.contains("Swipe down right to open quick settings"))
    assertFalse(hints.contains("Swipe down to open notifications"))
  }

  @Test
  fun combinedShadeMentionsASingleSwipeDown() {
    val hints = hints(hasSeparateShade = false)
    assertTrue(hints.contains("Swipe down to open notifications"))
    assertFalse(hints.contains("Swipe down left to open notifications"))
    assertFalse(hints.contains("Swipe down right to open quick settings"))
  }

  private fun hints(hasSeparateShade: Boolean): List<String> =
    HintManager(
        isWallpaperFolderSet = { true },
        isSnippetsSet = { true },
        isDefaultLauncher = { true },
        isContactsAccessGranted = { true },
        hasSeparateShade = { hasSeparateShade },
      )
      .visibleHintTexts()
}
