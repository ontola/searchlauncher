package com.searchlauncher.app.ui.components

import androidx.compose.ui.unit.dp
import com.searchlauncher.app.data.WidgetData
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetColumnsTest {

  private fun widgets(vararg heights: Int) = heights.map { WidgetData(id = it, height = it) }

  @Test
  fun `a phone keeps one column`() {
    val all = widgets(200, 200, 200)
    assertEquals(listOf(all), packWidgetsIntoColumns(all, columnCount = 1, columnHeight = 100.dp))
  }

  @Test
  fun `fills a column before starting the next`() {
    val all = widgets(200, 200, 200)
    val columns = packWidgetsIntoColumns(all, columnCount = 3, columnHeight = 450.dp)
    // 200 + 4 + 200 fits in 450; adding the third would not.
    assertEquals(listOf(all.take(2), all.drop(2)), columns)
  }

  @Test
  fun `leftovers stay in the last column rather than being dropped`() {
    val all = widgets(200, 200, 200, 200, 200)
    val columns = packWidgetsIntoColumns(all, columnCount = 2, columnHeight = 250.dp)
    assertEquals(2, columns.size)
    assertEquals(all.size, columns.sumOf { it.size })
    // One per column until the columns run out; the rest pile into the last one, which scrolls.
    assertEquals(listOf(all[0]), columns[0])
    assertEquals(all.drop(1), columns[1])
  }

  @Test
  fun `a widget taller than a column still gets placed`() {
    val all = widgets(900)
    val columns = packWidgetsIntoColumns(all, columnCount = 3, columnHeight = 400.dp)
    assertEquals(listOf(all), columns)
  }

  @Test
  fun `no widgets is one empty column`() {
    assertEquals(
      listOf(emptyList<WidgetData>()),
      packWidgetsIntoColumns(emptyList(), columnCount = 3, columnHeight = 400.dp),
    )
  }

  @Test
  fun `a widget with no stored height is assumed to be the default`() {
    val all = listOf(WidgetData(id = 1), WidgetData(id = 2))
    // Two defaults do not fit in one column of 250, so the second moves across.
    val columns = packWidgetsIntoColumns(all, columnCount = 2, columnHeight = 250.dp)
    assertEquals(listOf(listOf(all[0]), listOf(all[1])), columns)
  }

  @Test
  fun `widget list uses the measured favorites and chrome height`() {
    // One-row guess was 80.dp; two rows of 48.dp icons plus a 56.dp chrome bar is taller.
    val twoRowSection = 48.dp + 8.dp + 48.dp + 56.dp
    assertEquals(
      160.dp + 12.dp,
      widgetsListBottomPadding(keyboardInset = 0.dp, bottomSection = twoRowSection),
    )
    assertEquals(
      300.dp + 160.dp + 12.dp,
      widgetsListBottomPadding(keyboardInset = 300.dp, bottomSection = twoRowSection),
    )
  }

  @Test
  fun `widget list keeps the one-row reserve until the bar has been measured`() {
    assertEquals(80.dp, widgetsListBottomPadding(keyboardInset = 0.dp, bottomSection = 0.dp))
    assertEquals(380.dp, widgetsListBottomPadding(keyboardInset = 300.dp, bottomSection = 0.dp))
  }
}
