package com.searchlauncher.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Shows the software keyboard as soon as this window can take it.
 *
 * The IME is started by the system when a focused, text-capable view exists at the moment the
 * window gains focus. Compose's search field is often not that view yet: it is created a frame or
 * two later, and [InputMethodManager.SHOW_IMPLICIT] then drops the follow-up request because the
 * user just dismissed another app's keyboard. Show requests are explicit so they are not dropped,
 * and the home screen parks the chrome bar at a reserved height so it does not ride the IME
 * animation.
 */
object Ime {

  /**
   * Always-visible, and we pad for the IME ourselves. [setSoftInputMode] replaces the whole field,
   * so both bits have to be set together or the manifest's adjust mode is lost.
   */
  const val SOFT_INPUT_MODE =
    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
      WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING

  /** Below this, a stored IME height is treated as missing rather than as a tiny keyboard. */
  const val MIN_PLAUSIBLE_HEIGHT_PX = 101

  /** Fraction of the window used as a stand-in until a real IME height has been measured. */
  const val DEFAULT_HEIGHT_FRACTION = 0.36f

  /** Hard ceiling: some IME windows report nearly the full screen while only the keys are not. */
  const val MAX_HEIGHT_FRACTION = 0.5f

  fun applyWindowMode(window: Window) {
    window.setSoftInputMode(SOFT_INPUT_MODE)
  }

  /**
   * Height the home screen should reserve for the keyboard, whether or not the IME has reported
   * itself yet. Using the live inset here is what made the search bar hop: the IME animates from 0,
   * and a dummy editor taking focus then giving it back made that animation run twice.
   */
  fun reservedHeightPx(storedPx: Int, containerHeightPx: Int): Int {
    if (containerHeightPx <= 0) return storedPx.coerceAtLeast(0)
    val maxPx = (containerHeightPx * MAX_HEIGHT_FRACTION).toInt()
    if (storedPx in MIN_PLAUSIBLE_HEIGHT_PX..maxPx) return storedPx
    return (containerHeightPx * DEFAULT_HEIGHT_FRACTION)
      .toInt()
      .coerceIn(MIN_PLAUSIBLE_HEIGHT_PX, maxPx.coerceAtLeast(MIN_PLAUSIBLE_HEIGHT_PX))
  }

  fun show(view: View) {
    if (!view.isAttachedToWindow) {
      view.post { if (view.isAttachedToWindow) show(view) }
      return
    }
    val activity = view.activityOrNull()
    if (activity != null && !activity.hasWindowFocus()) return
    val window = view.windowOrNull()
    if (window != null) {
      WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.ime())
    }
    val target = window?.currentFocus ?: view
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    // Flags 0 is an explicit show, not SHOW_IMPLICIT (dropped after another app's keyboard was
    // dismissed) and not SHOW_FORCED (which keeps the IME up in the next activity).
    imm.showSoftInput(target, 0)
  }

  fun hide(view: View) {
    val window = view.windowOrNull()
    if (window != null) {
      WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime())
    }
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
  }

  fun isVisible(view: View): Boolean =
    ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true

  fun onWindowFocused(activity: Activity) {
    val view = activity.window.currentFocus ?: activity.window.decorView
    show(view)
  }

  private fun View.activityOrNull(): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
      if (ctx is Activity) return ctx
      ctx = ctx.baseContext
    }
    return null
  }

  private fun View.windowOrNull(): Window? = activityOrNull()?.window
}
