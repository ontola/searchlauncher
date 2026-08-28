package com.searchlauncher.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.WeakHashMap

/**
 * Shows the software keyboard as soon as this window can take it.
 *
 * The IME is started by the system when a focused, text-capable view exists at the moment the
 * window gains focus. Compose's search field is often not that view yet: it is created a frame or
 * two later, and [InputMethodManager.SHOW_IMPLICIT] then drops the follow-up request because the
 * user just dismissed another app's keyboard. A zero-size [EditText] is parked on the window until
 * the real field takes over, and show requests are explicit so they are not dropped.
 */
object Ime {

  /**
   * Always-visible, and we pad for the IME ourselves. [setSoftInputMode] replaces the whole field,
   * so both bits have to be set together or the manifest's adjust mode is lost.
   */
  const val SOFT_INPUT_MODE =
    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
      WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING

  private val warmups = WeakHashMap<Activity, EditText>()

  fun applyWindowMode(window: Window) {
    window.setSoftInputMode(SOFT_INPUT_MODE)
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

  /**
   * Parks a real editor on [activity] so the window already has something the IME can bind to
   * before Compose has focused the search field. Released once that field takes focus.
   */
  fun installWarmup(activity: Activity) {
    if (warmups.containsKey(activity)) return
    val editor =
      EditText(activity).apply {
        layoutParams = ViewGroup.LayoutParams(0, 0)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = true
        isFocusableInTouchMode = true
        showSoftInputOnFocus = true
        isCursorVisible = false
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        imeOptions =
          EditorInfo.IME_ACTION_GO or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI
        inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
      }
    (activity.window.decorView as ViewGroup).addView(editor)
    editor.requestFocus()
    editor.post { if (warmups[activity] === editor) editor.requestFocus() }
    warmups[activity] = editor
    if (activity.hasWindowFocus()) show(editor)
  }

  /**
   * Called when the window itself has just taken focus. Re-asserts the warmup editor if Compose has
   * not claimed focus yet, then asks the IME up on this same callback rather than on the next
   * Compose frame.
   */
  fun onWindowFocused(activity: Activity) {
    val warmup = warmups[activity]
    if (warmup != null) {
      warmup.requestFocus()
      show(warmup)
    } else {
      val view = activity.window.currentFocus ?: activity.window.decorView
      show(view)
    }
  }

  fun releaseWarmup(activity: Activity) {
    val editor = warmups.remove(activity) ?: return
    editor.isFocusable = false
    editor.isFocusableInTouchMode = false
    (editor.parent as? ViewGroup)?.removeView(editor)
  }

  internal fun warmupFor(activity: Activity): EditText? = warmups[activity]

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
