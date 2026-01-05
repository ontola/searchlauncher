package com.searchlauncher.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

class HistoryRepository(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("history", Context.MODE_PRIVATE)

  private val _historyIds = MutableStateFlow<List<String>>(emptyList())
  val historyIds: StateFlow<List<String>> = _historyIds

  init {
    loadHistory()
  }

  private fun loadHistory() {
    val jsonString = prefs.getString("history_ids", null)
    if (jsonString != null) {
      try {
        val array = JSONArray(jsonString)
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
          list.add(array.getString(i))
        }
        _historyIds.value = list
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  /** Records that an app was used. Moves it to the front of the list. */
  fun addHistoryItem(id: String) {
    val currentHistory = _historyIds.value.toMutableList()
    // Remove if already exists so we can move it to front
    currentHistory.remove(id)
    currentHistory.add(0, id)

    // Limit total history size
    val truncatedHistory = currentHistory.take(20)

    _historyIds.value = truncatedHistory
    saveHistory(truncatedHistory)
  }

  fun removeHistoryItem(id: String) {
    val currentHistory = _historyIds.value.toMutableList()
    if (currentHistory.remove(id)) {
      _historyIds.value = currentHistory
      saveHistory(currentHistory)
    }
  }

  private fun saveHistory(history: List<String>) {
    val array = JSONArray()
    history.forEach { array.put(it) }
    prefs.edit().putString("history_ids", array.toString()).apply()
  }

  fun clearHistory() {
    _historyIds.value = emptyList()
    prefs.edit().remove("history_ids").apply()
  }
}
