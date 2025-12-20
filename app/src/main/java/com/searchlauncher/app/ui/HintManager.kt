package com.searchlauncher.app.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class Hint(val text: String, val condition: () -> Boolean = { true })

class HintManager(
  private val isWallpaperFolderSet: () -> Boolean,
  private val isSnippetsSet: () -> Boolean,
  private val isDefaultLauncher: () -> Boolean,
  private val isContactsAccessGranted: () -> Boolean,
  private val shortcutHints: List<String> = emptyList(),
) {
  private val hints =
    listOf(
      Hint("Search anything"),
      Hint("This could be your home screen") { !isDefaultLauncher() },
      Hint("Set a folder with wallpapers and rotate wallpapers") { !isWallpaperFolderSet() },
      Hint("Give access to contacts to search them") { !isContactsAccessGranted() },
      Hint("Type a phone number and call"),
      Hint("Type a website URL to open"),
      Hint("Try some maths: 1+1, 2^2, 2*2, 2/2, 2%2"),
      Hint("Swipe up to open the app drawer"),
      Hint("Swipe down left to open notifications"),
      Hint("Swipe down right to open quick settings"),
      Hint("Hold search results for more options"),
      Hint("Set custom snippets in settings") { !isSnippetsSet() },
    ) + shortcutHints.map { Hint(it) }

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
