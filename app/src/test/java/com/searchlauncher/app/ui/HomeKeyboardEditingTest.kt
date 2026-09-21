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
  fun cursorSwipesPreserveCharactersAndClampAtTextEdges() {
    val value = TextFieldValue("a😀e\u0301b", TextRange(6))
    assertEquals(TextRange(5), value.moveKeyboardCursor(-1).selection)
    assertEquals(TextRange(3), value.moveKeyboardCursor(-2).selection)
    assertEquals(TextRange(1), value.moveKeyboardCursor(-3).selection)
    assertEquals(TextRange(0), value.moveKeyboardCursor(-20).selection)
    assertEquals(TextRange(6), value.moveKeyboardCursor(20).selection)
    assertEquals(value.text, value.moveKeyboardCursor(-2).text)
  }

  @Test
  fun externalTextDropsImeCompositionThatCopyKeeps() {
    val composing = TextFieldValue("hello", TextRange(5), TextRange(0, 5))
    // copy clamps a range that no longer fits, but it is still a composition. On a longer
    // replacement the original range is kept whole. Either way the IME session stays open.
    assertEquals(TextRange(0, 0), composing.copy(text = "", selection = TextRange(0)).composition)
    assertEquals(
      TextRange(0, 5),
      composing.copy(text = "widgets ", selection = TextRange(8)).composition,
    )

    val cleared = composing.applyExternalText("")
    assertEquals("", cleared.text)
    assertEquals(TextRange(0), cleared.selection)
    assertNull(cleared.composition)

    val replaced = composing.applyExternalText("widgets ")
    assertEquals("widgets ", replaced.text)
    assertEquals(TextRange("widgets ".length), replaced.selection)
    assertNull(replaced.composition)
  }

  @Test
  fun externalTextKeepsCaretWhenTheQueryDidNotChange() {
    val caret = TextFieldValue("hello", TextRange(2), TextRange(0, 5))
    assertEquals(caret, caret.applyExternalText("hello"))
  }

  @Test
  fun backspaceAtStartDoesNothing() {
    val before = TextFieldValue("hello", TextRange(0))
    assertEquals(before, before.deleteKeyboardText())
    assertEquals(TextFieldValue(""), TextFieldValue("").deleteKeyboardText())
  }
}
