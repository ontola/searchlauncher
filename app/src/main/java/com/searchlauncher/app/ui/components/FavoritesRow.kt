package com.searchlauncher.app.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.ui.toImageBitmap
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesRow(
  favorites: List<SearchResult>,
  history: List<SearchResult> = emptyList(),
  historyLimit: Int = -1,
  minIconSizeSetting: Int = 36,
  onLaunch: (SearchResult) -> Unit,
  onToggleFavorite: (SearchResult) -> Unit,
  onReorder: (List<String>) -> Unit,
) {
  if (favorites.isEmpty() && history.isEmpty()) return

  val haptic = LocalHapticFeedback.current
  val density = LocalDensity.current
  val context = LocalContext.current

  val minIconSizeDp = minIconSizeSetting.dp
  val dividerGapDp = 16.dp

  BoxWithConstraints(
    modifier =
      Modifier.fillMaxWidth().wrapContentHeight().padding(horizontal = 16.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    val minSpacingDp = 8.dp
    val spacingPx = with(density) { minSpacingDp.toPx() }
    val minIconSizePx = with(density) { minIconSizeDp.toPx() }
    val dividerGapPx = with(density) { dividerGapDp.toPx() }
    val totalWidthPx = constraints.maxWidth.toFloat()

    // Dynamically calculate how many history items we can actually fit
    val visibleHistory =
      remember(favorites, history, totalWidthPx, historyLimit) {
        val effectiveLimit =
          if (historyLimit >= 0) {
            historyLimit
          } else {
            val showDividerInitial = favorites.isNotEmpty() && history.isNotEmpty()
            val gapToReserve = if (showDividerInitial) dividerGapPx else 0f

            // Calculate max items that fit at min size
            val maxTotalItems =
              ((totalWidthPx + spacingPx - gapToReserve) / (minIconSizePx + spacingPx)).toInt()
            (maxTotalItems - favorites.size).coerceAtLeast(0)
          }

        history.take(effectiveLimit)
      }

    val allItems = favorites + visibleHistory
    val boundaryIndex = favorites.size
    val showDivider = favorites.isNotEmpty() && visibleHistory.isNotEmpty()

    // State for dragging
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var totalDragX by remember { mutableStateOf(0f) }
    var currentOrder by remember(allItems) { mutableStateOf(allItems.map { it.id }) }
    var showMenuForIndex by remember { mutableStateOf<Int?>(null) }
    var dragBoundaryIndex by remember(boundaryIndex) { mutableStateOf(boundaryIndex) }

    val totalCount = allItems.size

    // Update dragBoundaryIndex whenever order changes during drag
    LaunchedEffect(draggedItemId, currentOrder) {
      val id = draggedItemId ?: return@LaunchedEffect
      val bestIdx = currentOrder.indexOf(id).coerceAtLeast(0)
      val wasFavorite =
        boundaryIndex > allItems.indexOfFirst { it.id == id }.let { if (it == -1) 0 else it }

      dragBoundaryIndex =
        if (wasFavorite) {
          if (bestIdx < boundaryIndex) boundaryIndex else (boundaryIndex - 1).coerceAtLeast(0)
        } else {
          if (bestIdx < boundaryIndex) (boundaryIndex + 1).coerceAtMost(totalCount)
          else boundaryIndex
        }
    }

    // Caching icons to prevent flickering
    val iconBitmaps =
      remember(allItems) { allItems.associate { it.id to it.icon?.toImageBitmap() } }

    // Calculate layout metrics
    // We reserve space for the divider gap if needed
    val effectiveSpacingCount = totalCount - 1
    val baseReservedSpace =
      (spacingPx * effectiveSpacingCount) + (if (showDivider) dividerGapPx else 0f)

    val calculatedSizePx =
      if (totalCount > 0) (totalWidthPx - baseReservedSpace) / totalCount else 0f
    val finalIconSizePx =
      minOf(with(density) { 48.dp.toPx() }, if (calculatedSizePx > 0) calculatedSizePx else 1f)
    val finalIconSize = with(density) { finalIconSizePx.toDp() }

    val itemWidthPx = finalIconSizePx + spacingPx
    val contentWidthPx = (totalCount * finalIconSizePx) + baseReservedSpace - spacingPx
    val startX = (totalWidthPx - contentWidthPx) / 2

    val effectiveBoundary = if (draggedItemId != null) dragBoundaryIndex else boundaryIndex
    val effectiveShowDivider =
      if (draggedItemId != null) {
        effectiveBoundary > 0 && (totalCount - effectiveBoundary) > 0
      } else {
        showDivider
      }

    // Helper to calculate relative X (without startX) for an item at a specific index
    fun getRelativeXForIndex(index: Int): Float {
      var x = index * (finalIconSizePx + spacingPx)
      if (effectiveShowDivider && index >= effectiveBoundary) {
        x += dividerGapPx
      }
      return x
    }

    // Original helper for drag logic (includes startX)
    fun getXPositionForIndex(index: Int): Float {
      return startX + getRelativeXForIndex(index)
    }

    Box(modifier = Modifier.fillMaxWidth().height(finalIconSize)) {
      // Background Divider
      if (effectiveShowDivider) {
        val relativeDividerX =
          getRelativeXForIndex(effectiveBoundary) - (dividerGapPx / 2) - (spacingPx / 2)
        Box(
          modifier =
            Modifier.offset(x = with(density) { (startX + relativeDividerX).toDp() })
              .width(1.5.dp)
              .height(finalIconSize * 0.7f)
              .align(Alignment.CenterStart)
              .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        )
      }

      allItems.forEach { result ->
        key(result.id) {
          val id = result.id
          val currentIndex =
            currentOrder.indexOf(id).takeIf { it != -1 } ?: allItems.indexOf(result)
          val isGhost = id == draggedItemId

          val relativeX = getRelativeXForIndex(currentIndex)
          val relativeXDp = with(density) { relativeX.toDp() }

          // Animate ONLY the relative position, not the startX group shift
          val animatedRelativeXDp by
            animateDpAsState(targetValue = relativeXDp, label = "ItemAnimation")
          val startXDp = with(density) { startX.toDp() }

          Box(
            modifier =
              Modifier.offset(x = startXDp + animatedRelativeXDp)
                .size(finalIconSize)
                .clip(RoundedCornerShape(12.dp))
                .graphicsLayer { alpha = if (isGhost) 0f else 1f }
                .pointerInput(result.id) { detectTapGestures(onTap = { onLaunch(result) }) }
                .pointerInput(result.id, startX, itemWidthPx) {
                  detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                      val freshIndex = currentOrder.indexOf(id).takeIf { it != -1 } ?: 0
                      val freshTargetX = getXPositionForIndex(freshIndex)

                      draggedItemId = id
                      dragPosition = Offset(freshTargetX + offset.x, offset.y)
                      totalDragX = 0f
                      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, dragAmount ->
                      change.consume()
                      dragPosition += dragAmount
                      totalDragX += abs(dragAmount.x)

                      val draggedId = draggedItemId ?: return@detectDragGesturesAfterLongPress
                      val dragIdx = currentOrder.indexOf(draggedId)
                      if (dragIdx == -1) return@detectDragGesturesAfterLongPress

                      // Calculate target index by finding which item center it's closest to
                      var bestIndex = 0
                      var minDistance = Float.MAX_VALUE
                      for (i in 0 until totalCount) {
                        val centerX = getXPositionForIndex(i) + finalIconSizePx / 2
                        val dist = abs(dragPosition.x - centerX)
                        if (dist < minDistance) {
                          minDistance = dist
                          bestIndex = i
                        }
                      }

                      if (bestIndex != dragIdx) {
                        val newList = currentOrder.toMutableList()
                        val removedId = newList.removeAt(dragIdx)
                        newList.add(bestIndex, removedId)
                        currentOrder = newList
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      }
                    },
                    onDragEnd = {
                      val draggedId = draggedItemId ?: return@detectDragGesturesAfterLongPress
                      val finalIdx = currentOrder.indexOf(draggedId)
                      val dragThreshold = with(density) { 10.dp.toPx() }

                      if (finalIdx != -1 && totalDragX < dragThreshold) {
                        showMenuForIndex = finalIdx
                      } else {
                        // REORDER / MOVE Logic
                        val wasFavorite = favorites.any { it.id == draggedId }
                        val isNowInFavoriteZone = finalIdx < boundaryIndex

                        if (!wasFavorite && isNowInFavoriteZone) {
                          // History item moved to favorites!
                          onToggleFavorite(result)
                          // The SearchScreen will re-trigger a fetch and update currentOrder
                        } else if (wasFavorite && isNowInFavoriteZone) {
                          // Just reordering within favorites
                          onReorder(currentOrder.take(boundaryIndex))
                        } else if (wasFavorite && !isNowInFavoriteZone) {
                          // Favorite moved out - unfavorite it? (User didn't ask but it's natural)
                          onToggleFavorite(result)
                        }
                      }

                      draggedItemId = null
                      totalDragX = 0f
                    },
                    onDragCancel = {
                      draggedItemId = null
                      totalDragX = 0f
                    },
                  )
                },
            contentAlignment = Alignment.Center,
          ) {
            val imageBitmap = iconBitmaps[id]
            if (imageBitmap != null) {
              Image(
                bitmap = imageBitmap,
                contentDescription = result.title,
                modifier = Modifier.size(finalIconSize * 0.8f),
              )
            }

            // Context Menu
            DropdownMenu(
              expanded = showMenuForIndex == currentIndex, // Use currentIndex for menu
              onDismissRequest = { showMenuForIndex = null },
              modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
              properties = PopupProperties(focusable = false),
            ) {
              val isFavorite = favorites.any { it.id == result.id }

              DropdownMenuItem(
                text = { Text(if (isFavorite) "Remove from Favorites" else "Add Favorite") },
                onClick = {
                  onToggleFavorite(result)
                  showMenuForIndex = null
                },
                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
              )

              if (result is SearchResult.App) {
                DropdownMenuItem(
                  text = { Text("App Info") },
                  onClick = {
                    try {
                      val intent =
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                      intent.data = Uri.parse("package:${result.packageName}")
                      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                      context.startActivity(intent)
                    } catch (e: Exception) {
                      Toast.makeText(context, "Cannot open App Info", Toast.LENGTH_SHORT).show()
                    }
                    showMenuForIndex = null
                  },
                  leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                )
              }
            }
          }
        }
      }
    }

    // 2. Drag Overlay
    // Positioned using global coordinates derived from gesture
    draggedItemId?.let { id ->
      val result = allItems.find { it.id == id } ?: return@let
      val imageBitmap = iconBitmaps[id] ?: return@let

      Box(modifier = Modifier.fillMaxWidth().height(finalIconSize)) {
        Box(
          modifier =
            Modifier.offset {
                IntOffset(
                  (dragPosition.x - finalIconSizePx / 2).roundToInt(),
                  (dragPosition.y - finalIconSizePx / 2).roundToInt(),
                )
              }
              .size(finalIconSize)
              .graphicsLayer {
                scaleX = 1.25f
                scaleY = 1.25f
                shadowElevation = 12f
              }
              .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                RoundedCornerShape(12.dp),
              )
              .clip(RoundedCornerShape(12.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Image(
            bitmap = imageBitmap,
            contentDescription = result.title,
            modifier = Modifier.size(finalIconSize * 0.8f),
          )
        }
      }
    }
  }
}
