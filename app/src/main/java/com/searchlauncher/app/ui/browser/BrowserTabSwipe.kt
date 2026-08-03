package com.searchlauncher.app.ui.browser

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * State of the launcher home screen's "swipe the browser back in" gesture: the same horizontal drag
 * that moves between tabs inside the browser, but starting from the home chrome bar and landing on
 * the newest tab.
 */
@Stable
internal class BrowserTabSwipeState {
  var offsetPx by mutableFloatStateOf(0f)
    internal set

  var viewportWidthPx by mutableIntStateOf(1)
    internal set

  var viewportHeightPx by mutableIntStateOf(1)
    internal set

  /** Captured when the drag starts so the preview cannot change halfway through the gesture. */
  var tab by mutableStateOf<BrowserTab?>(null)
    internal set

  fun reset() {
    offsetPx = 0f
    tab = null
  }
}

@Composable
internal fun rememberBrowserTabSwipeState(): BrowserTabSwipeState = remember {
  BrowserTabSwipeState()
}

/**
 * The launcher end of the browser's own chrome-bar gestures: drag sideways to pull the newest tab
 * in from the screen edge ([onOpenLastTab]), or up to raise the tabs overview
 * ([onOpenTabsOverview]) — the same two gestures the bar answers to inside the browser.
 *
 * Both are claimed during [PointerEventPass.Initial] because the chrome bar's own children (the
 * search field, the icon buttons) would otherwise swallow the movement. Downward drags are left
 * alone for whatever else is on screen.
 */
@Composable
internal fun Modifier.browserTabSwipe(
  state: BrowserTabSwipeState,
  enabled: Boolean,
  onOpenTabsOverview: () -> Unit,
  onOpenLastTab: () -> Unit,
): Modifier {
  val scope = rememberCoroutineScope()
  var settleJob by remember { mutableStateOf<Job?>(null) }

  return this.pointerInput(enabled, onOpenTabsOverview, onOpenLastTab) {
    if (!enabled) return@pointerInput
    val touchSlop = viewConfiguration.touchSlop
    val overviewThreshold = 24.dp.toPx()
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      var totalX = 0f
      var totalY = 0f
      var gesture = Gesture.UNDECIDED

      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        val delta = change.positionChange()
        totalX += delta.x
        totalY += delta.y
        if (gesture == Gesture.UNDECIDED) {
          when {
            abs(totalX) > touchSlop && abs(totalX) >= abs(totalY) -> {
              gesture = Gesture.SIDEWAYS
              settleJob?.cancel()
              state.tab = BrowserTabStore.lastTab()
            }
            -totalY > touchSlop -> gesture = Gesture.UPWARD
            // Downward: someone else's gesture. Bow out for the rest of this touch.
            totalY > touchSlop -> break
            else -> continue
          }
        }
        change.consume()
        if (gesture == Gesture.SIDEWAYS) {
          // The browser lives one screen to the left, so only a rightward pull has somewhere to
          // go. The other direction still gives under the finger the way the browser's outermost
          // tab does, so it reads as "nothing over there" rather than as dead.
          val hasTarget = state.tab != null && state.offsetPx + delta.x > 0f
          state.offsetPx += if (hasTarget) delta.x else delta.x * NO_TARGET_RESISTANCE
        }
      }

      if (gesture == Gesture.UPWARD) {
        if (-totalY >= overviewThreshold) onOpenTabsOverview()
        return@awaitEachGesture
      }
      if (gesture != Gesture.SIDEWAYS) return@awaitEachGesture

      val committed = state.tab != null && state.offsetPx >= state.viewportWidthPx * COMMIT_FRACTION
      val target = if (committed) state.viewportWidthPx.toFloat() else 0f
      settleJob =
        scope.launch {
          animate(
            initialValue = state.offsetPx,
            targetValue = target,
            animationSpec =
              spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
          ) { value, _ ->
            state.offsetPx = value
          }
          // Left in place rather than reset: the browser opens without a window animation onto
          // this very image, so clearing it here would flash the home screen in between.
          if (committed) onOpenLastTab() else state.reset()
        }
    }
  }
}

private enum class Gesture {
  UNDECIDED,
  SIDEWAYS,
  UPWARD,
}

/**
 * The incoming tab during [browserTabSwipe]: an edge-to-edge panel in the tab's own page color, so
 * the launcher's wallpaper never shows through the gap the way it would around a floating preview.
 * The captured page sits inside it exactly where the browser will draw it — under the status bar,
 * above the browser's own chrome — so opening the browser continues the image rather than resizing
 * it.
 *
 * Also measures the viewport, which the gesture needs to decide when a swipe has gone far enough to
 * commit.
 */
@Composable
internal fun BrowserTabSwipePreview(
  state: BrowserTabSwipeState,
  /** Height of the launcher's chrome bar, which the browser's own bar closely matches. */
  chromeHeight: Dp,
  modifier: Modifier = Modifier,
) {
  // Only ever enters from the left, mirroring the browser's own "one screen further right".
  val visible by remember { derivedStateOf { state.offsetPx > 0.5f } }
  Box(
    modifier =
      modifier.fillMaxSize().onSizeChanged {
        state.viewportWidthPx = it.width.coerceAtLeast(1)
        state.viewportHeightPx = it.height.coerceAtLeast(1)
      }
  ) {
    val tab = state.tab
    if (!visible || tab == null) return@Box
    Box(
      modifier =
        Modifier.fillMaxSize()
          .graphicsLayer {
            // Starts one screen off the left edge and ends flush.
            translationX = state.offsetPx - state.viewportWidthPx
          }
          .background(Color(tab.pageBackgroundArgb))
    ) {
      tab.snapshot?.takeUnless(Bitmap::isRecycled)?.let { snapshot ->
        Image(
          bitmap = snapshot.asImageBitmap(),
          contentDescription = null,
          modifier =
            Modifier.fillMaxSize()
              .statusBarsPadding()
              .navigationBarsPadding()
              .padding(bottom = chromeHeight + BROWSER_CHROME_SPACING),
          // Matched by width from the top rather than zoomed or stretched to fit, so a capture of
          // a different height — taken behind the keyboard, or before a rotation — keeps its
          // proportions instead of snapping back to shape when the browser takes over.
          alignment = Alignment.TopCenter,
          contentScale = ContentScale.FillWidth,
        )
      }
    }
  }
}

/** Matches the browser's own tab-swipe commit threshold. */
private const val COMMIT_FRACTION = 0.18f
private const val NO_TARGET_RESISTANCE = 0.16f

/** The vertical padding BrowserLauncherChrome puts around its bar. */
private val BROWSER_CHROME_SPACING = 12.dp
