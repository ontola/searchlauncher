package com.searchlauncher.app.ui

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.ui.browser.BrowserActivity

class SearchActivity : ComponentActivity(), KeyShortcutHost {
  override var keyShortcutHandler: ((KeyEvent) -> Boolean)? = null

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (keyShortcutHandler?.invoke(event) == true) return true
    // Consume BACK before the IME does. Otherwise the first press only hides the keyboard and the
    // overlay stays up until a second press finishes this activity.
    if (closeOverlayOnBackKey(event) { finish() }) return true
    return super.dispatchKeyEvent(event)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    closeOverlayOnPredictiveBack()
    enableEdgeToEdge()

    // Make window transparent
    window.setBackgroundDrawableResource(android.R.color.transparent)
    window.setFlags(
      android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    )

    Ime.applyWindowMode(window)

    animateBackdropBlur()

    setContent {
      val context = LocalContext.current
      val query = remember { mutableStateOf(intent.getStringExtra(EXTRA_INITIAL_QUERY).orEmpty()) }
      val browserSearchMode = intent.getBooleanExtra(EXTRA_BROWSER_SEARCH, false)
      val chromeBarColor =
        intent
          .getIntExtra(EXTRA_CHROME_COLOR, 0)
          .takeIf { it != 0 }
          ?.let { androidx.compose.ui.graphics.Color(it) }

      SearchScreen(
        query = query.value,
        onQueryChange = { query.value = it },
        onDismiss = { finish() },
        onOpenSettings = {
          val intent =
            Intent(this, MainActivity::class.java).apply {
              putExtra("open_settings", true)
              flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
          startActivity(intent)
          finish()
        },
        onOpenAppDrawer = {},
        searchRepository = (application as SearchLauncherApp).searchRepository,
        focusTrigger = 0L,
        showBackgroundImage = false,
        privateWebResults = intent.getBooleanExtra(EXTRA_PRIVATE_WEB_RESULTS, false),
        startVoiceSearchOnOpen = intent.getBooleanExtra(EXTRA_START_VOICE_SEARCH, false),
        fixedHint = if (browserSearchMode) "Search anything…" else null,
        onOpenBrowserContext =
          if (browserSearchMode) {
            {
              sendBroadcast(
                Intent(BrowserActivity.ACTION_SHOW_BROWSER_MENU).setPackage(packageName)
              )
              finish()
            }
          } else null,
        // The overlay opened from a tab's window belongs to that tab: a page picked here loads
        // there, in the window the user was just looking at, rather than in a window of its own.
        browserTabId =
          intent.getLongExtra(EXTRA_BROWSER_TAB_ID, 0L).takeIf { browserSearchMode && it != 0L },
        chromeBarColor = chromeBarColor,
        // This overlay always opens together with the keyboard, so the bar rides up with it
        // rather than appearing already parked above where the keys will land.
        riseWithKeyboard = true,
      )
    }
    interceptBackBeforeIme()
  }

  /**
   * BACK is delivered to the IME before [dispatchKeyEvent], so intercepting there is too late: the
   * first press only hides the keyboard. Walking the focused field's parents with
   * [ViewGroup.dispatchKeyEventPreIme] is the path that still sees that press.
   */
  private fun interceptBackBeforeIme() {
    val content = findViewById<ViewGroup>(android.R.id.content)
    val child = content.getChildAt(0) ?: return
    content.removeView(child)
    val wrapper =
      object : FrameLayout(this) {
        override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
          if (keyShortcutHandler?.invoke(event) == true) return true
          if (closeOverlayOnBackKey(event) { finish() }) return true
          return super.dispatchKeyEventPreIme(event)
        }
      }
    wrapper.addView(
      child,
      FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      ),
    )
    content.addView(wrapper)
  }

  /**
   * Gesture back on API 33+ never becomes a [KeyEvent]. The IME registers at default priority to
   * hide itself, so this overlay has to sit above that or the first swipe only drops the keyboard.
   */
  private fun closeOverlayOnPredictiveBack() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    onBackInvokedDispatcher.registerOnBackInvokedCallback(
      OnBackInvokedDispatcher.PRIORITY_OVERLAY
    ) {
      finish()
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) Ime.onWindowFocused(this)
  }

  /**
   * Pushes whatever is behind this translucent window — the web page, the launcher — out of focus,
   * ramping the radius up so the backdrop settles along with the rest of the overlay instead of
   * snapping out of focus the instant the search opens.
   *
   * Cross-window blur arrived in Android 12 and the system can switch it off at any time (battery
   * saver, low-end devices, developer setting). Where it is unavailable the overlay simply keeps
   * the dim it already had.
   */
  private fun animateBackdropBlur() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    if (!windowManager.isCrossWindowBlurEnabled) return

    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    val targetRadiusPx = (BACKDROP_BLUR_RADIUS_DP * resources.displayMetrics.density).toInt()
    ValueAnimator.ofInt(0, targetRadiusPx).apply {
      duration = BACKDROP_BLUR_DURATION_MS
      interpolator = DecelerateInterpolator()
      addUpdateListener { animator ->
        // Reassigning is what commits the change; mutating the params in place does nothing.
        window.attributes =
          window.attributes.apply { blurBehindRadius = animator.animatedValue as Int }
      }
      start()
    }
  }

  companion object {
    /** Enough to make text behind the overlay unreadable without turning it into a smear. */
    private const val BACKDROP_BLUR_RADIUS_DP = 16f
    /** Roughly the keyboard's own entrance, so the two land together. */
    private const val BACKDROP_BLUR_DURATION_MS = 250L

    const val EXTRA_PRIVATE_WEB_RESULTS = "private_web_results"
    const val EXTRA_START_VOICE_SEARCH = "start_voice_search"
    const val EXTRA_BROWSER_SEARCH = "browser_search"
    const val EXTRA_BROWSER_TAB_ID = "browser_tab_id"
    const val EXTRA_CHROME_COLOR = "chrome_color"
    const val EXTRA_INITIAL_QUERY = "initial_query"
  }
}

/**
 * Whether [event] is a back press that should close the search overlay rather than being handed to
 * the IME. Down is consumed so the keyboard never sees it; the overlay closes on up.
 */
internal fun closeOverlayOnBackKey(event: KeyEvent, close: () -> Unit): Boolean {
  if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
  if (event.action == KeyEvent.ACTION_UP) close()
  return true
}
