package com.searchlauncher.app.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImeTest {

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
  fun warmup_isAFocusedZeroSizeEditorUntilReleased() {
    val activity = activity()
    Ime.installWarmup(activity)
    val warmup = Ime.warmupFor(activity)
    assertNotNull(warmup)
    val editor = warmup!!
    assertTrue(editor.isFocused)
    assertTrue(editor.isFocusableInTouchMode)
    assertEquals(0, editor.layoutParams.width)
    assertEquals(0, editor.layoutParams.height)
    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, editor.importantForAccessibility)
    assertTrue(editor.inputType and EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
    assertTrue(editor.parent is ViewGroup)

    Ime.releaseWarmup(activity)
    assertNull(Ime.warmupFor(activity))
    assertNull(editor.parent)
    assertFalse(editor.isFocusable)
  }

  @Test
  fun installWarmup_isIdempotent() {
    val activity = activity()
    Ime.installWarmup(activity)
    val first = Ime.warmupFor(activity)
    Ime.installWarmup(activity)
    assertSame(first, Ime.warmupFor(activity))
    Ime.releaseWarmup(activity)
  }

  @Test
  fun onWindowFocused_refocusesWarmupThenShowDoesNotThrow() {
    val activity = activity()
    Ime.installWarmup(activity)
    val warmup = Ime.warmupFor(activity)!!
    warmup.clearFocus()
    Ime.onWindowFocused(activity)
    assertTrue(warmup.isFocused)
    Ime.show(activity.window.decorView)
    Ime.hide(activity.window.decorView)
    Ime.releaseWarmup(activity)
  }

  @Test
  fun releaseWarmup_withoutInstall_isANoOp() {
    Ime.releaseWarmup(activity())
  }

  private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
}
