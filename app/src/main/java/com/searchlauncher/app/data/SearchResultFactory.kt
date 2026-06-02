package com.searchlauncher.app.data

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.util.Log
import com.searchlauncher.app.R
import com.searchlauncher.app.SearchLauncherApp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SearchResultFactory(
  private val context: Context,
  private val iconRepository: IconRepository,
  private val iconGenerator: SearchIconGenerator,
) {
  private val defaultContactIcon: Drawable by lazy {
    context.getDrawable(R.drawable.ic_contact_default)!!.apply { setTint(Color.GRAY) }
  }

  suspend fun create(
    sdoc: SearchableDocument,
    rankingScore: Int,
    query: String? = null,
    saveToDisk: Boolean = false,
    allowIpc: Boolean = true,
    allowDisk: Boolean = true,
  ): SearchResult {
    val doc = sdoc.doc
    currentCoroutineContext().ensureActive()
    return when (doc.namespace) {
      "shortcuts" -> createShortcutResult(sdoc, rankingScore, saveToDisk, allowIpc, allowDisk)
      "app_shortcuts" ->
        createAppShortcutResult(sdoc, rankingScore, saveToDisk, allowIpc, allowDisk)
      "search_shortcuts" -> createSearchShortcutResult(sdoc, rankingScore)
      "web_bookmarks" -> createWebBookmarkResult(sdoc, rankingScore)
      "static_shortcuts" ->
        createStaticShortcutResult(sdoc, rankingScore, saveToDisk, allowIpc, allowDisk)
      "contacts" -> createContactResult(sdoc, rankingScore, query, allowIpc)
      "snippets" -> createSnippetResult(sdoc, rankingScore)
      else -> createAppResult(sdoc, rankingScore, saveToDisk, allowIpc, allowDisk)
    }
  }

  private fun createShortcutResult(
    sdoc: SearchableDocument,
    rankingScore: Int,
    saveToDisk: Boolean,
    allowIpc: Boolean,
    allowDisk: Boolean,
  ): SearchResult.Shortcut {
    val doc = sdoc.doc
    val pkg = sdoc.packageName ?: ""
    val icon = loadShortcutIcon(sdoc, saveToDisk, allowIpc, allowDisk)
    val appIcon = loadAppIcon(pkg, saveToDisk, allowIpc, allowDisk)

    return SearchResult.Shortcut(
      id = doc.id,
      namespace = "shortcuts",
      title = doc.name,
      subtitle = doc.description ?: "Shortcut",
      icon = icon,
      packageName = pkg,
      intentUri = doc.intentUri ?: "",
      appIcon = appIcon,
      rankingScore = rankingScore,
    )
  }

  private fun createAppShortcutResult(
    sdoc: SearchableDocument,
    rankingScore: Int,
    saveToDisk: Boolean,
    allowIpc: Boolean,
    allowDisk: Boolean,
  ): SearchResult.Content {
    val doc = sdoc.doc
    val icon = loadAppShortcutIcon(doc, saveToDisk, allowIpc, allowDisk)
    val isLauncherItem = doc.id.contains("launcher_")
    val launcherIcon =
      if (isLauncherItem) {
        loadAppIcon(
          context.packageName,
          saveToDisk,
          allowIpc,
          allowDisk,
          cacheKey = "launcher_app_icon",
        )
      } else {
        null
      }

    return SearchResult.Content(
      id = doc.id,
      namespace = "app_shortcuts",
      title = doc.name,
      subtitle = if (isLauncherItem) "SearchLauncher" else "Action",
      icon = launcherIcon ?: icon,
      packageName = "android",
      deepLink = doc.intentUri,
      rankingScore = rankingScore,
    )
  }

  private fun createSearchShortcutResult(
    sdoc: SearchableDocument,
    rankingScore: Int,
  ): SearchResult.SearchIntent {
    val doc = sdoc.doc
    val alias = doc.description ?: ""
    val cacheKey = "search_shortcut_${doc.id}"
    var icon = iconRepository.getMemory(cacheKey)

    if (icon == null) {
      val app = context.applicationContext as? SearchLauncherApp
      val shortcutDef = app?.searchShortcutRepository?.items?.value?.find { it.alias == alias }
      icon = iconGenerator.getColoredSearchIcon(shortcutDef?.color, shortcutDef?.alias)
      if (icon != null) {
        iconRepository.putMemory(cacheKey, icon)
      }
    }

    return SearchResult.SearchIntent(
      id = doc.id,
      namespace = "search_shortcuts",
      title = doc.name,
      subtitle = "Type '${doc.description} ' to search",
      icon = icon,
      trigger = doc.description ?: "",
      rankingScore = rankingScore,
    )
  }

  private fun createWebBookmarkResult(
    sdoc: SearchableDocument,
    rankingScore: Int,
  ): SearchResult.Content {
    val doc = sdoc.doc
    val browserIcon =
      context.getDrawable(android.R.drawable.ic_menu_compass)
        ?: context.getDrawable(android.R.drawable.ic_menu_search)

    return SearchResult.Content(
      id = doc.id,
      namespace = "web_bookmarks",
      title = doc.name,
      subtitle = "Bookmark",
      icon = browserIcon,
      packageName = "com.android.chrome",
      deepLink = doc.intentUri,
      rankingScore = rankingScore,
    )
  }

  private fun createStaticShortcutResult(
    sdoc: SearchableDocument,
    rankingScore: Int,
    saveToDisk: Boolean,
    allowIpc: Boolean,
    allowDisk: Boolean,
  ): SearchResult.Shortcut {
    val doc = sdoc.doc
    val pkg = sdoc.packageName ?: ""
    val icon = loadStaticShortcutIcon(sdoc, saveToDisk, allowIpc, allowDisk)
    val appIcon =
      if (allowIpc) runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
      else null

    return SearchResult.Shortcut(
      id = doc.id,
      namespace = "static_shortcuts",
      title = doc.name,
      subtitle = doc.description ?: "Shortcut",
      icon = icon,
      packageName = pkg,
      intentUri = doc.intentUri ?: "",
      appIcon = appIcon,
      rankingScore = rankingScore,
    )
  }

  private fun createContactResult(
    sdoc: SearchableDocument,
    rankingScore: Int,
    query: String?,
    allowIpc: Boolean,
  ): SearchResult.Contact {
    val doc = sdoc.doc
    val lookupKey = sdoc.contactLookupKey ?: ""
    val contactId = sdoc.contactId
    val photoUri = sdoc.photoUri
    val icon = loadContactIcon(doc.id, photoUri, allowIpc)

    return SearchResult.Contact(
      id = doc.id,
      namespace = "contacts",
      title = doc.name,
      subtitle = contactSubtitle(sdoc, query),
      icon = icon,
      rankingScore = rankingScore,
      lookupKey = lookupKey,
      contactId = contactId,
      photoUri = photoUri,
    )
  }

  private fun createSnippetResult(
    sdoc: SearchableDocument,
    rankingScore: Int,
  ): SearchResult.Snippet {
    val doc = sdoc.doc
    val icon = context.getDrawable(android.R.drawable.ic_menu_save)
    return SearchResult.Snippet(
      id = doc.id,
      namespace = "snippets",
      title = doc.name,
      subtitle = doc.description ?: "Snippet",
      icon = icon,
      alias = doc.id,
      content = doc.description ?: "",
      rankingScore = rankingScore,
    )
  }

  private fun createAppResult(
    sdoc: SearchableDocument,
    rankingScore: Int,
    saveToDisk: Boolean,
    allowIpc: Boolean,
    allowDisk: Boolean,
  ): SearchResult.App {
    val doc = sdoc.doc
    val packageName = doc.id
    val icon = loadAppIcon(packageName, saveToDisk, allowIpc, allowDisk)

    return SearchResult.App(
      id = doc.id,
      namespace = "apps",
      title = doc.name,
      subtitle = doc.description ?: doc.id,
      icon = icon,
      packageName = doc.id,
      rankingScore = rankingScore,
    )
  }

  private fun loadShortcutIcon(
    sdoc: SearchableDocument,
    saveToDisk: Boolean,
    allowIpc: Boolean,
    allowDisk: Boolean,
  ): Drawable? {
    val doc = sdoc.doc
    val cacheKey = "shortcut_${doc.id}"
    return loadCachedIcon(cacheKey, allowDisk)
      ?: if (allowIpc) loadShortcutIconViaLauncherApps(sdoc, cacheKey, saveToDisk) else null
  }

  private fun loadShortcutIconViaLauncherApps(
    sdoc: SearchableDocument,
    cacheKey: String,
    saveToDisk: Boolean,
  ): Drawable? =
    try {
      val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
      val query = LauncherApps.ShortcutQuery()
      query.setPackage(sdoc.packageName ?: "")
      query.setShortcutIds(listOf(sdoc.doc.id.substringAfter("/")))
      query.setQueryFlags(
        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
          LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
          LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
      )
      val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
      val icon =
        shortcuts
          ?.takeIf { it.isNotEmpty() }
          ?.let {
            launcherApps.getShortcutIconDrawable(it[0], context.resources.displayMetrics.densityDpi)
          }
      icon?.also { cacheIcon(cacheKey, it, saveToDisk) }
    } catch (e: Exception) {
      null
    }

  private fun loadAppShortcutIcon(
    doc: AppSearchDocument,
    saveToDisk: Boolean,
    allowIpc: Boolean,
    allowDisk: Boolean,
  ): Drawable? {
    val cacheKey = "app_shortcut_${doc.id}"
    return loadCachedIcon(cacheKey, allowDisk)
      ?: if (allowIpc) {
        val icon =
          when {
            doc.intentUri?.contains("android.settings") == true ->
              runCatching { context.packageManager.getApplicationIcon("com.android.settings") }
                .getOrNull()
            doc.intentUri?.contains("STILL_IMAGE_CAMERA") == true ->
              context.getDrawable(android.R.drawable.ic_menu_camera)
            doc.intentUri?.contains("VIDEO_CAMERA") == true ->
              context.getDrawable(android.R.drawable.ic_menu_camera)
            else -> null
          }
        icon?.also { cacheIcon(cacheKey, it, saveToDisk) }
      } else {
        null
      }
  }

  private fun loadStaticShortcutIcon(
    sdoc: SearchableDocument,
    saveToDisk: Boolean,
    allowIpc: Boolean,
    allowDisk: Boolean,
  ): Drawable? {
    val doc = sdoc.doc
    val cacheKey = "static_shortcut_${doc.id}"
    return loadCachedIcon(cacheKey, allowDisk)
      ?: if (allowIpc) {
        try {
          val pkg = sdoc.packageName ?: ""
          if (doc.iconResId > 0) {
            context.packageManager
              .getResourcesForApplication(pkg)
              .getDrawable(doc.iconResId.toInt(), null)
              .also { cacheIcon(cacheKey, it, saveToDisk) }
          } else {
            null
          }
        } catch (e: Exception) {
          null
        }
      } else {
        null
      }
  }

  private fun loadContactIcon(id: String, photoUri: String?, allowIpc: Boolean): Drawable? {
    val cacheKey = "contact_$id"
    var icon = iconRepository.getMemory(cacheKey)

    if (icon == null && photoUri != null && allowIpc) {
      try {
        val uri = Uri.parse(photoUri)
        val stream = context.contentResolver.openInputStream(uri)
        icon = Drawable.createFromStream(stream, uri.toString())
        stream?.close()
        if (icon != null) {
          iconRepository.putMemory(cacheKey, icon)
        }
      } catch (e: Exception) {
        // Ignore invalid or inaccessible contact photos.
      }
    }

    return icon ?: if (allowIpc) defaultContactIcon else null
  }

  private fun loadAppIcon(
    packageName: String,
    saveToDisk: Boolean,
    allowIpc: Boolean,
    allowDisk: Boolean,
    cacheKey: String = "appicon_$packageName",
  ): Drawable? =
    loadCachedIcon(cacheKey, allowDisk)
      ?: if (allowIpc) {
        try {
          context.packageManager.getApplicationIcon(packageName).also {
            cacheIcon(cacheKey, it, saveToDisk)
          }
        } catch (e: Exception) {
          Log.w("SearchResultFactory", "Failed to load app icon on demand for $packageName")
          null
        }
      } else {
        null
      }

  private fun loadCachedIcon(cacheKey: String, allowDisk: Boolean): Drawable? {
    iconRepository.getMemory(cacheKey)?.let {
      return it
    }
    if (!allowDisk) return null
    return iconRepository.loadFromDisk(cacheKey)?.also { iconRepository.putMemory(cacheKey, it) }
  }

  private fun cacheIcon(cacheKey: String, icon: Drawable, saveToDisk: Boolean) {
    iconRepository.putMemory(cacheKey, icon)
    if (saveToDisk) {
      iconRepository.saveToDisk(cacheKey, icon, force = true)
    }
  }

  private fun contactSubtitle(sdoc: SearchableDocument, query: String?): String {
    val doc = sdoc.doc
    if (query == null || doc.name.contains(query, ignoreCase = true)) {
      return "Contact"
    }

    val tokens = (sdoc.phoneNumbers ?: "").split(" ")
    return tokens.find { it.contains(query, ignoreCase = true) }?.trim() ?: "Contact"
  }
}
