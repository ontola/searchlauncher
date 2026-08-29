package com.searchlauncher.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesBarLayoutTest {

  private val width = 380f
  private val icon = 32f
  private val spacing = 6f
  private val divider = 16f

  /** 380 + 6 + 3 = 389; 389 / 38 = 10.23 → 10 icons at the preferred size. */
  private val tenFit = itemsThatFit(width, icon, spacing)

  private fun layout(
    favorites: Int,
    historyLimit: Int = -1,
    maxRows: Int = FAVORITES_MAX_ROWS_AUTO,
    drawDivider: Boolean = true,
    expandToFill: Boolean = false,
  ) =
    computeFavoritesBarLayout(
      totalWidthPx = width,
      favoriteCount = favorites,
      historyLimit = historyLimit,
      minIconSizePx = icon,
      spacingPx = spacing,
      dividerGapPx = divider,
      drawDivider = drawDivider,
      expandToFill = expandToFill,
      maxRowsSetting = maxRows,
    )

  @Test
  fun `itemsThatFit matches the original single-row formula`() {
    assertEquals(10, tenFit)
    assertEquals(9, itemsThatFit(width, icon, spacing, reservedGapPx = divider))
  }

  @Test
  fun `favorites that fit stay on one row`() {
    val result = layout(favorites = 4)
    assertEquals(1, result.rowCount)
    assertEquals(10, result.itemsPerRow)
    assertEquals(icon, result.iconSizePx, 0.01f)
    assertTrue(result.historyCapacity > 0)
    assertTrue(result.showDivider)
    assertEquals(4, result.lastRowFavoriteCount)
  }

  @Test
  fun `overflowing favorites wrap onto a second row`() {
    val result = layout(favorites = 14)
    assertEquals(2, result.rowCount)
    assertEquals(10, result.itemsPerRow)
    assertEquals(icon, result.iconSizePx, 0.01f)
    // 20 slots − 14 favorites = 6 leftover on the last row (divider reduces that a little).
    assertTrue(result.historyCapacity in 5..6)
    assertEquals(4, result.lastRowFavoriteCount)
  }

  @Test
  fun `a one-row cap shrinks icons instead of wrapping`() {
    val result = layout(favorites = 14, maxRows = 1, historyLimit = 0)
    assertEquals(1, result.rowCount)
    assertEquals(14, result.itemsPerRow)
    assertTrue(result.iconSizePx < icon)
    assertEquals(0, result.historyCapacity)
    assertFalse(result.showDivider)
  }

  @Test
  fun `auto history never adds a row of its own`() {
    val few = layout(favorites = 3, historyLimit = -1)
    assertEquals(1, few.rowCount)
    val none = layout(favorites = 10, historyLimit = -1)
    assertEquals(1, none.rowCount)
    // Ten fill a row exactly; leftover history is zero (or one less if the divider is reserved).
    assertEquals(0, none.historyCapacity)
  }

  @Test
  fun `hiding history leaves leftover slots empty`() {
    val result = layout(favorites = 4, historyLimit = 0)
    assertEquals(0, result.historyCapacity)
    assertFalse(result.showDivider)
    assertEquals(1, result.rowCount)
    assertEquals(icon, result.iconSizePx, 0.01f)
  }

  @Test
  fun `fixed history shrinks a single row to fit`() {
    val result = layout(favorites = 8, historyLimit = 8, maxRows = 1)
    assertEquals(1, result.rowCount)
    assertEquals(16, result.itemsPerRow)
    assertEquals(8, result.historyCapacity)
    assertTrue(result.iconSizePx < icon)
  }

  @Test
  fun `history-only uses a single row`() {
    val result = layout(favorites = 0, historyLimit = -1)
    assertEquals(1, result.rowCount)
    assertEquals(0, result.lastRowFavoriteCount)
    assertFalse(result.showDivider)
    assertEquals(tenFit, result.historyCapacity)
  }

  @Test
  fun `auto grows up to the cap then shrinks`() {
    val overflow = layout(favorites = 41, historyLimit = 0)
    assertEquals(FAVORITES_MAX_ROWS_CAP, overflow.rowCount)
    assertTrue(overflow.itemsPerRow > tenFit)
    assertTrue(overflow.iconSizePx < icon)
  }

  @Test
  fun `resolveFavoritesMaxRows treats negative as auto`() {
    assertEquals(FAVORITES_MAX_ROWS_CAP, resolveFavoritesMaxRows(FAVORITES_MAX_ROWS_AUTO))
    assertEquals(1, resolveFavoritesMaxRows(1))
    assertEquals(FAVORITES_MAX_ROWS_CAP, resolveFavoritesMaxRows(99))
    assertEquals(2, resolveFavoritesMaxRows(2))
  }

  @Test
  fun `item cells are row-major`() {
    assertEquals(0, itemRow(4, itemsPerRow = 6))
    assertEquals(4, itemCol(4, itemsPerRow = 6))
    assertEquals(1, itemRow(8, itemsPerRow = 6))
    assertEquals(2, itemCol(8, itemsPerRow = 6))
    assertEquals(6, rowItemCount(0, totalCount = 8, itemsPerRow = 6))
    assertEquals(2, rowItemCount(1, totalCount = 8, itemsPerRow = 6))
  }

  @Test
  fun `second-row items sit below the first`() {
    val y0 = itemY(0, itemsPerRow = 6, iconSizePx = 32f, rowSpacingPx = 4f)
    val y1 = itemY(6, itemsPerRow = 6, iconSizePx = 32f, rowSpacingPx = 4f)
    assertEquals(0f, y0, 0.01f)
    assertEquals(36f, y1, 0.01f)
    assertEquals(68f, barHeightPx(2, iconSizePx = 32f, rowSpacingPx = 4f), 0.01f)
  }

  @Test
  fun `last-row items are centered and skip the divider gap`() {
    // One row, 4 favorites + 2 history, divider between them.
    val x0 =
      itemX(
        index = 0,
        totalCount = 6,
        itemsPerRow = 10,
        iconSizePx = 32f,
        spacingPx = 6f,
        totalWidthPx = width,
        showDivider = true,
        lastRowFavoriteCount = 4,
        dividerGapPx = divider,
        rowCount = 1,
      )
    val x4 =
      itemX(
        index = 4,
        totalCount = 6,
        itemsPerRow = 10,
        iconSizePx = 32f,
        spacingPx = 6f,
        totalWidthPx = width,
        showDivider = true,
        lastRowFavoriteCount = 4,
        dividerGapPx = divider,
        rowCount = 1,
      )
    // Four icons + 3 gaps + divider + start of the 5th icon.
    val contentWidth = 6 * 32f + 5 * 6f + divider
    val start = (width - contentWidth) / 2f
    assertEquals(start, x0, 0.01f)
    assertEquals(start + 4 * (32f + 6f) + divider, x4, 0.01f)
  }

  @Test
  fun `expandToFill grows icons on a short single row`() {
    val result = layout(favorites = 3, historyLimit = 0, expandToFill = true)
    assertTrue(result.iconSizePx > icon)
  }
}
