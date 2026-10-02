package com.searchlauncher.app.ui.browser

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.searchlauncher.app.ui.MainActivity
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

  val towardsHome: Boolean
    get() = offset < 0 && neighbour(1) == null

  fun start() {
    settle?.cancel()
    inMotion = true
    velocity.resetTracking()
    // A frame was already captured at paint/load time, so the first drag is immediate.
    page.capture()
  }

  fun drag(change: androidx.compose.ui.input.pointer.PointerInputChange, delta: Float) {
    velocity.addPointerInputChange(change)
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
        var handedOver = false
        fun handOver() {
          handedOver = true
          page.persist()
          if (next != null) BrowserTabTasks.open(activity, next.id)
          else
            activity.startActivity(
              Intent(activity, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_FOCUS_SEARCH, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
        animate(
          initialValue = offset,
          targetValue = if (commit) -direction * widthPx.toFloat() else 0f,
          animationSpec =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        ) { value, _ ->
          offset = value
          if (commit && !handedOver && abs(value) >= widthPx * LAUNCHER_HANDOVER_FRACTION)
            handOver()
        }
        if (commit && !handedOver) handOver()
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
      if (event == Lifecycle.Event.ON_RESUME) swipe.reset()
    }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      swipe.reset()
    }
  }
  return swipe
}

internal fun Modifier.geckoChromeSwipe(swipe: GeckoChromeSwipe): Modifier =
  pointerInput(swipe) {
    detectHorizontalDragGestures(
      onDragStart = { swipe.start() },
      onDragEnd = { swipe.end() },
      onDragCancel = { swipe.end(cancelled = true) },
      onHorizontalDrag = { change, delta ->
        change.consume()
        swipe.drag(change, delta)
      },
    )
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
  if (!swipe.inMotion || swipe.offset == 0f) return
  val home = swipe.towardsHome
  val direction = if (swipe.offset < 0) 1 else -1
  Box(
    Modifier.fillMaxSize()
      .graphicsLayer { translationX = swipe.offset + direction * swipe.widthPx }
      .background(background)
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
