package com.searchlauncher.app.ui

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyShortcutsTest {

  @Test
  fun matchesCtrlChordAndIgnoresOtherModifiers() {
    val ctrlT = key(KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON)
    assertTrue(KeyShortcuts.matches(ctrlT, KeyEvent.KEYCODE_T, ctrl = true))
    assertFalse(KeyShortcuts.matches(ctrlT, KeyEvent.KEYCODE_W, ctrl = true))
    assertFalse(KeyShortcuts.matches(ctrlT, KeyEvent.KEYCODE_T, ctrl = false))

    val ctrlShiftTab = key(KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
    assertTrue(KeyShortcuts.matches(ctrlShiftTab, KeyEvent.KEYCODE_TAB, ctrl = true, shift = true))
    assertFalse(
      KeyShortcuts.matches(ctrlShiftTab, KeyEvent.KEYCODE_TAB, ctrl = true, shift = false)
    )
  }

  @Test
  fun matchesBareEscapeAndDpad() {
    assertTrue(KeyShortcuts.matches(key(KeyEvent.KEYCODE_ESCAPE), KeyEvent.KEYCODE_ESCAPE))
    assertTrue(KeyShortcuts.matches(key(KeyEvent.KEYCODE_DPAD_UP), KeyEvent.KEYCODE_DPAD_UP))
    assertFalse(
      KeyShortcuts.matches(
        key(KeyEvent.KEYCODE_ESCAPE, action = KeyEvent.ACTION_UP),
        KeyEvent.KEYCODE_ESCAPE,
      )
    )
  }

  private fun key(keyCode: Int, metaState: Int = 0, action: Int = KeyEvent.ACTION_DOWN): KeyEvent =
    KeyEvent(0L, 0L, action, keyCode, 0, metaState)
}
