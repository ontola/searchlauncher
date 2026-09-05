package com.searchlauncher.app.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeSearchKeyboardTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun wideKeyboardSplitsAndReturnsToCompactLayoutWhenResized() {
    val width = mutableStateOf(800.dp)
    var text = ""
    compose.setContent {
      MaterialTheme {
        HomeSearchKeyboard(
          { text += it },
          {},
          {},
          Modifier.requiredWidth(width.value).height(243.dp),
          spaceShortcutLabel = "YouTube",
        )
      }
    }
    val spaces = compose.onAllNodesWithContentDescription("Space: activate YouTube search")
    spaces.assertCountEquals(2)
    val t = compose.onNodeWithText("t").fetchSemanticsNode().boundsInRoot
    val y = compose.onNodeWithText("y").fetchSemanticsNode().boundsInRoot
    assertTrue(y.left - t.right > t.width)
    spaces[0].performClick()
    spaces[1].performClick()
    compose.onNodeWithText("q").performClick()
    compose.onNodeWithText("p").performClick()
    compose.runOnIdle { assertEquals("  qp", text) }
    compose.onNodeWithContentDescription("Numbers and symbols").performClick()
    compose.onNodeWithText("1").performClick()
    compose.onNodeWithText("0").performClick()
    compose.runOnIdle { assertEquals("  qp10", text) }
    compose.runOnIdle { width.value = 400.dp }
    spaces.assertCountEquals(1)
  }

  private fun holdKey(key: String) {
    compose.onNodeWithText(key).performTouchInput {
      down(center)
      advanceEventTime(700)
      moveBy(Offset.Zero)
    }
    // Keep the pointer down while Android's long-press timeout expires.
    android.os.SystemClock.sleep(600)
    compose.waitForIdle()
  }

  @Test
  fun extraCharacterCommitsAfter250msHold() {
    var text = ""
    compose.setContent {
      MaterialTheme { HomeSearchKeyboard({ text += it }, {}, {}, Modifier.height(243.dp)) }
    }
    compose.onNodeWithText("q").performTouchInput { longClick(durationMillis = 300) }
    compose.runOnIdle { assertEquals("1", text) }
    compose.onNodeWithContentDescription("Alternative 1").assertDoesNotExist()
  }

  @Test
  fun touchTapTypesLetterAndCancelledHoldTypesNothing() {
    var text = ""
    compose.setContent {
      MaterialTheme { HomeSearchKeyboard({ text += it }, {}, {}, Modifier.height(243.dp)) }
    }
    compose.onNodeWithText("q").performTouchInput { click() }
    compose.runOnIdle { assertEquals("q", text) }
    holdKey("q")
    compose.onNodeWithText("q").performTouchInput { cancel() }
    compose.runOnIdle { assertEquals("q", text) }
    compose.onNodeWithContentDescription("Alternative 1").assertDoesNotExist()
  }

  @Test
  fun holdingHighlightsDefaultAndReleaseCommitsAndDismisses() {
    var text = ""
    compose.setContent {
      MaterialTheme { HomeSearchKeyboard({ text += it }, {}, {}, Modifier.height(243.dp)) }
    }
    holdKey("q")
    compose.onNodeWithContentDescription("Alternative 1").assertIsSelected()
    compose.runOnIdle { assertEquals("", text) }
    compose.onNodeWithText("q").performTouchInput { up() }
    compose.runOnIdle { assertEquals("1", text) }
    compose.onNodeWithContentDescription("Alternative 1").assertDoesNotExist()
  }

  @Test
  fun longPressOffersHintedNumberWithoutTypingTheLetter() {
    var text = ""
    compose.setContent {
      MaterialTheme { HomeSearchKeyboard({ text += it }, {}, {}, Modifier.height(243.dp)) }
    }
    compose.onNodeWithText("q").performTouchInput { longClick() }
    compose.runOnIdle { assertEquals("1", text) }
    compose.onNodeWithContentDescription("Alternative 1").assertDoesNotExist()
  }

  @Test
  fun bracketAlternativesAndOrdinaryTapsStaySeparate() {
    var text = ""
    compose.setContent {
      MaterialTheme { HomeSearchKeyboard({ text += it }, {}, {}, Modifier.height(243.dp)) }
    }
    compose.onNodeWithText("k").performClick()
    holdKey("k")
    val step = with(compose.density) { 36.dp.toPx() }
    compose.onNodeWithText("k").performTouchInput {
      moveBy(Offset(-step, 0f))
      up()
    }
    compose.runOnIdle { assertEquals("k[", text) }
  }

  @Test
  fun spacePreviewStillInsertsASpaceAndReturnsToNormalWhenCleared() {
    var text = ""
    val preview = androidx.compose.runtime.mutableStateOf<String?>("YouTube")
    compose.setContent {
      MaterialTheme {
        HomeSearchKeyboard(
          { text += it },
          {},
          {},
          Modifier.height(243.dp),
          spaceShortcutLabel = preview.value,
          spaceShortcutIcon = androidx.compose.ui.graphics.ImageBitmap(16, 16),
        )
      }
    }
    compose.onNodeWithText("Search YouTube").assertExists()
    compose.onNodeWithContentDescription("Space: activate YouTube search").performClick()
    compose.runOnIdle {
      assertEquals(" ", text)
      preview.value = null
    }
    compose.onNodeWithContentDescription("Space").assertExists()
    compose.onNodeWithText("Search YouTube").assertDoesNotExist()
  }

  @Test
  fun shortcutHintsKeepTypingLettersAndDisappearOnSymbols() {
    var text = ""
    compose.setContent {
      MaterialTheme {
        HomeSearchKeyboard(
          { text += it },
          {},
          {},
          Modifier.height(243.dp),
          shortcutHints =
            mapOf(
              'y' to
                KeyboardShortcutHint("YouTube", androidx.compose.ui.graphics.ImageBitmap(16, 16))
            ),
        )
      }
    }
    compose.onNodeWithText("YouTube").assertDoesNotExist()
    compose.onNodeWithContentDescription("y, YouTube shortcut").performClick()
    compose.runOnIdle { assertEquals("y", text) }
    compose.onNodeWithContentDescription("Numbers and symbols").performClick()
    compose.onNodeWithContentDescription("y, YouTube shortcut").assertDoesNotExist()
  }

  @Test
  fun goUsesSelectedResultIconAndAccessibleName() {
    var opened = false
    val icon = androidx.compose.ui.graphics.ImageBitmap(16, 16)
    compose.setContent {
      MaterialTheme {
        HomeSearchKeyboard(
          {},
          {},
          { opened = true },
          Modifier.height(243.dp),
          goIcon = icon,
          goDescription = "Go: YouTube",
        )
      }
    }
    compose.onNodeWithText("Go").assertDoesNotExist()
    compose.onNodeWithContentDescription("Go: YouTube").performClick()
    compose.runOnIdle { assertTrue(opened) }
  }

  @Test
  fun shiftIsOneShotAndSymbolsCanReturnToLetters() {
    var text = ""
    compose.setContent {
      MaterialTheme { HomeSearchKeyboard({ text += it }, {}, {}, Modifier.height(243.dp)) }
    }
    compose.onNodeWithContentDescription("Shift").performClick()
    compose.onNodeWithText("A").performClick()
    compose.onNodeWithText("b").performClick()
    compose.onNodeWithContentDescription("Numbers and symbols").performClick()
    compose.onNodeWithText("1").performClick()
    compose.onNodeWithContentDescription("Letters").performClick()
    compose.onNodeWithText("c").performClick()
    compose.runOnIdle { assertEquals("Ab1c", text) }
  }

  @Test
  fun accentLongPressInsertsOnlyChosenAccent() {
    var text = ""
    compose.setContent {
      MaterialTheme { HomeSearchKeyboard({ text += it }, {}, {}, Modifier.height(243.dp)) }
    }
    holdKey("e")
    val step = with(compose.density) { 72.dp.toPx() }
    compose.onNodeWithText("e").performTouchInput {
      moveBy(Offset(step, 0f))
      up()
    }
    compose.runOnIdle { assertEquals("é", text) }
  }

  @Test
  fun goAndBackspaceUseTheirOwnActions() {
    var deletes = 0
    var go = false
    compose.setContent {
      MaterialTheme {
        HomeSearchKeyboard({}, { deletes++ }, { go = true }, Modifier.height(243.dp))
      }
    }
    compose.onNodeWithContentDescription("Backspace").performClick()
    compose.onNodeWithContentDescription("Go: open search result").performClick()
    compose.runOnIdle {
      assertEquals(1, deletes)
      assertTrue(go)
    }
  }
}
