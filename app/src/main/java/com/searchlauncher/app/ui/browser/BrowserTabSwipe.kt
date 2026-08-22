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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
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
 * in from the screen edge ([onOpenLastTab]), up to raise the tabs overview ([onOpenTabsOverview]),
 * or — while it is up — back down to put it away again ([onCloseTabsOverview]).
 *
 * All are claimed during [PointerEventPass.Initial] because the chrome bar's own children (the
 * search field, the icon buttons) would otherwise swallow the movement. A downward drag with no
 * overview to close is left alone for whatever else is on screen.
 */
@Composable
internal fun Modifier.browserTabSwipe(
  state: BrowserTabSwipeState,
  enabled: Boolean,
  /** Only then does a downward drag mean anything here. */
  tabsOverviewOpen: Boolean,
  onOpenTabsOverview: () -> Unit,
  onCloseTabsOverview: () -> Unit,
  /**
   * The swipe has gone far enough to open the tab and the finger is off. Runs before the settle
   * animation rather than after it, for whatever should be underway while the tab slides in.
   */
  onCommitLastTab: () -> Unit,
  onOpenLastTab: () -> Unit,
  /**
   * The drag has been recognised as sideways and there is a tab to pull in, but nothing has moved
   * yet. The browser is expensive to build — it owns a WebView — and building it once the drag is
   * already under way costs about five frames, during which the home screen has no wallpaper and
   * the window shows through. Given the news here, the host can have it ready before then.
   */
  onSidewaysDragStart: () -> Unit = {},
  /** The drag ended without opening anything, so whatever was made ready can be let go. */
  onSidewaysDragAbandoned: () -> Unit = {},
): Modifier {
  val scope = rememberCoroutineScope()
  var settleJob by remember { mutableStateOf<Job?>(null) }

  return this.pointerInput(
    enabled,
    tabsOverviewOpen,
    onOpenTabsOverview,
    onCloseTabsOverview,
    onCommitLastTab,
    onOpenLastTab,
    onSidewaysDragStart,
    onSidewaysDragAbandoned,
  ) {
    if (!enabled) return@pointerInput
    val touchSlop = viewConfiguration.touchSlop
    val overviewThreshold = 24.dp.toPx()
    val flingVelocity = TAB_FLING_VELOCITY.toPx()
    val velocityTracker = VelocityTracker()
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      // The bar this gesture lives on is inside the layer that [BrowserTabSwipeState.offsetPx]
      // translates, so its local coordinates slide out from under the finger as the drag proceeds:
      // a still finger reads as moving back by however far the bar has come. Measured against that,
      // a small movement either way flipped the page between two positions about a screen-tenth
      // apart. Undoing the layer's own travel puts the finger back in a frame that holds still.
      val offsetAtStart = state.offsetPx
      fun PointerInputChange.screenX() = position.x + (state.offsetPx - offsetAtStart)
      val start = down.position
      var lastX = down.screenX()
      var totalX = 0f
      var totalY = 0f
      var gesture = Gesture.UNDECIDED
      velocityTracker.resetTracking()

      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        // Measured from where the finger landed rather than summed from each event's reported
        // delta. positionChange() answers zero for a change something else has already consumed,
        // and every icon button in the bar consumes its own press — so a total built by adding
        // those up could never climb past the slop, and a swipe that started on a button read as
        // no swipe at all. Absolute displacement cannot be undercounted that way.
        val screenX = change.screenX()
        totalX = screenX - start.x
        totalY = change.position.y - start.y
        val deltaX = screenX - lastX
        lastX = screenX
        if (gesture == Gesture.UNDECIDED) {
          when {
            abs(totalX) > touchSlop && abs(totalX) >= abs(totalY) -> {
              gesture = Gesture.SIDEWAYS
              settleJob?.cancel()
              state.tab = BrowserTabStore.lastTab()
              if (state.tab != null) onSidewaysDragStart()
            }
            -totalY > touchSlop -> gesture = Gesture.UPWARD
            // Downward closes the overview if one is up; otherwise it is someone else's gesture
            // and this bows out for the rest of the touch.
            totalY > touchSlop -> if (tabsOverviewOpen) gesture = Gesture.DOWNWARD else break
            else -> continue
          }
        }
        change.consume()
        if (gesture == Gesture.SIDEWAYS) {
          velocityTracker.addPointerInputChange(change)
          // The browser lives one screen to the left, so only a rightward pull has somewhere to
          // go. The other direction still gives under the finger the way the browser's outermost
          // tab does, so it reads as "nothing over there" rather than as dead.
          val hasTarget = state.tab != null && state.offsetPx + deltaX > 0f
          val proposed = state.offsetPx + if (hasTarget) deltaX else deltaX * NO_TARGET_RESISTANCE
          // One screen is the entire journey, so the offset cannot mean more than that. Left
          // unbounded it could: a drag beginning while the offset was still a full screen — during
          // the exit animation, say — kept adding to it, and past one screen the home content and
          // the browser are both translated off to the right at once, leaving a bare window.
          state.offsetPx = proposed.coerceAtMost(state.viewportWidthPx.toFloat())
        }
      }

      if (gesture == Gesture.UPWARD) {
        if (-totalY >= overviewThreshold) onOpenTabsOverview()
        return@awaitEachGesture
      }
      if (gesture == Gesture.DOWNWARD) {
        if (totalY >= overviewThreshold) onCloseTabsOverview()
        return@awaitEachGesture
      }
      if (gesture != Gesture.SIDEWAYS) return@awaitEachGesture

      // Shared with the browser's own tab swipe so the two ends of the same gesture agree.
      // Rightward only: the browser is the one screen to the left, so a leftward flick — which the
      // resistance above still lets wander a little negative — has nowhere to go.
      val committed =
        state.tab != null &&
          state.offsetPx > 0f &&
          shouldCommitTabSwipe(
            offsetPx = state.offsetPx,
            velocityPxPerSecond = velocityTracker.calculateVelocity().x,
            viewportWidthPx = state.viewportWidthPx,
            commitFraction = TAB_COMMIT_FRACTION,
            commitDistanceCapPx = TAB_COMMIT_MAX_DISTANCE.toPx(),
            flingVelocityPx = flingVelocity,
          )
      val target = if (committed) state.viewportWidthPx.toFloat() else 0f
      // Announced on the lift, not when the settle lands: from here the tab is going to open
      // whatever happens next, and anything the user should see respond — the keyboard on its way
      // out — has a whole animation's worth of time to do it in rather than snapping afterwards.
      if (committed) onCommitLastTab()
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
          if (committed) {
            onOpenLastTab()
          } else {
            state.reset()
            onSidewaysDragAbandoned()
          }
        }
    }
  }
}

private enum class Gesture {
  UNDECIDED,
  SIDEWAYS,
  UPWARD,
  DOWNWARD,
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
          .background(Color(tab.frameColorArgb))
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

private const val NO_TARGET_RESISTANCE = 0.16f

/** The vertical padding BrowserLauncherChrome puts around its bar. */
private val BROWSER_CHROME_SPACING = 12.dp
