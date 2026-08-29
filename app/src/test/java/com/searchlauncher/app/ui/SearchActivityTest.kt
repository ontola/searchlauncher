package com.searchlauncher.app.ui

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchActivityTest {

  @Test
  fun backKeyClosesTheOverlayOnUp() {
    var closed = false
    val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
    val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)

    assertTrue(closeOverlayOnBackKey(down) { closed = true })
    assertFalse(closed)
    assertTrue(closeOverlayOnBackKey(up) { closed = true })
    assertTrue(closed)
  }

  @Test
  fun otherKeysAreLeftToTheWindow() {
    var closed = false
    val escape = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE)

    assertFalse(closeOverlayOnBackKey(escape) { closed = true })
    assertFalse(closed)
  }
}
