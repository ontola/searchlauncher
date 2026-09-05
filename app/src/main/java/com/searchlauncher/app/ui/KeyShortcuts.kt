package com.searchlauncher.app.ui

import android.app.Activity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Hardware-keyboard shortcuts for the search bar and the in-app browser.
 *
 * Ctrl/Cmd chords are matched on [KeyEvent] so they can be intercepted before a WebView eats them.
 */
private const val KEY_SHORTCUT_PRE_IME_TAG = "key_shortcut_pre_ime"

/**
 * Runs [KeyShortcutHost.keyShortcutHandler] on [ViewGroup.dispatchKeyEventPreIme] so Tab and Enter
 * are seen before an IME (or Compose's InputConnection) turns them into text.
 */
fun Activity.installKeyShortcutPreIme() {
  val content = findViewById<ViewGroup>(android.R.id.content)
  val child = content.getChildAt(0) ?: return
  if (child.tag == KEY_SHORTCUT_PRE_IME_TAG) return
  content.removeView(child)
  val wrapper =
    object : FrameLayout(this) {
      override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if ((context as? KeyShortcutHost)?.keyShortcutHandler?.invoke(event) == true) return true
        return super.dispatchKeyEventPreIme(event)
      }
    }
  wrapper.tag = KEY_SHORTCUT_PRE_IME_TAG
  wrapper.addView(
    child,
    FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT,
    ),
  )
  content.addView(wrapper)
}

object KeyShortcuts {
  fun isCtrl(event: KeyEvent): Boolean = event.isCtrlPressed || event.isMetaPressed

  fun matches(
    event: KeyEvent,
    keyCode: Int,
    ctrl: Boolean = false,
    shift: Boolean = false,
    alt: Boolean = false,
  ): Boolean {
    if (event.action != KeyEvent.ACTION_DOWN) return false
    if (event.keyCode != keyCode) return false
    if (ctrl != isCtrl(event)) return false
    if (shift != event.isShiftPressed) return false
    return alt == event.isAltPressed
  }
}

interface KeyShortcutHost {
  var keyShortcutHandler: ((KeyEvent) -> Boolean)?
}

/** Lets the hosted or standalone browser ask the activity to enter PiP for fullscreen video. */
interface PipCapable {
  var pipVideoView: android.view.View?
  var inPictureInPicture: Boolean

  fun enterPipIfEligible(): Boolean
}
