package com.searchlauncher.app.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Builds AppSearch documents for calendar instances in the near future.
 *
 * Recurring events are expanded through [CalendarContract.Instances], so "standup every weekday"
 * yields the occurrences that actually fall in the window rather than a single master event.
 * Persisting the documents (and checking READ_CALENDAR) is the caller's responsibility.
 */
class CalendarIndexer(private val context: Context) {

  fun readFingerprint(): String? {
    return try {
      val entries = queryEntries()
      if (entries.isEmpty()) "0/0" else "${entries.size}/${entries.maxOf { it.beginMillis }}"
    } catch (e: Exception) {
      android.util.Log.w(TAG, "Failed to read calendar fingerprint", e)
      null
    }
  }

  suspend fun buildDocuments(pauseCheck: suspend () -> Unit): List<AppSearchDocument> {
    val entries = queryEntries()
    val docs = ArrayList<AppSearchDocument>(entries.size)
    for (entry in entries) {
      pauseCheck()
      docs.add(documentFrom(entry))
    }
    return docs
  }

  internal fun documentFrom(entry: CalendarEntry): AppSearchDocument {
    val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, entry.eventId)
    val subtitle = formatSubtitle(entry)
    val extra = listOfNotNull(entry.location, entry.calendarName).joinToString(" ")
    // The occurrence, not the series. Instances expands a recurring event into the dates it
    // actually falls on, so the event id alone is ambiguous: without the time extras a calendar
    // app has no occurrence to show and either opens the wrong date or nothing at all.
    val intent =
      Intent(Intent.ACTION_VIEW, eventUri).apply {
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, entry.beginMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, entry.endMillis)
      }
    return AppSearchDocument(
      namespace = NAMESPACE,
      id = "${entry.eventId}/${entry.beginMillis}",
      name = entry.title,
      score = 3,
      intentUri = intent.toUri(Intent.URI_INTENT_SCHEME),
      description = "$subtitle${if (extra.isNotEmpty()) " $extra" else ""}",
    )
  }

  private fun queryEntries(): List<CalendarEntry> {
    val now = System.currentTimeMillis()
    val end = now + WINDOW_MS
    val projection =
      arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
      )
    val cursor =
      try {
        CalendarContract.Instances.query(context.contentResolver, projection, now, end)
      } catch (e: SecurityException) {
        android.util.Log.w(TAG, "READ_CALENDAR denied", e)
        null
      } catch (e: Exception) {
        android.util.Log.w(TAG, "Failed querying calendar instances", e)
        null
      } ?: return emptyList()

    val out = ArrayList<CalendarEntry>(MAX_EVENTS)
    cursor.use {
      val idIdx = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
      val titleIdx = it.getColumnIndex(CalendarContract.Instances.TITLE)
      val beginIdx = it.getColumnIndex(CalendarContract.Instances.BEGIN)
      val endIdx = it.getColumnIndex(CalendarContract.Instances.END)
      val allDayIdx = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
      val locationIdx = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
      val calIdx = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
      if (idIdx < 0 || beginIdx < 0) return emptyList()

      while (it.moveToNext() && out.size < MAX_EVENTS) {
        val title = (if (titleIdx >= 0) it.getString(titleIdx) else null)?.trim().orEmpty()
        val eventId = it.getLong(idIdx)
        if (eventId <= 0L) continue
        val begin = it.getLong(beginIdx)
        out.add(
          CalendarEntry(
            eventId = eventId,
            title = title.ifBlank { "(No title)" },
            beginMillis = begin,
            // A zero-length event still needs an end, or Calendar treats the extras as unset.
            endMillis =
              (if (endIdx >= 0 && !it.isNull(endIdx)) it.getLong(endIdx) else 0L).takeIf { e ->
                e > begin
              } ?: (begin + DEFAULT_DURATION_MS),
            allDay = allDayIdx >= 0 && it.getInt(allDayIdx) == 1,
            calendarName =
              if (calIdx >= 0) it.getString(calIdx)?.takeIf { n -> n.isNotBlank() } else null,
            location =
              if (locationIdx >= 0) it.getString(locationIdx)?.takeIf { n -> n.isNotBlank() }
              else null,
          )
        )
      }
    }
    return out
  }

  companion object {
    const val NAMESPACE = "calendar"
    /** Today plus a week, with a little slack so Sunday-evening planning still finds Monday. */
    const val WINDOW_MS = 8L * 24 * 60 * 60 * 1000
    const val MAX_EVENTS = 200
    /** Stand-in span for an instance the provider gave no usable end for. */
    const val DEFAULT_DURATION_MS = 60L * 60 * 1000
    private const val TAG = "CalendarIndexer"

    fun formatSubtitle(entry: CalendarEntry, nowMillis: Long = System.currentTimeMillis()): String {
      val whenText = formatWhen(entry.beginMillis, entry.allDay, nowMillis)
      return listOfNotNull(whenText, entry.location, entry.calendarName).joinToString(" · ")
    }

    fun formatWhen(beginMillis: Long, allDay: Boolean, nowMillis: Long): String {
      val startOfToday = startOfDay(nowMillis)
      val startOfTarget = startOfDay(beginMillis)
      val dayDelta = TimeUnit.MILLISECONDS.toDays(startOfTarget - startOfToday)
      val dayLabel =
        when (dayDelta) {
          0L -> "Today"
          1L -> "Tomorrow"
          else -> {
            val fmt = java.text.SimpleDateFormat("EEE d MMM", Locale.getDefault())
            fmt.format(java.util.Date(beginMillis))
          }
        }
      if (allDay) return "$dayLabel · All day"
      val timeFmt = java.text.SimpleDateFormat("HH:mm", Locale.US)
      return "$dayLabel · ${timeFmt.format(java.util.Date(beginMillis))}"
    }

    private fun startOfDay(millis: Long): Long {
      val cal = Calendar.getInstance()
      cal.timeInMillis = millis
      cal.set(Calendar.HOUR_OF_DAY, 0)
      cal.set(Calendar.MINUTE, 0)
      cal.set(Calendar.SECOND, 0)
      cal.set(Calendar.MILLISECOND, 0)
      return cal.timeInMillis
    }
  }
}

data class CalendarEntry(
  val eventId: Long,
  val title: String,
  val beginMillis: Long,
  val endMillis: Long = beginMillis + CalendarIndexer.DEFAULT_DURATION_MS,
  val allDay: Boolean,
  val calendarName: String?,
  val location: String?,
)
