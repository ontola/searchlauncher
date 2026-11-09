package com.searchlauncher.app.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fast, overlapping typing: every press must land, in the order the fingers went down. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeSearchKeyboardTouchTest {
  @get:Rule val compose = createComposeRule()

  private var text = ""
  private var cursorMoves = 0
  private var touchSlop = 0f
  private var dp = 1f

  private fun keyboard(gestures: Boolean = false) {
    compose.setContent {
      dp = LocalDensity.current.density
      // Robolectric's slop is larger than a phone's 8dp; use the real value.
      val platform = LocalViewConfiguration.current
      val phone =
        object : ViewConfiguration by platform {
          override val touchSlop: Float = 8f * dp
        }
      touchSlop = phone.touchSlop
      CompositionLocalProvider(LocalViewConfiguration provides phone) {
        MaterialTheme {
          HomeSearchKeyboard(
            { text += it },
            { text = text.dropLast(1) },
            {},
            Modifier.requiredWidth(400.dp).height(243.dp),
            gesturesEnabled = gestures,
            onMoveCursor = { cursorMoves += it },
          )
        }
      }
    }
  }

  private fun center(key: String): Offset =
    compose.onNodeWithContentDescription(key).fetchSemanticsNode().boundsInRoot.center

  @Test
  fun overlappingTapsOnDifferentKeysAllType() {
    keyboard()
    val a = center("a")
    val s = center("s")
    val d = center("d")
    compose.onRoot().performTouchInput {
      down(0, a)
      advanceEventTime(40)
      down(1, s)
      advanceEventTime(40)
      up(0)
      advanceEventTime(40)
      down(0, d)
      advanceEventTime(40)
      up(1)
      advanceEventTime(40)
      up(0)
    }
    assertEquals("asd", text)
  }

  @Test
  fun laterFingerLiftingFirstKeepsTouchOrder() {
    keyboard()
    val a = center("a")
    val s = center("s")
    compose.onRoot().performTouchInput {
      down(0, a)
      advanceEventTime(40)
      down(1, s)
      advanceEventTime(40)
      up(1)
      advanceEventTime(40)
      up(0)
    }
    assertEquals("as", text)
  }

  @Test
  fun overlappingTapsOnTheSameKeyTypeTwice() {
    keyboard()
    val e = center("e")
    compose.onRoot().performTouchInput {
      down(0, e)
      advanceEventTime(40)
      down(1, e + Offset(4f, 2f))
      advanceEventTime(40)
      up(0)
      advanceEventTime(40)
      up(1)
    }
    assertEquals("ee", text)
  }

  @Test
  fun spaceRollingOverALetterKeepsOrder() {
    keyboard()
    val o = center("o")
    val space = center("Space")
    compose.onRoot().performTouchInput {
      down(0, o)
      advanceEventTime(40)
      down(1, space)
      advanceEventTime(40)
      up(1)
      advanceEventTime(40)
      up(0)
    }
    assertEquals("o ", text)
  }

  @Test
  fun heldKeyTypedByTheNextFingerOpensNoAccentPicker() {
    keyboard()
    val e = center("e")
    val r = center("r")
    compose.onRoot().performTouchInput {
      down(0, e)
      advanceEventTime(60)
      down(1, r)
      advanceEventTime(40)
      up(1)
      advanceEventTime(400)
      up(0)
    }
    assertEquals("er", text)
  }

  @Test
  fun tapBetweenTwoKeysTypesTheNearestOne() {
    keyboard()
    val g = center("g")
    val h = center("h")
    val gap = Offset((g.x + h.x) / 2f - 1f, g.y)
    compose.onRoot().performTouchInput { click(gap) }
    // Just below the row is the gap above the next one.
    val belowG = Offset(g.x, (g.y + center("b").y) / 2f - 1f)
    compose.onRoot().performTouchInput { click(belowG) }
    assertEquals("gg", text)
  }

  @Test
  fun indentBesideTheMiddleRowTypesItsOuterKey() {
    keyboard()
    // The half-key indent left of "a" used to be dead space.
    val s = compose.onNodeWithContentDescription("s").fetchSemanticsNode().boundsInRoot
    compose.onRoot().performTouchInput { click(Offset(s.left - 1.4f * s.width, s.center.y)) }
    assertEquals("a", text)
  }

  @Test
  fun tapThatDriftsOffItsKeyStillTypes() {
    keyboard()
    // Starts near the right edge of g and lifts beyond it.
    val g = compose.onNodeWithContentDescription("g").fetchSemanticsNode().boundsInRoot
    compose.onRoot().performTouchInput {
      down(Offset(g.right - 4f, g.center.y))
      moveBy(Offset(12f, 4f), delayMillis = 30)
      up()
    }
    assertEquals("g", text)
    // A long drag off the key is not a tap, even with keyboard gestures off.
    compose.onRoot().performTouchInput {
      down(Offset(g.center.x, g.center.y))
      moveBy(Offset(0f, -80f), delayMillis = 40)
      up()
    }
    assertEquals("g", text)
  }

  @Test
  fun smallDriftIsATapNotACursorSwipe() {
    keyboard(gestures = true)
    val g = center("g")
    // More than the platform slop, which used to start a cursor swipe and drop the letter.
    val drift = touchSlop + 4f
    assertEquals(true, drift < 16 * dp)
    compose.onRoot().performTouchInput {
      down(g)
      moveBy(Offset(drift, 0f), delayMillis = 30)
      up()
    }
    assertEquals("g", text)
    assertEquals(0, cursorMoves)
    // A deliberate drag still moves the cursor and types nothing.
    compose.onRoot().performTouchInput {
      down(g)
      moveBy(Offset(60f, 0f), delayMillis = 60)
      up()
    }
    assertEquals("g", text)
    assertEquals(true, cursorMoves > 0)
  }

  @Test
  fun backspaceStillRepeatsWhileHeld() {
    keyboard()
    text = "abcdefghij"
    val backspace = center("Backspace")
    compose.onRoot().performTouchInput {
      down(backspace)
      advanceEventTime(400 + 65 * 3 + 30)
      up()
    }
    assertEquals(true, text.length <= 7)
  }
}
