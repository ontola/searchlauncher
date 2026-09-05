package com.searchlauncher.app.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.text.BreakIterator

/** Replace the selection, including a reversed selection, and discard any old IME composition. */
internal fun TextFieldValue.insertKeyboardText(inserted: String): TextFieldValue {
  val start = selection.min
  val end = selection.max
  return TextFieldValue(text.replaceRange(start, end, inserted), TextRange(start + inserted.length))
}

/** Delete a selected range or the previous character boundary, never half of a surrogate pair. */
internal fun TextFieldValue.deleteKeyboardText(): TextFieldValue {
  if (!selection.collapsed) return insertKeyboardText("")
  val end = selection.start
  if (end == 0) return this
  val boundaries = BreakIterator.getCharacterInstance().also { it.setText(text) }
  val start = boundaries.preceding(end).coerceAtLeast(0)
  return TextFieldValue(text.removeRange(start, end), TextRange(start))
}

/** Preview only when inserting a space at the caret would complete the entire shortcut alias. */
internal fun pendingKeyboardShortcut(
  value: TextFieldValue,
  shortcuts: List<com.searchlauncher.app.data.SearchShortcut>,
): com.searchlauncher.app.data.SearchShortcut? {
  if (
    !value.selection.collapsed || value.selection.end != value.text.length || value.text.isEmpty()
  )
    return null
  return shortcuts.firstOrNull { it.alias.equals(value.text, ignoreCase = true) }
}
