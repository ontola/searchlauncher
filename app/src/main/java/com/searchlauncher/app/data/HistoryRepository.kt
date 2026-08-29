package com.searchlauncher.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val id: String, val lastUsedMs: Long)

class HistoryRepository(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences(Prefs.History.FILE, Context.MODE_PRIVATE)

  private val _historyIds = MutableStateFlow<List<String>>(emptyList())
  /** Ordered list of recently used ids, newest first. */
  val historyIds: StateFlow<List<String>> = _historyIds

  private val _historyEntries = MutableStateFlow<List<HistoryEntry>>(emptyList())
  val historyEntries: StateFlow<List<HistoryEntry>> = _historyEntries

  init {
    loadHistory()
  }

  private fun loadHistory() {
    val jsonString = prefs.getString(Prefs.History.IDS, null)
    if (jsonString == null) return
    try {
      val array = JSONArray(jsonString)
      val times = loadTimes()
      val now = System.currentTimeMillis()
      val entries = mutableListOf<HistoryEntry>()
      for (i in 0 until array.length()) {
        val id = array.getString(i)
        // Missing times keep list order: each older slot is one second earlier.
        val at = times[id] ?: (now - i * 1000L)
        entries.add(HistoryEntry(id, at))
      }
      publish(entries)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun loadTimes(): Map<String, Long> {
    val raw = prefs.getString(Prefs.History.TIMES, null) ?: return emptyMap()
    return try {
      val obj = JSONObject(raw)
      buildMap { obj.keys().forEach { key -> put(key, obj.optLong(key, 0L)) } }
    } catch (e: Exception) {
      emptyMap()
    }
  }

  /** Records that an app was used. Moves it to the front of the list. */
  fun addHistoryItem(id: String, atMs: Long = System.currentTimeMillis()) {
    val current = _historyEntries.value.toMutableList()
    current.removeAll { it.id == id }
    current.add(0, HistoryEntry(id, atMs))
    publish(current.take(20))
    save()
  }

  private fun publish(entries: List<HistoryEntry>) {
    _historyEntries.value = entries
    _historyIds.value = entries.map { it.id }
  }

  private fun save() {
    val array = JSONArray()
    val times = JSONObject()
    _historyEntries.value.forEach { entry ->
      array.put(entry.id)
      times.put(entry.id, entry.lastUsedMs)
    }
    prefs
      .edit()
      .putString(Prefs.History.IDS, array.toString())
      .putString(Prefs.History.TIMES, times.toString())
      .apply()
  }

  fun clearHistory() {
    publish(emptyList())
    prefs.edit().remove(Prefs.History.IDS).remove(Prefs.History.TIMES).apply()
  }

  fun timesById(): Map<String, Long> = _historyEntries.value.associate { it.id to it.lastUsedMs }
}
