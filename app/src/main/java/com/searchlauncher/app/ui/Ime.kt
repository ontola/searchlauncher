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
 * user just dismissed another app's keyboard. Show requests are explicit so they are not dropped.
 *
 * Calling [show] again while the IME is already on its way restarts its appearance animation, so
 * callers should treat a `true` return as "accepted" and wait rather than keep poking. Hiding from
 * [Activity.onPause] is also avoided: that tears the IME process down, and the next home arrival
 * then waits on a cold start.
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
   * IME inset to pad the chrome with. Live values larger than [MAX_HEIGHT_FRACTION] of the screen
   * are the IME window reporting itself as full-screen while the keys are not; using them shoved
   * the search bar off the top. Fall back to the stored height so the bar stays put for that frame.
   */
  fun insetForLayoutPx(imePx: Int, storedPx: Int, containerHeightPx: Int): Int {
    if (containerHeightPx <= 0) return imePx.coerceAtLeast(0)
    val maxPx = (containerHeightPx * MAX_HEIGHT_FRACTION).toInt()
    if (imePx in 0..maxPx) return imePx
    return reservedHeightPx(storedPx, containerHeightPx)
  }

  /**
   * Height to keep for the wallpaper while a tab is opening and the keys are on their way out. The
   * chrome bar follows the live inset so it does not sit in mid-air above an empty keyboard well.
   */
  fun reservedHeightPx(storedPx: Int, containerHeightPx: Int): Int {
    if (containerHeightPx <= 0) return storedPx.coerceAtLeast(0)
    val maxPx = (containerHeightPx * MAX_HEIGHT_FRACTION).toInt()
    if (storedPx in MIN_PLAUSIBLE_HEIGHT_PX..maxPx) return storedPx
    return (containerHeightPx * DEFAULT_HEIGHT_FRACTION)
      .toInt()
      .coerceIn(MIN_PLAUSIBLE_HEIGHT_PX, maxPx.coerceAtLeast(MIN_PLAUSIBLE_HEIGHT_PX))
  }

  /**
   * Asks the IME to appear for [view]'s window. Returns true if the input method accepted the
   * request (the keys may still be animating in). Returns false if this window cannot take the IME
   * yet — not attached, not focused, or no editor to bind.
   */
  fun show(view: View): Boolean {
    if (!view.isAttachedToWindow) {
      view.post { if (view.isAttachedToWindow) show(view) }
      return false
    }
    val activity = view.activityOrNull()
    if (activity != null && !activity.hasWindowFocus()) return false
    val window = view.windowOrNull()
    val target = window?.currentFocus ?: view
    // Only [showSoftInput]. WindowInsetsController.show(ime()) also drives the Compose inset, and
    // calling both (or calling either repeatedly) rewound the IME animation: the bar rode up over
    // an empty IME window, dropped, then rose again when the keys finally drew.
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    // Flags 0 is an explicit show, not SHOW_IMPLICIT (dropped after another app's keyboard was
    // dismissed) and not SHOW_FORCED (which keeps the IME up in the next activity).
    return imm.showSoftInput(target, 0)
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
