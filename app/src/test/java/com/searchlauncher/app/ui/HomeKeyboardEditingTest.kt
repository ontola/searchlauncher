package com.searchlauncher.app.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeKeyboardEditingTest {
  @Test
  fun spacePreviewRequiresExactAliasAndCaretAtEnd() {
    val youtube =
      com.searchlauncher.app.data.SearchShortcut(
        "youtube",
        "y",
        "https://youtube.com/?q=%s",
        "YouTube",
      )
    val multi = youtube.copy(id = "gemini", alias = "gem")
    val shortcuts = listOf(youtube, multi)
    assertEquals(youtube, pendingKeyboardShortcut(TextFieldValue("y", TextRange(1)), shortcuts))
    assertEquals(youtube, pendingKeyboardShortcut(TextFieldValue("Y", TextRange(1)), shortcuts))
    assertEquals(multi, pendingKeyboardShortcut(TextFieldValue("gem", TextRange(3)), shortcuts))
    assertNull(pendingKeyboardShortcut(TextFieldValue("ge", TextRange(2)), shortcuts))
    assertNull(pendingKeyboardShortcut(TextFieldValue("yellow", TextRange(6)), shortcuts))
    assertNull(pendingKeyboardShortcut(TextFieldValue("y ", TextRange(2)), shortcuts))
    assertNull(pendingKeyboardShortcut(TextFieldValue("y", TextRange(0)), shortcuts))
    assertNull(pendingKeyboardShortcut(TextFieldValue("y", TextRange(0, 1)), shortcuts))
    assertNull(pendingKeyboardShortcut(TextFieldValue("y", TextRange(1)), emptyList()))
  }

  @Test
  fun insertionReplacesReversedSelectionAndClearsComposition() {
    val before = TextFieldValue("hello world", TextRange(11, 6), TextRange(6, 11))
    val after = before.insertKeyboardText("Joep")
    assertEquals("hello Joep", after.text)
    assertEquals(TextRange(10), after.selection)
    assertNull(after.composition)
  }

  @Test
  fun insertionUsesCursorInsteadOfAppending() {
    val after = TextFieldValue("cats", TextRange(1)).insertKeyboardText("o")
    assertEquals("coats", after.text)
    assertEquals(TextRange(2), after.selection)
  }

  @Test
  fun backspaceDeletesSelection() {
    val after = TextFieldValue("abcdef", TextRange(4, 1)).deleteKeyboardText()
    assertEquals("aef", after.text)
    assertEquals(TextRange(1), after.selection)
  }

  @Test
  fun backspacePreservesTextAfterCursorAndDeletesWholeEmoji() {
    val after = TextFieldValue("a😀b", TextRange(3)).deleteKeyboardText()
    assertEquals("ab", after.text)
    assertEquals(TextRange(1), after.selection)
  }

  @Test
  fun backspaceDeletesCombiningAccentWithLetter() {
    val after = TextFieldValue("e\u0301", TextRange(2)).deleteKeyboardText()
    assertEquals("", after.text)
    assertEquals(TextRange(0), after.selection)
  }

  @Test
  fun backspaceAtStartDoesNothing() {
    val before = TextFieldValue("hello", TextRange(0))
    assertEquals(before, before.deleteKeyboardText())
    assertEquals(TextFieldValue(""), TextFieldValue("").deleteKeyboardText())
  }
}
