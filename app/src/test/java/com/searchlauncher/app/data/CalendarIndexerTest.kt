package com.searchlauncher.app.data

import android.content.Intent
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import java.util.Calendar
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CalendarIndexerTest {

  @Test
  fun formatWhen_todayTomorrowAndAllDay() {
    val now = startOfDay() + TimeUnit.HOURS.toMillis(10)
    val todayAfternoon = startOfDay() + TimeUnit.HOURS.toMillis(15)
    val tomorrowMorning = startOfDay() + TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(9)

    assertEquals("Today · 15:00", CalendarIndexer.formatWhen(todayAfternoon, allDay = false, now))
    assertEquals(
      "Tomorrow · 09:00",
      CalendarIndexer.formatWhen(tomorrowMorning, allDay = false, now),
    )
    assertEquals("Today · All day", CalendarIndexer.formatWhen(startOfDay(), allDay = true, now))
  }

  @Test
  fun windowCoversAboutAWeek() {
    val days = TimeUnit.MILLISECONDS.toDays(CalendarIndexer.WINDOW_MS)
    assertEquals(8L, days)
  }

  @Test
  fun documentFrom_namesTheOccurrenceNotJustTheSeries() {
    val indexer = CalendarIndexer(ApplicationProvider.getApplicationContext())
    val begin = startOfDay() + TimeUnit.HOURS.toMillis(9)
    val end = begin + TimeUnit.MINUTES.toMillis(30)
    val doc =
      indexer.documentFrom(
        CalendarEntry(
          eventId = 7L,
          title = "Standup",
          beginMillis = begin,
          endMillis = end,
          allDay = false,
          calendarName = "Work",
          location = null,
        )
      )

    // Recurring events are indexed per occurrence, so the intent has to carry the times or a
    // calendar app has no way to know which one to open.
    val intent = Intent.parseUri(doc.intentUri!!, Intent.URI_INTENT_SCHEME)
    assertEquals(Intent.ACTION_VIEW, intent.action)
    assertEquals(begin, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L))
    assertEquals(end, intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L))
    assertTrue(intent.data.toString().endsWith("/7"))
  }

  @Test
  fun documentFrom_substitutesAnEndForAnInstanceWithoutOne() {
    val indexer = CalendarIndexer(ApplicationProvider.getApplicationContext())
    val begin = startOfDay() + TimeUnit.HOURS.toMillis(11)
    val doc =
      indexer.documentFrom(
        CalendarEntry(
          eventId = 9L,
          title = "Reminder",
          beginMillis = begin,
          allDay = false,
          calendarName = null,
          location = null,
        )
      )

    val intent = Intent.parseUri(doc.intentUri!!, Intent.URI_INTENT_SCHEME)
    val end = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L)
    assertTrue("end should follow begin, was $end", end > begin)
  }

  @Test
  fun documentFrom_opensTheEventAndKeepsLocationSearchable() {
    val indexer = CalendarIndexer(ApplicationProvider.getApplicationContext())
    val begin = startOfDay() + TimeUnit.HOURS.toMillis(14)
    val doc =
      indexer.documentFrom(
        CalendarEntry(
          eventId = 99L,
          title = "Dentist",
          beginMillis = begin,
          allDay = false,
          calendarName = "Personal",
          location = "Utrecht",
        )
      )
    assertEquals("calendar", doc.namespace)
    assertEquals("99/$begin", doc.id)
    assertEquals("Dentist", doc.name)
    assertTrue(doc.intentUri!!.contains("99"))
    assertTrue(doc.description!!.contains("Utrecht"))
    assertTrue(doc.description!!.contains("Personal"))
  }

  private fun startOfDay(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
  }
}
