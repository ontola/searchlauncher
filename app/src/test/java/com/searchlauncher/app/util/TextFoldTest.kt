package com.searchlauncher.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFoldTest {
  @Test
  fun `strips Latin accents`() {
    assertEquals("telephone", TextFold.fold("téléphone"))
    assertEquals("cafe creme", TextFold.fold("café crème"))
    assertEquals("ijsbaan", TextFold.fold("ĳsbaan"))
    assertEquals("zurich", TextFold.fold("zürich"))
    assertEquals("ceske", TextFold.fold("české"))
  }

  @Test
  fun `expands letters that are not decomposable`() {
    assertEquals("strasse", TextFold.fold("straße"))
    assertEquals("aero", TextFold.fold("æro"))
    assertEquals("oslo", TextFold.fold("øslo"))
    assertEquals("lodz", TextFold.fold("łódź"))
  }

  @Test
  fun `leaves plain text and other scripts alone`() {
    assertEquals("telegram", TextFold.fold("telegram"))
    assertEquals("ガラス", TextFold.fold("ガラス"))
    assertEquals("한국어", TextFold.fold("한국어"))
    assertEquals("телефон", TextFold.fold("телефон"))
  }

  @Test
  fun `contains ignores case and accents both ways`() {
    assertTrue(TextFold.containsFolded("Téléphone", "tel"))
    assertTrue(TextFold.containsFolded("Telegram", "TÉL"))
    assertFalse(TextFold.containsFolded("Telegram", "tex"))
  }
}
