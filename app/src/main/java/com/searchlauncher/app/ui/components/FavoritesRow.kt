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
  onLaunch: (SearchResult) -> Unit,
  onRemoveFavorite: (SearchResult) -> Unit,
  onReorder: (List<String>) -> Unit,
) {
  if (favorites.isEmpty()) return

  val haptic = LocalHapticFeedback.current
  val density = LocalDensity.current
  val context = LocalContext.current

  // State for dragging
  var draggedItemId by remember { mutableStateOf<String?>(null) }
  var dragPosition by remember { mutableStateOf(Offset.Zero) }
  var totalDragX by remember { mutableStateOf(0f) }
  var currentOrder by remember(favorites) { mutableStateOf(favorites.map { it.id }) }
  var showMenuForIndex by remember { mutableStateOf<Int?>(null) }

  // Caching icons to prevent flickering
  val iconBitmaps =
    remember(favorites) { favorites.associate { it.id to it.icon?.toImageBitmap() } }

  BoxWithConstraints(
    modifier =
      Modifier.fillMaxWidth().wrapContentHeight().padding(horizontal = 16.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    val count = favorites.size
    val minSpacing = 8.dp
    val spacingPx = with(density) { minSpacing.toPx() }
    val totalWidthPx = constraints.maxWidth.toFloat()

    // Calculate layout metrics assuming CENTER alignment
    val calculatedSizePx = if (count > 0) (totalWidthPx - (spacingPx * (count - 1))) / count else 0f
    val finalIconSizePx =
      minOf(with(density) { 48.dp.toPx() }, if (calculatedSizePx > 0) calculatedSizePx else 1f)
    val finalIconSize = with(density) { finalIconSizePx.toDp() }

    val itemWidthPx = finalIconSizePx + spacingPx
    val contentWidthPx = (count * finalIconSizePx) + ((count - 1) * spacingPx)
    val startX = (totalWidthPx - contentWidthPx) / 2

    // 1. Items List (Visuals - Manual Layout)
    // We use a Box to hold the items, and precise offsets for positioning.
    Box(modifier = Modifier.fillMaxWidth().height(finalIconSize)) {
      favorites.forEach { result ->
        key(result.id) {
          val id = result.id
          // Determine the current index in the order list.
          // If not found (edge case), default to original index.
          val currentIndex =
            currentOrder.indexOf(id).takeIf { it != -1 } ?: favorites.indexOf(result)
          val isGhost = id == draggedItemId

          // Target X position for this item based on its index
          // We add startX so they are centered in the parent row
          val targetX = startX + (currentIndex * itemWidthPx)
          val targetXDp = with(density) { targetX.toDp() }

          // Animate to the new position
          val animatedOffsetX by animateDpAsState(targetValue = targetXDp, label = "ItemAnimation")

          Box(
            modifier =
              Modifier.offset(x = animatedOffsetX)
                .size(finalIconSize)
                .clip(RoundedCornerShape(12.dp))
                .graphicsLayer { alpha = if (isGhost) 0f else 1f }
                .pointerInput(result.id) { detectTapGestures(onTap = { onLaunch(result) }) }
                .pointerInput(result.id, startX, itemWidthPx) {
                  detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                      // Re-calculate targetX to ensure freshness
                      val freshIndex = currentOrder.indexOf(id).takeIf { it != -1 } ?: 0
                      val freshTargetX = startX + (freshIndex * itemWidthPx)

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
                      val currentIndex = currentOrder.indexOf(draggedId)
                      if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                      // Target calculation uses current dragPosition relative to startX
                      val targetIndex =
                        ((dragPosition.x - startX) / itemWidthPx)
                          .roundToInt()
                          .coerceIn(0, count - 1)

                      if (targetIndex != currentIndex) {
                        val newList = currentOrder.toMutableList()
                        val removedId = newList.removeAt(currentIndex)
                        newList.add(targetIndex, removedId)
                        currentOrder = newList
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      }
                    },
                    onDragEnd = {
                      val draggedId = draggedItemId
                      val index = if (draggedId != null) currentOrder.indexOf(draggedId) else -1
                      val dragThreshold = with(density) { 10.dp.toPx() }

                      if (index != -1 && totalDragX < dragThreshold) {
                        showMenuForIndex = index
                      } else {
                        onReorder(currentOrder)
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
              DropdownMenuItem(
                text = { Text("Remove from Favorites") },
                onClick = {
                  onRemoveFavorite(result)
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
      val result = favorites.find { it.id == id } ?: return@let
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
