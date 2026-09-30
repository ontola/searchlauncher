package com.searchlauncher.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The search shortcuts SearchLauncher knows about, read from `search_shortcuts.json`.
 *
 * The list lives in a data file rather than in code so that adding a site or app is a small,
 * reviewable edit anyone can make. Most entries are [onlyWhenInstalled]: they stay out of the way
 * until the user has that app, so a long list of regional shops and niche apps costs nobody
 * anything.
 */
object ShortcutCatalog {
  const val RESOURCE = "/search_shortcuts.json"

  data class Entry(
    val shortcut: SearchShortcut,
    /**
     * Apps this shortcut searches inside. Their icon stands in for the letter tile, and for an
     * [onlyWhenInstalled] entry, having any of them installed is what switches it on.
     */
    val apps: List<String>,
    /** Offered only once one of [apps] (or [SearchShortcut.packageName]) is installed. */
    val onlyWhenInstalled: Boolean,
    /**
     * Keys to try, best first. [SearchShortcut.alias] is the first of them. When the shortcut joins
     * the user's list, it gets the first one nobody else uses, and keeps it from then on.
     */
    val aliases: List<String> = listOf(shortcut.alias),
  ) {
    /** Every package that counts as "the app" for this entry. */
    val packages: List<String>
      get() = (listOfNotNull(shortcut.packageName) + apps).distinct()
  }

  val entries: List<Entry> by lazy { load() }

  private fun load(): List<Entry> {
    val stream =
      ShortcutCatalog::class.java.getResourceAsStream(RESOURCE)
        ?: error("$RESOURCE is missing from the app")
    return parse(stream.bufferedReader().use { it.readText() })
  }

  fun parse(json: String): List<Entry> {
    val array = JSONObject(json).getJSONArray("shortcuts")
    return List(array.length()) { i -> parseEntry(array.getJSONObject(i)) }
  }

  private fun parseEntry(obj: JSONObject): Entry {
    // "aliases" lists keys best first, for shortcuts that pick theirs on arrival; "alias" is the
    // one fixed key of a shortcut everyone starts with.
    val aliases = obj.optJSONArray("aliases")?.strings() ?: listOf(obj.getString("alias"))
    return Entry(
      shortcut =
        SearchShortcut(
          id = obj.getString("id"),
          alias = aliases.first(),
          urlTemplate = obj.getString("urlTemplate"),
          description = obj.getString("description"),
          packageName = obj.optString("packageName").takeIf { it.isNotEmpty() },
          suggestionUrl = obj.optString("suggestionUrl").takeIf { it.isNotEmpty() },
          color = obj.optString("color").takeIf { it.isNotEmpty() }?.let(::parseColor),
          shortLabel = obj.optString("shortLabel").takeIf { it.isNotEmpty() },
        ),
      apps = obj.optJSONArray("apps")?.strings().orEmpty(),
      onlyWhenInstalled = obj.optBoolean("onlyWhenInstalled", false),
      aliases = aliases,
    )
  }

  /** `#RRGGBB` as an opaque ARGB long, the form [SearchShortcut.color] has always stored. */
  fun parseColor(hex: String): Long {
    val digits = hex.removePrefix("#")
    require(digits.length == 6 && digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
      "Colour must look like #RRGGBB, got $hex"
    }
    return 0xFF000000L or digits.toLong(16)
  }

  private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
}
