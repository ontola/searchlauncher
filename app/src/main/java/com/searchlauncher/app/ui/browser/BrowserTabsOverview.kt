package com.searchlauncher.app.ui.browser

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.net.URI
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Full-screen tabs overview: a scrim over whatever is behind, with the tab strip stacked directly
 * on top of the chrome bar so the bar stays where the thumb already is.
 *
 * [progress] runs 0..1 and is read as a lambda rather than a value, so a running transition redraws
 * without recomposing the (large) screens this is layered over.
 */
@Composable
internal fun BrowserTabsOverviewLayer(
  tabs: List<BrowserTab>,
  activeIndex: Int,
  progress: () -> Float,
  scrimColor: Color,
  contentColor: Color,
  cardWidth: Dp,
  previewAspectRatio: Float,
  /** Space the strip has for a preview; a card narrows rather than overflow it. */
  maxPreviewHeight: Dp,
  bottomInset: Dp,
  onDismiss: () -> Unit,
  onSelect: (Int) -> Unit,
  onCloseTab: (Int) -> Unit,
  onCloseAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .graphicsLayer { alpha = progress() }
        .background(scrimColor)
        // Also swallows touches meant for whatever is underneath, which is still live.
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onDismiss,
        )
  ) {
    BrowserTabsOverview(
      tabs = tabs,
      activeIndex = activeIndex,
      // Floored so an unusually tall chrome bar (favorites row up, keyboard reserved) shrinks the
      // cards rather than driving the width to zero or negative.
      cardWidth = minOf(cardWidth, maxPreviewHeight * previewAspectRatio).coerceAtLeast(96.dp),
      previewAspectRatio = previewAspectRatio,
      contentColor = contentColor,
      onSelect = onSelect,
      onCloseTab = onCloseTab,
      onCloseAll = onCloseAll,
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .padding(bottom = bottomInset)
          // Rises into place instead of simply materialising.
          .graphicsLayer { translationY = (1f - progress()) * 48.dp.toPx() },
    )
  }
}

/**
 * Horizontal strip of tab previews. Previews are the same per-tab bitmaps the browser slides
 * between during a tab swipe, so a tab always looks here exactly like it will when opened.
 */
@Composable
internal fun BrowserTabsOverview(
  tabs: List<BrowserTab>,
  activeIndex: Int,
  cardWidth: Dp,
  previewAspectRatio: Float,
  contentColor: Color,
  onSelect: (Int) -> Unit,
  onCloseTab: (Int) -> Unit,
  onCloseAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  // Opening the overview should show where you are, not the start of the strip.
  LaunchedEffect(Unit) {
    if (tabs.isNotEmpty()) listState.scrollToItem(activeIndex.coerceIn(0, tabs.lastIndex))
  }

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = if (tabs.size == 1) "1 tab" else "${tabs.size} tabs",
        style = MaterialTheme.typography.labelLarge,
        color = contentColor.copy(alpha = 0.7f),
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = onCloseAll) { Text("Close all", color = contentColor) }
    }

    LazyRow(
      state = listState,
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
        TabCard(
          tab = tab,
          isActive = index == activeIndex,
          cardWidth = cardWidth,
          previewAspectRatio = previewAspectRatio,
          contentColor = contentColor,
          onSelect = { onSelect(index) },
          onClose = { onCloseTab(index) },
          // Neighbours slide across to fill the gap a closed tab leaves behind.
          modifier = Modifier.animateItem(),
        )
      }
    }
  }
}

