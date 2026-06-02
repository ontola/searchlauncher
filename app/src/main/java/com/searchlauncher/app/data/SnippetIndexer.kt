package com.searchlauncher.app.data

import android.content.Context
import com.searchlauncher.app.SearchLauncherApp

/**
 * Builds AppSearch documents for user-defined text snippets.
 *
 * Snippets are launched via the clipboard (see SearchResultItem), so [AppSearchDocument.intentUri]
 * carries a `snippet://` marker rather than a real launch intent.
 */
class SnippetIndexer(private val context: Context) {

  /** Returns one document per snippet, or an empty list if the app context is unavailable. */
  fun buildDocuments(): List<AppSearchDocument> {
    val app = context.applicationContext as? SearchLauncherApp ?: return emptyList()
    return app.snippetsRepository.items.value.map { snippet ->
      AppSearchDocument(
        namespace = "snippets",
        id = snippet.alias,
        name = snippet.alias,
        description = snippet.content,
        score = 5,
        intentUri = "snippet://${snippet.alias}",
        isAction = true,
      )
    }
  }
}
