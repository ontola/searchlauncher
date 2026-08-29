package com.searchlauncher.app.ui.components

/**
 * How many rows the favorites bar uses.
 *
 * `-1` means Auto: add a row when pinned favorites no longer fit at the preferred icon size, up to
 * [FAVORITES_MAX_ROWS_CAP]. `1`–`4` always use that many rows, filling leftover cells with recents.
 * Overflowing favorites shrink to fit.
 */
const val FAVORITES_MAX_ROWS_AUTO = -1
const val FAVORITES_MAX_ROWS_CAP = 4

/** Grid + icon metrics for one measurement of the favorites bar. */
internal data class FavoritesBarLayout(
  val rowCount: Int,
  val itemsPerRow: Int,
  val iconSizePx: Float,
  val historyCapacity: Int,
  val showDivider: Boolean,
  /** Pinned items on the row that holds the divider; the divider (if any) follows this many. */
  val lastRowFavoriteCount: Int,
)

internal fun resolveFavoritesMaxRows(setting: Int): Int =
  if (setting < 0) FAVORITES_MAX_ROWS_CAP else setting.coerceIn(1, FAVORITES_MAX_ROWS_CAP)

/** Icon size is useful whenever history can pack a row, or a fixed row count sizes the grid. */
internal fun shouldShowFavoritesIconSizeSetting(historyLimit: Int, maxRowsSetting: Int): Boolean =
  historyLimit != 0 || maxRowsSetting > 0

/**
 * How many icons fit in [totalWidthPx] at [iconSizePx], matching the original single-row formula
 * (slightly generous via `+ spacing/2` so a near-fit still counts).
 */
internal fun itemsThatFit(
  totalWidthPx: Float,
  iconSizePx: Float,
  spacingPx: Float,
  reservedGapPx: Float = 0f,
): Int {
  val stride = iconSizePx + spacingPx
  if (stride <= 0f) return 1
  return ((totalWidthPx + spacingPx - reservedGapPx + (spacingPx / 2f)) / stride)
    .toInt()
    .coerceAtLeast(0)
}

internal fun ceilDiv(value: Int, divisor: Int): Int {
  if (divisor <= 0) return value
  if (value <= 0) return 0
  return (value + divisor - 1) / divisor
}

/**
 * Decide rows, grid width, icon size, and how many history items leftover cells can take.
 *
 * Auto grows only when favorites overflow. A fixed count always uses that many rows and fills the
 * extra cells with recents. A fixed history limit may shrink icons so that many items still fit.
 */
internal fun computeFavoritesBarLayout(
  totalWidthPx: Float,
  favoriteCount: Int,
  historyLimit: Int,
  minIconSizePx: Float,
  spacingPx: Float,
  dividerGapPx: Float,
  drawDivider: Boolean,
  expandToFill: Boolean,
  maxRowsSetting: Int,
): FavoritesBarLayout {
  val autoRows = maxRowsSetting < 0
  val maxRows = resolveFavoritesMaxRows(maxRowsSetting)
  val preferredPerRow = itemsThatFit(totalWidthPx, minIconSizePx, spacingPx).coerceAtLeast(1)

  val favRows =
    if (favoriteCount <= 0) 0 else ceilDiv(favoriteCount, preferredPerRow).coerceAtLeast(1)
  val rowCount =
    when {
      autoRows && favoriteCount <= 0 -> 1
      autoRows -> favRows.coerceIn(1, maxRows)
      else -> maxRows
    }

  var itemsPerRow =
    if (favoriteCount > rowCount * preferredPerRow) {
      ceilDiv(favoriteCount, rowCount).coerceAtLeast(1)
    } else {
      preferredPerRow
    }

  /** Favorites sitting on the row that holds the first leftover / history cell. */
  fun favsOnBoundaryRow(perRow: Int): Int {
    if (favoriteCount <= 0 || perRow <= 0) return 0
    val rem = favoriteCount % perRow
    return if (rem == 0) perRow else rem
  }

  fun historySlots(perRow: Int, boundaryFavs: Int): Int {
    if (historyLimit == 0) return 0
    // A multi-row bar is a grid: every leftover cell fills with recents. The divider sits in
    // existing spacing so it does not steal a slot. A single row still reserves the original gap.
    if (rowCount > 1) return (rowCount * perRow - favoriteCount).coerceAtLeast(0)
    val gap = if (drawDivider && boundaryFavs > 0) dividerGapPx else 0f
    val lastRowCap =
      if (gap > 0f) {
        if (perRow > preferredPerRow) perRow
        else itemsThatFit(totalWidthPx, minIconSizePx, spacingPx, gap)
      } else {
        perRow
      }
    return (lastRowCap - boundaryFavs).coerceAtLeast(0)
  }

  var lastFavs = favsOnBoundaryRow(itemsPerRow)
  var slots = historySlots(itemsPerRow, lastFavs)

  val historyCapacity =
    when {
      historyLimit == 0 -> 0
      historyLimit > 0 -> {
        val wanted = historyLimit
        if (wanted > slots) {
          val totalWanted = favoriteCount + wanted
          itemsPerRow = ceilDiv(totalWanted, rowCount).coerceAtLeast(1)
          lastFavs = favsOnBoundaryRow(itemsPerRow)
          slots = (rowCount * itemsPerRow - favoriteCount).coerceAtLeast(0)
        }
        wanted.coerceAtMost(slots)
      }
      else -> slots
    }

  val visibleTotal = favoriteCount + historyCapacity
  val showDivider = drawDivider && favoriteCount > 0 && historyCapacity > 0
  lastFavs = favsOnBoundaryRow(itemsPerRow)

  val fullestRow = if (rowCount <= 1) visibleTotal.coerceAtLeast(1) else itemsPerRow
  val gapForSize = if (rowCount <= 1 && showDivider) dividerGapPx else 0f
  val iconSizePx =
    computeIconSizePx(
      totalWidthPx = totalWidthPx,
      itemCount = fullestRow,
      spacingPx = spacingPx,
      reservedGapPx = gapForSize,
      preferredIconSizePx = minIconSizePx,
      expandToFill = expandToFill,
    )

  return FavoritesBarLayout(
    rowCount = rowCount,
    itemsPerRow = itemsPerRow,
    iconSizePx = iconSizePx,
    historyCapacity = historyCapacity,
    showDivider = showDivider,
    lastRowFavoriteCount = if (rowCount <= 1) favoriteCount else lastFavs,
  )
}

