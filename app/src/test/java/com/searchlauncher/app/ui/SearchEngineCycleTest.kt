package com.searchlauncher.app.ui

import com.searchlauncher.app.data.SearchShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchEngineCycleTest {

  private val engines =
    listOf(engine("google"), engine("youtube"), engine("wikipedia"), engine("ddg"))

  @Test
  fun tabWalksForwardAndShiftTabBack() {
    assertEquals("youtube", cycleSearchEngineId(engines, "google", 1))
    assertEquals("wikipedia", cycleSearchEngineId(engines, "youtube", 1))
    assertEquals("youtube", cycleSearchEngineId(engines, "wikipedia", -1))
  }

  @Test
  fun wrapsAroundBothEnds() {
    assertEquals("google", cycleSearchEngineId(engines, "ddg", 1))
    assertEquals("ddg", cycleSearchEngineId(engines, "google", -1))
  }

  @Test
  fun startsFromTheFirstEngineWhenTheCurrentOneIsGone() {
    // A shortcut can be removed while its id is still the stored default.
    assertEquals("youtube", cycleSearchEngineId(engines, "deleted", 1))
    assertEquals("ddg", cycleSearchEngineId(engines, "deleted", -1))
  }

  @Test
  fun nothingToWalkLeavesTheBadgeAlone() {
    assertNull(cycleSearchEngineId(emptyList(), "google", 1))
  }

  @Test
  fun aSingleEngineStaysPut() {
    assertEquals("google", cycleSearchEngineId(listOf(engine("google")), "google", 1))
  }

  private fun engine(id: String) =
    SearchShortcut(
      id = id,
      alias = id.first().toString(),
      urlTemplate = "https://example.com/?q=%s",
      description = id,
    )
}
