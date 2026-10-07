package com.searchlauncher.app.ui.browser

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Same tab order, distance threshold and fling rule as the WebView browser's bottom bar. */
@Stable
internal class GeckoChromeSwipe(
  private val page: GeckoPage,
  private val activity: BrowserActivity,
  private val privateMode: Boolean,
  private val scope: CoroutineScope,
  private val distanceCap: Float,
  private val flingVelocity: Float,
) {
  var offset by mutableFloatStateOf(0f)
  var inMotion by mutableStateOf(false)
  var widthPx = 1
  private var settle: Job? = null
  private val velocity = VelocityTracker()

  fun neighbour(direction: Int): BrowserTab? {
    if (privateMode) return null
    val tabs = BrowserTabStore.tabs ?: return null
    return tabs.items.getOrNull(tabs.indexOfFirst(page.tab.id) + direction)
  }

  fun start() {
    settle?.cancel()
    inMotion = true
    velocity.resetTracking()
    // Reuse the painted preview. Capturing/scaling another bitmap while dragging competes
    // with animation frames and can replace the image in the middle of the gesture.
  }

  fun track(time: Long, screenPosition: androidx.compose.ui.geometry.Offset) {
    velocity.addPosition(time, screenPosition)
  }

  fun drag(delta: Float) {
    val proposed = offset + delta
    val allowed = proposed <= 0 || neighbour(-1) != null
    offset =
      (offset + if (allowed) delta else delta * 0.16f).coerceIn(
        -widthPx.toFloat(),
        widthPx.toFloat(),
      )
  }

  fun end(cancelled: Boolean = false) {
    val direction = if (offset < 0) 1 else -1
    val next = neighbour(direction)
    val home = direction == 1 && next == null
    val commit =
      !cancelled &&
        (next != null || home) &&
        shouldCommitTabSwipe(
          offset,
          velocity.calculateVelocity().x,
          widthPx,
          TAB_COMMIT_FRACTION,
          distanceCap,
          flingVelocity,
        )
    settle =
      scope.launch {
        fun handOver() {
          page.persist()
          if (next != null) BrowserTabTasks.open(activity, next.id)
          else BrowserTabTasks.openHome(activity)
        }
        animate(
          initialValue = offset,
          targetValue = if (commit) -direction * widthPx.toFloat() else 0f,
          animationSpec =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        ) { value, _ ->
          offset = value
        }
        // Switching activities at 85% cut off the remaining travel with a visible jump.
        if (commit) handOver()
        if (!commit) inMotion = false
      }
  }

  fun reset() {
    settle?.cancel()
    offset = 0f
    inMotion = false
  }
}

@Composable
internal fun rememberGeckoChromeSwipe(page: GeckoPage, privateMode: Boolean): GeckoChromeSwipe {
  val activity = LocalContext.current as BrowserActivity
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val swipe =
    remember(page, density) {
      GeckoChromeSwipe(
        page,
        activity,
        privateMode,
        scope,
        with(density) { TAB_COMMIT_MAX_DISTANCE.toPx() },
        with(density) { TAB_FLING_VELOCITY.toPx() },
      )
    }
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(swipe, lifecycle) {
    val observer = LifecycleEventObserver { _, event ->
      // Keep the completed destination painted while Android fades the outgoing task.
      // ON_STOP is not proof that the window has stopped participating in that transition.
      if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) swipe.reset()
    }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      swipe.reset()
    }
  }
  return swipe
}

@Composable
internal fun Modifier.geckoChromeSwipe(swipe: GeckoChromeSwipe): Modifier {
  var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
  return onGloballyPositioned { coordinates = it }
    .pointerInput(swipe) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        fun screenPosition(change: androidx.compose.ui.input.pointer.PointerInputChange) =
          coordinates?.takeIf { it.isAttached }?.localToRoot(change.position) ?: change.position
        val start = screenPosition(down)
        var previousX = start.x
        var dragging = false
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          val change = event.changes.firstOrNull { it.id == down.id }
          if (change == null) {
            if (dragging) swipe.end(cancelled = true)
            break
          }
          // The toolbar moves with the page. Measure the finger in stationary screen coordinates,
          // otherwise the toolbar's own travel feeds back into drag distance and fling velocity.
          val position = screenPosition(change)
          val screenX = position.x
          val dx = screenX - start.x
          val dy = position.y - start.y
          if (!change.pressed) {
            if (dragging) {
              swipe.track(change.uptimeMillis, position)
              swipe.end()
            }
            break
          }
          if (!dragging) {
            if (abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) break
            if (abs(dx) <= viewConfiguration.touchSlop) continue
            swipe.start()
            swipe.track(down.uptimeMillis, start)
            dragging = true
            previousX = start.x + kotlin.math.sign(dx) * viewConfiguration.touchSlop
          }
          change.consume()
          swipe.track(change.uptimeMillis, position)
          swipe.drag(screenX - previousX)
          previousX = screenX
        }
      }
    }
}

@Composable
internal fun GeckoTabPreview(tab: BrowserTab, modifier: Modifier = Modifier) {
  tab.snapshot
    ?.takeUnless { it.isRecycled }
    ?.let {
      Image(
        it.asImageBitmap(),
        null,
        modifier,
        alignment = Alignment.TopCenter,
        contentScale = ContentScale.FillWidth,
      )
    }
}

@Composable
internal fun GeckoSwipeDestination(swipe: GeckoChromeSwipe, background: Color) {
  // Offset is read by the render layer only; don't recompose this full-screen image every frame.
  val direction by
    remember(swipe) {
      derivedStateOf {
        when {
          !swipe.inMotion || swipe.offset == 0f -> 0
          swipe.offset < 0f -> 1
          else -> -1
        }
      }
    }
  if (direction == 0) return
  val home = direction == 1 && swipe.neighbour(1) == null
  Box(
    Modifier.fillMaxSize()
      .graphicsLayer { translationX = swipe.offset + direction * swipe.widthPx }
      .background(if (home) Color.Transparent else background)
  ) {
    if (home) {
      HomeSwipePreview.image?.let {
        Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
      }
    } else
      swipe.neighbour(direction)?.let { tab ->
        Box(
          Modifier.fillMaxSize()
            .background(Color(tab.frameColorArgb))
            .statusBarsPadding()
            .navigationBarsPadding()
        ) {
          GeckoTabPreview(tab, Modifier.fillMaxSize())
        }
      }
  }
}
