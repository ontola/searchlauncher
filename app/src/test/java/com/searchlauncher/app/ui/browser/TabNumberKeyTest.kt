package com.searchlauncher.app.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabNumberKeyTest {

  @Test
  fun digitsOneToEightArePositions() {
    assertEquals(0, tabIndexForNumberKey(0, 5))
    assertEquals(4, tabIndexForNumberKey(4, 5))
  }

  @Test
  fun nineIsAlwaysTheLastTab() {
    assertEquals(2, tabIndexForNumberKey(8, 3))
    assertEquals(11, tabIndexForNumberKey(8, 12))
    assertEquals(0, tabIndexForNumberKey(8, 1))
  }

  @Test
  fun aPositionPastTheEndNamesNoTab() {
    assertNull(tabIndexForNumberKey(4, 3))
    assertNull(tabIndexForNumberKey(0, 0))
    assertNull(tabIndexForNumberKey(8, 0))
  }
}
