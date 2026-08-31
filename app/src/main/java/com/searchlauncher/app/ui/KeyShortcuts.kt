package com.searchlauncher.app.ui

import android.view.KeyEvent

/**
 * Hardware-keyboard shortcuts for the search bar and the in-app browser.
 *
 * Ctrl/Cmd chords are matched on [KeyEvent] so they can be intercepted before a WebView eats them.
 */
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
