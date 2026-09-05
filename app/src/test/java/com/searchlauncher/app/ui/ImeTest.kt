package com.searchlauncher.app.ui

import android.app.Activity
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImeTest {

  @Test
  fun homeKeyboardModeCanSwitchBackToSystemWithoutRecreatingActivity() {
    val window = activity().window
    Ime.applyHomeWindowMode(window, true)
    assertEquals(
      WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN,
      window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE,
    )
    assertEquals(
      WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
      window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST,
    )
    Ime.applyHomeWindowMode(window, false)
    assertEquals(Ime.SOFT_INPUT_MODE, window.attributes.softInputMode)
  }

  @Test
  fun softInputMode_isAlwaysVisibleWithoutResizing() {
    val state = Ime.SOFT_INPUT_MODE and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE
    val adjust = Ime.SOFT_INPUT_MODE and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
    assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE, state)
    assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING, adjust)
  }

  @Test
  fun applyWindowMode_setsBothBitsTogether() {
    val activity = activity()
    Ime.applyWindowMode(activity.window)
    val mode = activity.window.attributes.softInputMode
    assertEquals(
      WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
      mode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE,
    )
    assertEquals(
      WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
      mode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST,
    )
  }

  @Test
  fun reservedHeight_usesStoredWhenPlausible() {
    assertEquals(800, Ime.reservedHeightPx(storedPx = 800, containerHeightPx = 2400))
    assertEquals(901, Ime.reservedHeightPx(storedPx = 901, containerHeightPx = 2400))
  }

  @Test
  fun reservedHeight_guessesWhenStoredIsMissingOrHuge() {
    val guessed = (2400 * Ime.DEFAULT_HEIGHT_FRACTION).toInt()
    assertEquals(guessed, Ime.reservedHeightPx(storedPx = 0, containerHeightPx = 2400))
    assertEquals(guessed, Ime.reservedHeightPx(storedPx = 50, containerHeightPx = 2400))
    assertEquals(guessed, Ime.reservedHeightPx(storedPx = 2000, containerHeightPx = 2400))
  }

  @Test
  fun reservedHeight_keepsStoredWhileContainerSizeIsUnknown() {
    assertEquals(800, Ime.reservedHeightPx(storedPx = 800, containerHeightPx = 0))
    assertEquals(0, Ime.reservedHeightPx(storedPx = 0, containerHeightPx = 0))
  }

  @Test
  fun insetForLayout_usesLiveWhenPlausible() {
    assertEquals(800, Ime.insetForLayoutPx(imePx = 800, storedPx = 900, containerHeightPx = 2400))
    assertEquals(0, Ime.insetForLayoutPx(imePx = 0, storedPx = 900, containerHeightPx = 2400))
  }

  @Test
  fun insetForLayout_ignoresFullScreenImeWindow() {
    val reserved = Ime.reservedHeightPx(storedPx = 900, containerHeightPx = 2400)
    assertEquals(
      reserved,
      Ime.insetForLayoutPx(imePx = 2300, storedPx = 900, containerHeightPx = 2400),
    )
  }

  @Test
  fun showAndHide_doNotThrow() {
    val activity = activity()
    Ime.onWindowFocused(activity)
    Ime.show(activity.window.decorView)
    Ime.hide(activity.window.decorView)
  }

  @Test
  fun show_returnsFalseWhenTheWindowIsNotFocused() {
    val activity = Robolectric.buildActivity(Activity::class.java).create().get()
    assertFalse(Ime.show(activity.window.decorView))
  }

  private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
}
