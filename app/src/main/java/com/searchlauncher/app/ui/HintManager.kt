package com.searchlauncher.app.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class Hint(val text: String, val condition: () -> Boolean = { true })

class HintManager(
  private val isWallpaperFolderSet: () -> Boolean,
  private val isQuickCopySet: () -> Boolean,
) {
  private val hints =
    listOf(
      Hint("Search anything"),
      Hint("Set a folder with wallpapers and rotate wallpapers") { !isWallpaperFolderSet() },
      Hint("Type a phone number and call"),
      Hint("Type a website URL to open"),
      Hint("Swipe down left to open notifications"),
      Hint("Swipe down right to open quick settings"),
      Hint("Set custom snippets with QuickCopy") { !isQuickCopySet() },
      Hint("Press 'y ' to search youtube"),
      Hint("Press 'm ' to search maps"),
      Hint("Press 'c ' to ask ChatGPT"),
    )

  fun getHintsFlow(): Flow<String> = flow {
    var index = 0
    while (true) {
      val validHints = hints.filter { it.condition() }
      if (validHints.isNotEmpty()) {
        emit(validHints[index % validHints.size].text)
        index++
      }
      delay(4000) // Rotate every 4 seconds
    }
  }
}
