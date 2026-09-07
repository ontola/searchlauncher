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
  fun homeSwipesFireOnceWithoutTypingAndTapsStillWork() {
    var text = ""
    val swipes = mutableListOf<KeyboardHomeSwipe>()
    compose.setContent {
      MaterialTheme {
        HomeSearchKeyboard(
          { text += it },
          {},
          {},
          Modifier.requiredWidth(400.dp).height(243.dp),
          gesturesEnabled = false,
          onHomeSwipe = { swipes += it },
        )
      }
    }
    compose.onNodeWithText("g").performTouchInput { click() }
    assertEquals("g", text)
    for ((key, delta) in
      listOf(
        "a" to Offset(0f, -140f),
        "a" to Offset(0f, 140f),
        "l" to Offset(0f, 140f),
        "g" to Offset(-140f, 0f),
        "g" to Offset(140f, 0f),
      )) {
      compose.onNodeWithText(key).performTouchInput {
        down(center)
        moveBy(delta, delayMillis = 80)
        moveBy(delta * 0.1f, delayMillis = 16)
        up()
      }
    }
    assertEquals("g", text)
    assertEquals(
      listOf(
        KeyboardHomeSwipe.Up,
        KeyboardHomeSwipe.DownLeft,
        KeyboardHomeSwipe.DownRight,
        KeyboardHomeSwipe.Left,
        KeyboardHomeSwipe.Right,
      ),
      swipes,
    )
  }

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

  @Test
  fun swipesMoveCursorOrScrollAndReleaseNeverOpens() {
    var text = ""
    var cursor = 0
    var selection = 0f
    var opened = 0
    compose.setContent {
      MaterialTheme {
        HomeSearchKeyboard(
          { text += it },
          {},
          { opened++ },
          Modifier.requiredWidth(400.dp).height(243.dp),
          onMoveCursor = { cursor += it },
          onScrollResults = { selection += it },
        )
      }
    }
    compose.onNodeWithText("g").performTouchInput {
      down(center)
      moveBy(Offset(100f, 0f), delayMillis = 40)
      up()
    }
    compose.runOnIdle {
      assertTrue(cursor > 0)
      assertEquals(0f, selection)
      assertEquals(0, opened)
      assertEquals("", text)
    }
    compose.onNodeWithText("g").performTouchInput {
      down(center)
      moveBy(Offset(0f, -100f), delayMillis = 40)
    }
    compose.runOnIdle {
      assertTrue(selection < 0)
      assertEquals(0, opened)
    }
    var beforeSmallMove = 0f
    compose.runOnIdle { beforeSmallMove = selection }
    compose.onNodeWithText("g").performTouchInput { moveBy(Offset(0f, -2f), delayMillis = 16) }
    compose.runOnIdle { assertEquals(beforeSmallMove - 2f, selection, 0.01f) }
    compose.onNodeWithText("g").performTouchInput { up() }
    compose.runOnIdle {
      assertEquals(0, opened)
      assertEquals("", text)
    }
    compose.onNodeWithContentDescription("Space").performTouchInput {
      down(center)
      moveBy(Offset(0f, -100f), delayMillis = 40)
      cancel()
    }
    compose.runOnIdle { assertEquals(0, opened) }
  }

  @Test
  fun disablingGesturesStopsNavigationButKeepsTypingAndCanBeReenabled() {
    val enabled = mutableStateOf(false)
    var text = ""
    var movement = 0f
    var cursor = 0
    var opens = 0
    compose.setContent {
      MaterialTheme {
        HomeSearchKeyboard(
          { text += it },
          {},
          { opens++ },
          Modifier.requiredWidth(400.dp).height(243.dp),
          gesturesEnabled = enabled.value,
          onMoveCursor = { cursor += it },
          onScrollResults = { movement += it },
        )
      }
    }
    compose.onNodeWithContentDescription("Space").performTouchInput {
      down(center)
      moveBy(Offset(80f, 0f), delayMillis = 40)
      up()
    }
    compose.onNodeWithText("g").performTouchInput {
      down(center)
      moveBy(Offset(0f, -80f), delayMillis = 40)
      up()
    }
    compose.runOnIdle {
      assertEquals(0, cursor)
      assertEquals(0f, movement)
      assertEquals(0, opens)
      assertEquals("", text)
    }
    compose.onNodeWithText("g").performTouchInput { click() }
    compose.runOnIdle {
      assertEquals("g", text)
      enabled.value = true
    }
    compose.onNodeWithText("g").performTouchInput {
      down(center)
      moveBy(Offset(0f, -80f), delayMillis = 40)
      up()
    }
    compose.runOnIdle {
      assertTrue(movement < 0f)
      assertEquals(0, opens)
    }
  }

  @Test
  fun verticalReleaseCoastsAndNewTouchStopsMomentum() {
    var distance = 0f
    var opens = 0
    compose.setContent {
      MaterialTheme {
        HomeSearchKeyboard(
          {},
          {},
          { opens++ },
          Modifier.requiredWidth(400.dp).height(243.dp),
          onScrollResults = { distance += it },
        )
      }
    }
    compose.mainClock.autoAdvance = false
    compose.onNodeWithText("g").performTouchInput {
      down(center)
      moveBy(Offset(0f, 30f), delayMillis = 16)
      moveBy(Offset(0f, 30f), delayMillis = 16)
      moveBy(Offset(0f, 30f), delayMillis = 16)
      up()
    }
    var releasedAt = 0f
    compose.runOnIdle { releasedAt = distance }
    compose.mainClock.advanceTimeBy(120)
    compose.runOnIdle {
      assertTrue(distance > releasedAt)
      assertEquals(0, opens)
    }
    compose.onNodeWithContentDescription("Space").performTouchInput { down(center) }
    var stoppedAt = 0f
    compose.runOnIdle { stoppedAt = distance }
    compose.mainClock.advanceTimeBy(160)
    compose.runOnIdle { assertEquals(stoppedAt, distance, 0.01f) }
    compose.onNodeWithContentDescription("Space").performTouchInput { cancel() }
  }

  @Test
  fun swipeStartingOnGoNeverLaunchesEvenWhenSelectedIconChanges() {
    var opens = 0
    val icon = mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    compose.setContent {
      MaterialTheme {
        HomeSearchKeyboard(
          {},
          {},
          { opens++ },
          Modifier.requiredWidth(400.dp).height(243.dp),
          goIcon = icon.value,
          onScrollResults = { icon.value = androidx.compose.ui.graphics.ImageBitmap(16, 16) },
        )
      }
    }
    val go = compose.onNodeWithContentDescription("Go: open search result")
    go.performTouchInput {
      down(center)
      moveBy(Offset(0f, -40f), delayMillis = 16)
    }
    compose.waitForIdle()
    go.performTouchInput {
      moveBy(Offset(0f, -60f), delayMillis = 16)
      up()
    }
    compose.runOnIdle { assertEquals(0, opens) }
    go.performTouchInput { click() }
    compose.runOnIdle { assertEquals(1, opens) }
  }

  @Test
  fun heldGoDragDoesNotFallThroughToOpen() {
    var opens = 0
    compose.setContent {
      MaterialTheme {
        HomeSearchKeyboard({}, {}, { opens++ }, Modifier.requiredWidth(400.dp).height(243.dp))
      }
    }
    compose.onNodeWithContentDescription("Go: open search result").performTouchInput {
      down(center)
      advanceEventTime(300)
      moveBy(Offset(0f, -30f), delayMillis = 16)
      up()
    }
    compose.runOnIdle { assertEquals(0, opens) }
  }

  @Test
  fun changingSelectionOnlyRecomposesGoTarget() {
    val selection = mutableStateOf(0)
    var screenCompositions = 0
    compose.setContent {
      androidx.compose.runtime.SideEffect { screenCompositions++ }
      MaterialTheme {
        HomeSearchKeyboard(
          {},
          {},
          {},
          Modifier.requiredWidth(400.dp).height(243.dp),
          goTarget = { KeyboardGoTarget(null, "Open result ${selection.value}") },
        )
      }
    }
    var before = 0
    compose.runOnIdle {
      before = screenCompositions
      selection.value = 1
    }
    compose.onNodeWithContentDescription("Open result 1").assertExists()
    compose.runOnIdle { assertEquals(before, screenCompositions) }
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
      MaterialTheme {
        HomeSearchKeyboard({ text += it }, {}, {}, Modifier.requiredWidth(400.dp).height(243.dp))
      }
    }
    compose.onNodeWithText("q").performTouchInput { longClick(durationMillis = 300) }
    compose.runOnIdle { assertEquals("1", text) }
    compose.onNodeWithContentDescription("Alternative 1").assertDoesNotExist()
  }

  @Test
  fun touchTapTypesLetterAndCancelledHoldTypesNothing() {
    var text = ""
    compose.setContent {
      MaterialTheme {
        HomeSearchKeyboard({ text += it }, {}, {}, Modifier.requiredWidth(400.dp).height(243.dp))
      }
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
      MaterialTheme {
        HomeSearchKeyboard({ text += it }, {}, {}, Modifier.requiredWidth(400.dp).height(243.dp))
      }
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
      MaterialTheme {
        HomeSearchKeyboard({ text += it }, {}, {}, Modifier.requiredWidth(400.dp).height(243.dp))
      }
    }
    compose.onNodeWithText("q").performTouchInput { longClick() }
    compose.runOnIdle { assertEquals("1", text) }
    compose.onNodeWithContentDescription("Alternative 1").assertDoesNotExist()
  }

  @Test
  fun bracketAlternativesAndOrdinaryTapsStaySeparate() {
    var text = ""
    compose.setContent {
      MaterialTheme {
        HomeSearchKeyboard({ text += it }, {}, {}, Modifier.requiredWidth(400.dp).height(243.dp))
      }
    }
    compose.onNodeWithText("k").performClick()
    holdKey("k")
    val step = with(compose.density) { 36.dp.toPx() }
    val keyX = compose.onNodeWithText("k").fetchSemanticsNode().boundsInRoot.center.x
    val screenWidth =
      androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        .targetContext
        .resources
        .displayMetrics
        .widthPixels
    val direction = if (keyX > screenWidth / 2f) -1f else 1f
    compose.onNodeWithText("k").performTouchInput {
      moveBy(Offset(direction * step, 0f))
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
          Modifier.requiredWidth(400.dp).height(243.dp),
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
          Modifier.requiredWidth(400.dp).height(243.dp),
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
          Modifier.requiredWidth(400.dp).height(243.dp),
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
      MaterialTheme {
        HomeSearchKeyboard({ text += it }, {}, {}, Modifier.requiredWidth(400.dp).height(243.dp))
      }
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
      MaterialTheme {
        HomeSearchKeyboard({ text += it }, {}, {}, Modifier.requiredWidth(400.dp).height(243.dp))
      }
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
        HomeSearchKeyboard(
          {},
          { deletes++ },
          { go = true },
          Modifier.requiredWidth(400.dp).height(243.dp),
        )
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