@Composable
private fun TabCard(
  tab: BrowserTab,
  isActive: Boolean,
  cardWidth: Dp,
  previewAspectRatio: Float,
  contentColor: Color,
  onSelect: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val cardShape = RoundedCornerShape(12.dp)
  val scope = rememberCoroutineScope()
  // Both ways of closing a tab — flicking the card up, and the ✕ — play the same exit, so the
  // button reads as a shortcut for the gesture rather than as a separate mechanism.
  val dismissOffsetPx = remember { Animatable(0f) }
  var cardHeightPx by remember { mutableIntStateOf(1) }
  var dismissing by remember { mutableStateOf(false) }

  fun dismiss() {
    if (dismissing) return
    dismissing = true
    scope.launch {
      dismissOffsetPx.animateTo(-cardHeightPx * 1.2f, tween(durationMillis = 220))
      onClose()
    }
  }

  Column(
    modifier =
      modifier
        .width(cardWidth)
        .onSizeChanged { cardHeightPx = it.height.coerceAtLeast(1) }
        .graphicsLayer {
          translationY = dismissOffsetPx.value
          // Fades out over the card's own height, so it is gone by the time it clears the strip.
          alpha = (1f - abs(dismissOffsetPx.value) / cardHeightPx).coerceIn(0f, 1f)
        }
        .pointerInput(tab.id) {
          detectVerticalDragGestures(
            onDragEnd = {
              if (-dismissOffsetPx.value >= cardHeightPx * DISMISS_FRACTION) dismiss()
              else
                scope.launch { dismissOffsetPx.animateTo(0f, spring(Spring.DampingRatioNoBouncy)) }
            },
            onDragCancel = {
              scope.launch { dismissOffsetPx.animateTo(0f, spring(Spring.DampingRatioNoBouncy)) }
            },
            onVerticalDrag = { change, dragAmount ->
              if (!dismissing) {
                // Upward only: dragging down would fight the strip's own resting position.
                scope.launch {
                  dismissOffsetPx.snapTo((dismissOffsetPx.value + dragAmount).coerceAtMost(0f))
                }
                change.consume()
              }
            },
          )
        }
        .clickable(onClick = onSelect)
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .aspectRatio(previewAspectRatio)
          .clip(cardShape)
          .background(Color(tab.pageBackgroundArgb))
          .then(
            if (isActive) {
              Modifier.border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
            } else {
              Modifier.border(1.dp, contentColor.copy(alpha = 0.25f), cardShape)
            }
          )
    ) {
      val snapshot = tab.snapshot?.takeUnless(Bitmap::isRecycled)
      if (snapshot != null) {
        Image(
          bitmap = snapshot.asImageBitmap(),
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          // Anchored to the top so the card shows the part of the page the user was reading
          // rather than a letterboxed whole-page squeeze.
          alignment = Alignment.TopCenter,
          contentScale = ContentScale.Crop,
        )
      } else {
        // A tab that has never been drawn (opened in the background, or trimmed under memory
        // pressure) still needs to look like a card rather than an empty hole.
        SiteIcon(tab, contentColor, size = 32.dp, modifier = Modifier.align(Alignment.Center))
      }

      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clickable { dismiss() },
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close tab",
          modifier = Modifier.padding(4.dp).size(16.dp),
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SiteIcon(tab, contentColor, size = 14.dp)
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = tabTitle(tab),
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    tabAddress(tab.url)?.let { address ->
      Text(
        text = address,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor.copy(alpha = 0.6f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 21.dp, end = 2.dp),
      )
    }
  }
}

/** The site's own favicon where the page reported one, otherwise a generic globe. */
@Composable
private fun SiteIcon(
  tab: BrowserTab,
  contentColor: Color,
  size: Dp,
  modifier: Modifier = Modifier,
) {
  val favicon = tab.favicon?.takeUnless(Bitmap::isRecycled)
  if (favicon != null) {
    Image(
      bitmap = favicon.asImageBitmap(),
      contentDescription = null,
      modifier = modifier.size(size),
      contentScale = ContentScale.Fit,
    )
  } else {
    Icon(
      imageVector = Icons.Default.Public,
      contentDescription = null,
      tint = contentColor.copy(alpha = 0.5f),
      modifier = modifier.size(size),
    )
  }
}

/** Page title, falling back to the host, then to the label a still-empty tab deserves. */
private fun tabTitle(tab: BrowserTab): String =
  tab.title?.takeUnless(String::isBlank)
    ?: runCatching { URI(tab.url).host }.getOrNull()?.takeUnless(String::isBlank)
    ?: "New tab"

/**
 * The address as a browser's URL bar would show it — no scheme, no trailing slash — or null for a
 * tab with nothing loaded, where an address line would just say "about:blank".
 */
private fun tabAddress(url: String): String? =
  url.removePrefix("https://").removePrefix("http://").removeSuffix("/").takeIf {
    it.isNotBlank() && !url.startsWith("about:")
  }

/** How far up a card must travel before letting go closes it rather than snapping it back. */
private const val DISMISS_FRACTION = 0.25f