internal fun computeIconSizePx(
  totalWidthPx: Float,
  itemCount: Int,
  spacingPx: Float,
  reservedGapPx: Float,
  preferredIconSizePx: Float,
  expandToFill: Boolean,
): Float {
  if (itemCount <= 0) return preferredIconSizePx.coerceAtLeast(1f)
  val reserved = spacingPx * (itemCount - 1) + reservedGapPx
  val calculated = (totalWidthPx - reserved) / itemCount
  return when {
    calculated <= 0f -> 1f
    expandToFill -> calculated
    else -> minOf(preferredIconSizePx, calculated)
  }
}

/** Newest-first history, left to right unless [reverse] puts the newest at the trailing end. */
internal fun <T> takeHistoryForDisplay(history: List<T>, capacity: Int, reverse: Boolean): List<T> {
  val visible = history.take(capacity.coerceAtLeast(0))
  return if (reverse) visible.reversed() else visible
}

internal fun itemRow(index: Int, itemsPerRow: Int): Int =
  if (itemsPerRow <= 0) 0 else index / itemsPerRow

internal fun itemCol(index: Int, itemsPerRow: Int): Int =
  if (itemsPerRow <= 0) index else index % itemsPerRow

internal fun rowItemCount(row: Int, totalCount: Int, itemsPerRow: Int): Int {
  if (itemsPerRow <= 0) return totalCount
  val start = row * itemsPerRow
  return (totalCount - start).coerceIn(0, itemsPerRow)
}

/**
 * Left edge of [index] within the bar.
 *
 * A single row stays centered as a group, with the original divider gap. Multiple rows share one
 * column grid so a short last row lines up under the first instead of floating in the middle.
 */
internal fun itemX(
  index: Int,
  totalCount: Int,
  itemsPerRow: Int,
  iconSizePx: Float,
  spacingPx: Float,
  totalWidthPx: Float,
  showDivider: Boolean,
  lastRowFavoriteCount: Int,
  dividerGapPx: Float,
  rowCount: Int,
): Float {
  val row = itemRow(index, itemsPerRow)
  val col = itemCol(index, itemsPerRow)
  val lastRow = (rowCount - 1).coerceAtLeast(0)
  if (rowCount > 1) {
    val gridWidth = itemsPerRow * iconSizePx + (itemsPerRow - 1).coerceAtLeast(0) * spacingPx
    val startX = (totalWidthPx - gridWidth) / 2f
    return startX + col * (iconSizePx + spacingPx)
  }
  val countInRow = rowItemCount(row, totalCount, itemsPerRow)
  val dividerInRow =
    showDivider && row == lastRow && lastRowFavoriteCount > 0 && countInRow > lastRowFavoriteCount
  val gap = if (dividerInRow) dividerGapPx else 0f
  val contentWidth = countInRow * iconSizePx + (countInRow - 1).coerceAtLeast(0) * spacingPx + gap
  val startX = (totalWidthPx - contentWidth) / 2f
  var x = startX + col * (iconSizePx + spacingPx)
  if (dividerInRow && col >= lastRowFavoriteCount) x += dividerGapPx
  return x
}

internal fun itemY(index: Int, itemsPerRow: Int, iconSizePx: Float, rowSpacingPx: Float): Float {
  val row = itemRow(index, itemsPerRow)
  return row * (iconSizePx + rowSpacingPx)
}

internal fun barHeightPx(rowCount: Int, iconSizePx: Float, rowSpacingPx: Float): Float {
  if (rowCount <= 0) return 0f
  return rowCount * iconSizePx + (rowCount - 1) * rowSpacingPx
}
