package com.searchlauncher.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.searchlauncher.app.ui.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

@kotlinx.serialization.Serializable
data class WidgetData(val id: Int, val height: Int? = null, val provider: String? = null)

class WidgetRepository(private val context: Context) {
  companion object {
    const val APPWIDGET_HOST_ID = 1002
  }

  private val WIDGETS_KEY = stringPreferencesKey("widgets_data")
  // Legacy key for migration
  private val WIDGET_IDS_KEY = stringPreferencesKey("widget_ids")

  val widgets: Flow<List<WidgetData>> =
    context.dataStore.data.map { preferences ->
      val jsonString = preferences[WIDGETS_KEY]
      if (jsonString != null) {
        try {
          val jsonArray = JSONArray(jsonString)
          val list = mutableListOf<WidgetData>()
          for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
              WidgetData(
                id = obj.getInt("id"),
                height = if (obj.has("height")) obj.getInt("height") else null,
                provider = obj.optString("provider").takeIf { it.isNotEmpty() },
              )
            )
          }
          list
        } catch (e: Exception) {
          emptyList()
        }
      } else {
        // Migration path: Check legacy key
        val legacyJson = preferences[WIDGET_IDS_KEY] ?: "[]"
        val legacyArray = JSONArray(legacyJson)
        val list = mutableListOf<WidgetData>()
        for (i in 0 until legacyArray.length()) {
          list.add(WidgetData(id = legacyArray.getInt(i)))
        }
        // Ideally we should save this back to new key, but we can't emit side effects here easily.
        // We will do it on next write or via a migration scope if needed.
        // For now, this read-compatibility is sufficient.
        list
      }
    }

  // Helper to save
  private suspend fun saveWidgets(list: List<WidgetData>) {
    context.dataStore.edit { preferences ->
      val jsonArray = JSONArray()
      list.forEach { item ->
        val obj = org.json.JSONObject()
        obj.put("id", item.id)
        item.height?.let { obj.put("height", it) }
        item.provider?.let { obj.put("provider", it) }
        jsonArray.put(obj)
      }
      preferences[WIDGETS_KEY] = jsonArray.toString()
      // Clear legacy key to complete migration
      if (preferences.contains(WIDGET_IDS_KEY)) {
        preferences.remove(WIDGET_IDS_KEY)
      }
    }
  }

  suspend fun addWidgetId(appWidgetId: Int) {
    val current = widgets.first()
    val newList = current + WidgetData(appWidgetId, height = 200)
    saveWidgets(newList)
  }

  suspend fun removeWidgetId(appWidgetId: Int) {
    val current = widgets.first()
    val newList = current.filter { it.id != appWidgetId }
    saveWidgets(newList)
  }

  suspend fun replaceWidgetId(oldAppWidgetId: Int, newAppWidgetId: Int, provider: String? = null) {
    val current = widgets.first()
    val newList =
      current.map {
        if (it.id == oldAppWidgetId) {
          it.copy(id = newAppWidgetId, provider = provider ?: it.provider)
        } else {
          it
        }
      }
    saveWidgets(newList)
  }

  suspend fun replaceAll(widgets: List<WidgetData>) {
    saveWidgets(widgets)
  }

  suspend fun clearAllWidgets() {
    context.dataStore.edit { preferences ->
      preferences.remove(WIDGETS_KEY)
      preferences.remove(WIDGET_IDS_KEY)
    }
  }

  suspend fun updateWidgetHeight(appWidgetId: Int, newHeight: Int) {
    val current = widgets.first()
    val newList = current.map { if (it.id == appWidgetId) it.copy(height = newHeight) else it }
    saveWidgets(newList)
  }

  suspend fun moveWidgetUp(appWidgetId: Int) {
    val current = widgets.first().toMutableList()
    val index = current.indexOfFirst { it.id == appWidgetId }
    if (index > 0) {
      val item = current.removeAt(index)
      current.add(index - 1, item)
      saveWidgets(current)
    }
  }

  suspend fun moveWidgetDown(appWidgetId: Int) {
    val current = widgets.first().toMutableList()
    val index = current.indexOfFirst { it.id == appWidgetId }
    if (index != -1 && index < current.size - 1) {
      val item = current.removeAt(index)
      current.add(index + 1, item)
      saveWidgets(current)
    }
  }
}
