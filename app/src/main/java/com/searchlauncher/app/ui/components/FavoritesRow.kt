package com.searchlauncher.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.searchlauncher.app.data.SearchResult
import com.searchlauncher.app.data.favoriteKey
import com.searchlauncher.app.ui.PreferencesKeys
import com.searchlauncher.app.ui.ThemedIcons
import com.searchlauncher.app.ui.dataStore
import com.searchlauncher.app.ui.toImageBitmap
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesRow(
  favorites: List<SearchResult>,
  history: List<SearchResult> = emptyList(),
  historyLimit: Int = -1,
  minIconSizeSetting: Int = 48,
  onLaunch: (SearchResult) -> Unit,
  onToggleFavorite: (SearchResult) -> Unit,
  onReorder: (List<String>) -> Unit,
  onCapacityChanged: (Int) -> Unit,
  /**
   * Grow icons past the preferred size so the row uses the full width when there are fewer items
   * than would fill it at [minIconSizeSetting].
   */
  expandToFill: Boolean = false,
  /** History is newest-first and sits against the divider; set false to keep the given order. */
  reverseHistory: Boolean = true,
  /** Draw the gap between pinned items and the fill/history items. */
  drawDivider: Boolean = true,
  /**
   * What long-pressing an item offers. Supplied by callers that can answer for a result in full —
   * the search screen hands over the same one its results list uses, which is what makes the two
   * menus identical. Left out, an item offers only what this row can do by itself.
   */
  menuActions: ((SearchResult) -> ResultMenuActions)? = null,
  /**
   * How many rows pinned favorites may wrap onto. [FAVORITES_MAX_ROWS_AUTO] grows as needed (up to
   * [FAVORITES_MAX_ROWS_CAP]); `1`–`4` cap growth at that many rows.
   */
  maxRows: Int = FAVORITES_MAX_ROWS_AUTO,
) {
  if (favorites.isEmpty() && history.isEmpty()) return

  val haptic = LocalHapticFeedback.current
  val density = LocalDensity.current

  val minIconSizeDp = minIconSizeSetting.dp
  val dividerGapDp = 16.dp
  val minSpacingDp = 6.dp
  val rowSpacingDp = 4.dp

  BoxWithConstraints(
    modifier =
      Modifier.fillMaxWidth().wrapContentHeight().padding(horizontal = 16.dp, vertical = 2.dp),
    contentAlignment = Alignment.Center,
  ) {
    val spacingPx = with(density) { minSpacingDp.toPx() }
    val minIconSizePx = with(density) { minIconSizeDp.toPx() }
    val dividerGapPx = with(density) { dividerGapDp.toPx() }
    val rowSpacingPx = with(density) { rowSpacingDp.toPx() }
    val totalWidthPx = constraints.maxWidth.toFloat()

    val measured =
      remember(
        favorites.size,
        history.size,
        historyLimit,
        totalWidthPx,
        minIconSizeSetting,
        drawDivider,
        expandToFill,
        maxRows,
      ) {
        computeFavoritesBarLayout(
            totalWidthPx = totalWidthPx,
            favoriteCount = favorites.size,
            historyLimit = historyLimit,
            minIconSizePx = minIconSizePx,
            spacingPx = spacingPx,
            dividerGapPx = dividerGapPx,
            drawDivider = drawDivider,
            expandToFill = expandToFill,
            maxRowsSetting = maxRows,
          )
          .also { onCapacityChanged(it.historyCapacity) }
      }

    val visibleHistory =
      remember(history, measured.historyCapacity, reverseHistory) {
        val visible = history.take(measured.historyCapacity)
        if (reverseHistory) visible.reversed() else visible
      }

    val allItems = favorites + visibleHistory
    val boundaryIndex = favorites.size
    val showDivider = measured.showDivider

    // State for dragging
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var totalDrag by remember { mutableStateOf(0f) }
    var currentOrder by remember(allItems) { mutableStateOf(allItems.map { it.favoriteKey }) }
    var showMenuForIndex by remember { mutableStateOf<Int?>(null) }
    var dragBoundaryIndex by remember(boundaryIndex) { mutableStateOf(boundaryIndex) }
    var initialDragIndex by remember { mutableStateOf(-1) }

    val totalCount = allItems.size
    val itemsPerRow = measured.itemsPerRow
    val rowCount = measured.rowCount

    // Update dragBoundaryIndex whenever order changes during drag
    LaunchedEffect(draggedItemId, currentOrder) {
      val id = draggedItemId ?: return@LaunchedEffect
      val bestIdx = currentOrder.indexOf(id).coerceAtLeast(0)
      val wasFavorite =
        boundaryIndex >
          allItems.indexOfFirst { it.favoriteKey == id }.let { if (it == -1) 0 else it }

      dragBoundaryIndex =
        if (wasFavorite) {
          if (bestIdx < boundaryIndex) boundaryIndex else (boundaryIndex - 1).coerceAtLeast(0)
        } else {
          if (bestIdx < boundaryIndex) (boundaryIndex + 1).coerceAtMost(totalCount)
          else boundaryIndex
        }
    }

    val context = LocalContext.current
    val themedIcons by
      remember { context.dataStore.data.map { it[PreferencesKeys.THEMED_ICONS] ?: false } }
        .collectAsState(initial = false)
    val themeBg = MaterialTheme.colorScheme.primary.toArgb()
    val themeFg = MaterialTheme.colorScheme.onPrimary.toArgb()
    // Caching icons to prevent flickering. The plain pass is synchronous so the row is never
    // blank; theming needs PackageManager (cached icons arrive flattened, with no monochrome
    // layer left to tint) and swaps in once it resolves.
    val plainIconBitmaps =
      remember(allItems) { allItems.associate { it.favoriteKey to it.icon?.toImageBitmap() } }
    val themedIconBitmaps by
      produceState<Map<String, ImageBitmap?>?>(null, allItems, themedIcons, themeBg, themeFg) {
        value =
          if (!themedIcons) null
          else
            allItems.associate { result ->
              val source =
                ThemedIcons.resolveThemeable(
                  context,
                  result.icon,
                  (result as? SearchResult.App)?.packageName,
                )
              result.favoriteKey to
                withContext(Dispatchers.IO) {
                  ThemedIcons.apply(source, themeBg, themeFg)?.toImageBitmap()
                }
            }
      }
    val iconBitmaps = themedIconBitmaps ?: plainIconBitmaps

    val finalIconSizePx = measured.iconSizePx
    val finalIconSize = with(density) { finalIconSizePx.toDp() }
    val barHeightPx = barHeightPx(rowCount, finalIconSizePx, rowSpacingPx)
    val barHeight = with(density) { barHeightPx.toDp() }

    val effectiveBoundary = if (draggedItemId != null) dragBoundaryIndex else boundaryIndex
    val effectiveShowDivider =
      if (draggedItemId != null) {
        effectiveBoundary > 0 && (totalCount - effectiveBoundary) > 0
      } else {
        showDivider
      }
    val effectiveLastRowFavs =
      if (rowCount <= 1) effectiveBoundary
      else (effectiveBoundary - itemsPerRow * (rowCount - 1)).coerceIn(0, itemsPerRow)

    fun xForIndex(index: Int): Float =
      itemX(
        index = index,
        totalCount = totalCount,
        itemsPerRow = itemsPerRow,
        iconSizePx = finalIconSizePx,
        spacingPx = spacingPx,
        totalWidthPx = totalWidthPx,
        showDivider = effectiveShowDivider,
        lastRowFavoriteCount = effectiveLastRowFavs,
        dividerGapPx = dividerGapPx,
        rowCount = rowCount,
      )

    fun yForIndex(index: Int): Float = itemY(index, itemsPerRow, finalIconSizePx, rowSpacingPx)

    Box(modifier = Modifier.fillMaxWidth().height(barHeight)) {
      if (effectiveShowDivider && effectiveLastRowFavs > 0) {
        val dividerIndex = effectiveBoundary.coerceIn(0, (totalCount - 1).coerceAtLeast(0))
        // Multi-row: the divider sits in the grid spacing. Single-row: the original reserved gap.
        val dividerX =
          if (rowCount > 1) xForIndex(dividerIndex) - (spacingPx / 2)
          else xForIndex(dividerIndex) - (dividerGapPx / 2) - (spacingPx / 2)
        val dividerY = yForIndex((rowCount - 1) * itemsPerRow)
        Box(
          modifier =
            Modifier.offset(
                x = with(density) { dividerX.toDp() },
                y = with(density) { (dividerY + finalIconSizePx * 0.15f).toDp() },
              )
              .size(width = 1.5.dp, height = finalIconSize * 0.7f)
              .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )
      }

      allItems.forEach { result ->
        val itemKey = result.favoriteKey
        key(itemKey) {
          val currentIndex =
            currentOrder.indexOf(itemKey).takeIf { it != -1 } ?: allItems.indexOf(result)
          val isGhost = itemKey == draggedItemId

          // Use static position for the Box receiving gestures to avoid fighting the animation
          val displayIndex = if (isGhost) initialDragIndex else currentIndex
          val xDp = with(density) { xForIndex(displayIndex).toDp() }
          val yDp = with(density) { yForIndex(displayIndex).toDp() }

          val animatedXDp by animateDpAsState(targetValue = xDp, label = "ItemAnimationX")
          val animatedYDp by animateDpAsState(targetValue = yDp, label = "ItemAnimationY")

          Box(
            modifier =
              Modifier.offset(x = animatedXDp, y = animatedYDp)
                .size(finalIconSize)
                .clip(RoundedCornerShape(12.dp))
                .graphicsLayer { alpha = if (isGhost) 0f else 1f }
                .pointerInput(itemKey) { detectTapGestures(onTap = { onLaunch(result) }) }
                .pointerInput(itemKey, itemsPerRow, finalIconSizePx) {
                  detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                      val freshIndex = currentOrder.indexOf(itemKey).takeIf { it != -1 } ?: 0
                      initialDragIndex = freshIndex

                      val currentVisualXPx = with(density) { animatedXDp.toPx() }
                      val currentVisualYPx = with(density) { animatedYDp.toPx() }

                      draggedItemId = itemKey
                      dragPosition =
                        Offset(currentVisualXPx + offset.x, currentVisualYPx + offset.y)
                      totalDrag = 0f
                      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, dragAmount ->
                      change.consume()
                      dragPosition += dragAmount
                      totalDrag += hypot(dragAmount.x, dragAmount.y)

                      val draggedId = draggedItemId ?: return@detectDragGesturesAfterLongPress
                      val dragIdx = currentOrder.indexOf(draggedId)
                      if (dragIdx == -1) return@detectDragGesturesAfterLongPress

                      var bestIndex = 0
                      var minDistance = Float.MAX_VALUE
                      for (i in 0 until totalCount) {
                        val centerX = xForIndex(i) + finalIconSizePx / 2
                        val centerY = yForIndex(i) + finalIconSizePx / 2
                        val dist = hypot(dragPosition.x - centerX, dragPosition.y - centerY)
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

                      if (finalIdx != -1 && totalDrag < dragThreshold) {
                        showMenuForIndex = finalIdx
                      } else {
                        val wasFavorite = favorites.any { it.favoriteKey == draggedId }
                        val isNowInFavoriteZone = finalIdx < boundaryIndex

                        if (!wasFavorite && isNowInFavoriteZone) {
                          onToggleFavorite(result)
                        } else if (wasFavorite && isNowInFavoriteZone) {
                          onReorder(currentOrder.take(boundaryIndex))
                        } else if (wasFavorite && !isNowInFavoriteZone) {
                          onToggleFavorite(result)
                        }
                      }

                      draggedItemId = null
                      initialDragIndex = -1
                      totalDrag = 0f
                    },
                    onDragCancel = {
                      draggedItemId = null
                      initialDragIndex = -1
                      totalDrag = 0f
                    },
                  )
                },
            contentAlignment = Alignment.Center,
          ) {
            val imageBitmap = iconBitmaps[itemKey]
            if (imageBitmap != null) {
              Image(
                bitmap = imageBitmap,
                contentDescription = result.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(finalIconSize * 0.8f),
              )
            } else {
              Box(
                modifier =
                  Modifier.size(finalIconSize * 0.7f)
                    .background(
                      MaterialTheme.colorScheme.surfaceVariant,
                      shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = result.title.firstOrNull()?.toString() ?: "?",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            DropdownMenu(
              expanded = showMenuForIndex == currentIndex,
              onDismissRequest = { showMenuForIndex = null },
              modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
              properties = PopupProperties(focusable = false),
            ) {
              val isFavorite = favorites.any { it.favoriteKey == result.favoriteKey }
              val actions =
                menuActions?.invoke(result)
                  ?: ResultMenuActions(onToggleFavorite = { onToggleFavorite(result) })
              ResultContextMenuItems(
                result = result,
                isFavorite = isFavorite,
                actions = actions,
                contactChatActions = rememberContactChatActions(result, actions),
                onCloseMenu = { showMenuForIndex = null },
              )
            }
          }
        }
      }
    }

    draggedItemId?.let { id ->
      val result = allItems.find { it.favoriteKey == id } ?: return@let
      val imageBitmap = iconBitmaps[id] ?: return@let

      Box(modifier = Modifier.fillMaxWidth().height(barHeight)) {
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
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(finalIconSize * 0.8f),
          )
        }
      }
    }
  }
}
