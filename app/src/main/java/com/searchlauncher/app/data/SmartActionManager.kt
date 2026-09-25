package com.searchlauncher.app.data

import android.content.Context
import com.searchlauncher.app.util.webAddressUrl

class SmartActionManager(private val context: Context) {

  fun checkSmartActions(query: String): List<SearchResult> {
    val results = mutableListOf<SearchResult>()

    val trimmedQuery = query.trim()

    // Phone Number Check
    // "22.10" is phone-shaped too, but a clock time means an alarm, not a call.
    val alarm = parseAlarmQuery(trimmedQuery)
    val phoneMatcher = android.util.Patterns.PHONE.matcher(trimmedQuery)
    val isPhone = alarm == null && phoneMatcher.matches() && trimmedQuery.length >= 3

    // Check for explicit triggers
    val lowerQuery = trimmedQuery.lowercase()
    val isCallTrigger = lowerQuery.startsWith("call ")
    val isSmsTrigger = lowerQuery.startsWith("sms ") || lowerQuery.startsWith("text ")

    var phoneQuery = trimmedQuery
    if (isCallTrigger) {
      phoneQuery = trimmedQuery.substring(5).trim()
    } else if (isSmsTrigger) {
      if (lowerQuery.startsWith("sms ")) {
        phoneQuery = trimmedQuery.substring(4).trim()
      } else {
        phoneQuery = trimmedQuery.substring(5).trim()
      }
    }

    val isExplicitPhone =
      (isCallTrigger || isSmsTrigger) &&
        android.util.Patterns.PHONE.matcher(phoneQuery).matches() &&
        phoneQuery.length >= 3

    if (isPhone || isExplicitPhone) {
      val targetNumber = if (isExplicitPhone) phoneQuery else trimmedQuery

      // Call Action
      if (isPhone || isCallTrigger) {
        val callIcon = context.getDrawable(android.R.drawable.sym_action_call)
        results.add(
          SearchResult.Content(
            id = "smart_action_call_$targetNumber",
            namespace = "smart_actions",
            title = "Call $targetNumber",
            subtitle = "Phone",
            icon = callIcon,
            packageName = "com.android.dialer", // Best effort
            deepLink = "tel:$targetNumber",
            rankingScore = RankingScores.SMART_ACTION_CALL,
          )
        )
      }

      // Text Action
      if (isPhone || isSmsTrigger) {
        val messageIcon = context.getDrawable(android.R.drawable.sym_action_chat)
        results.add(
          SearchResult.Content(
            id = "smart_action_sms_$targetNumber",
            namespace = "smart_actions",
            title = "Text $targetNumber",
            subtitle = "SMS",
            icon = messageIcon,
            packageName = "com.android.mms", // Best effort
            deepLink = "sms:$targetNumber",
            rankingScore = RankingScores.SMART_ACTION_SMS,
          )
        )
      }

      // Add Contact Action
      if (isPhone) {
        val addContactIcon = context.getDrawable(android.R.drawable.ic_menu_add)
        val encodedNumber = android.net.Uri.encode(targetNumber)
        results.add(
          SearchResult.Content(
            id = "smart_action_add_contact_$targetNumber",
            namespace = "smart_actions",
            title = "Add $targetNumber to contacts",
            subtitle = "Contact",
            icon = addContactIcon,
            packageName = "com.android.contacts", // Best effort
            deepLink =
              "intent:#Intent;action=android.intent.action.INSERT;type=vnd.android.cursor.dir/raw_contact;S.phone=$encodedNumber;end",
            rankingScore = RankingScores.SMART_ACTION_ADD_CONTACT,
          )
        )
      }
    }

    // Email Check
    val emailMatcher = android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedQuery)
    val isEmail = emailMatcher.matches()

    val isEmailTrigger = lowerQuery.startsWith("email ") || lowerQuery.startsWith("mailto ")
    var emailQuery = trimmedQuery
    if (isEmailTrigger) {
      if (lowerQuery.startsWith("email ")) {
        emailQuery = trimmedQuery.substring(6).trim()
      } else {
        emailQuery = trimmedQuery.substring(7).trim()
      }
    }

    val isExplicitEmail =
      isEmailTrigger && android.util.Patterns.EMAIL_ADDRESS.matcher(emailQuery).matches()

    if (isEmail || isExplicitEmail) {
      val targetEmail = if (isExplicitEmail) emailQuery else trimmedQuery
      val emailIcon = context.getDrawable(android.R.drawable.sym_action_email)
      results.add(
        SearchResult.Content(
          id = "smart_action_email_$targetEmail",
          namespace = "smart_actions",
          title = "Send Email to $targetEmail",
          subtitle = "Email",
          icon = emailIcon,
          packageName = "com.android.email", // Best effort
          deepLink = "mailto:$targetEmail",
          rankingScore = RankingScores.SMART_ACTION_EMAIL,
        )
      )
    }

    // URL check. Patterns.WEB_URL misses real hosts (multi-label names, localhost, some
    // IPs), so recognition lives in webAddressUrl.
    val url = webAddressUrl(trimmedQuery)
    if (url != null) {

      // Use a generic browser icon or similar if available, otherwise default
      // search icon
      val browserIcon =
        context.getDrawable(android.R.drawable.ic_menu_compass)
          ?: context.getDrawable(android.R.drawable.ic_menu_search)

      results.add(
        SearchResult.Content(
          id = "smart_action_url_$query",
          namespace = "smart_actions",
          title = "Open $query",
          subtitle = "Website",
          icon = browserIcon,
          packageName = "com.android.chrome", // Best effort, system handles
          // it
          deepLink = url,
          rankingScore = RankingScores.SMART_ACTION_URL,
        )
      )
    }

    // Timer Check - patterns like "1min", "4h", "20sec", "4m rice", "2 minutes pasta"
    val timerResult = parseTimerQuery(trimmedQuery)
    if (timerResult != null) {
      val (seconds, label, name) = timerResult
      val timerIcon = context.getDrawable(android.R.drawable.ic_menu_recent_history)
      val title = if (name != null) "Set timer for $label ($name)" else "Set timer for $label"
      val deepLink =
        if (name != null) "timer://set?seconds=$seconds&name=${android.net.Uri.encode(name)}"
        else "timer://set?seconds=$seconds"
      results.add(
        SearchResult.Content(
          id = "smart_action_timer_$seconds",
          namespace = "smart_actions",
          title = title,
          subtitle = "Timer",
          icon = timerIcon,
          packageName = "com.google.android.deskclock",
          deepLink = deepLink,
          rankingScore = RankingScores.SMART_ACTION_TIMER,
        )
      )
    }

    // Alarm Check - clock times like "22:10", "22.10", "7:30am", "alarm 6.45 gym"
    if (alarm != null) {
      val alarmIcon = context.getDrawable(android.R.drawable.ic_lock_idle_alarm)
      val title =
        if (alarm.name != null) "Set alarm for ${alarm.label} (${alarm.name})"
        else "Set alarm for ${alarm.label}"
      val deepLink =
        "alarm://set?hour=${alarm.hour}&minutes=${alarm.minutes}" +
          (alarm.name?.let { "&name=${android.net.Uri.encode(it)}" } ?: "")
      results.add(
        SearchResult.Content(
          id = "smart_action_alarm_${alarm.label}",
          namespace = "smart_actions",
          title = title,
          subtitle = "Alarm",
          icon = alarmIcon,
          packageName = "com.google.android.deskclock",
          deepLink = deepLink,
          rankingScore = RankingScores.SMART_ACTION_ALARM,
        )
      )
    }

    // Widget logic moved to Shortcuts.kt

    return results
  }

  // Returns Triple(seconds, durationLabel, optionalName)
  private fun parseTimerQuery(query: String): Triple<Int, String, String?>? {
    val pattern =
      Regex(
        """^(\d+)\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes|s|sec|secs|second|seconds)(?:\s+(.+))?$""",
        RegexOption.IGNORE_CASE,
      )
    val match = pattern.matchEntire(query.trim()) ?: return null

    val amount = match.groupValues[1].toIntOrNull() ?: return null
    val unit = match.groupValues[2].lowercase()
    val name = match.groupValues[3].trim().takeIf { it.isNotEmpty() }

    val seconds =
      when (unit) {
        "h",
        "hr",
        "hrs",
        "hour",
        "hours" -> amount * 3600
        "m",
        "min",
        "mins",
        "minute",
        "minutes" -> amount * 60
        "s",
        "sec",
        "secs",
        "second",
        "seconds" -> amount
        else -> return null
      }

    val label =
      when (unit) {
        "h",
        "hr",
        "hrs",
        "hour",
        "hours" -> if (amount == 1) "1 hour" else "$amount hours"
        "m",
        "min",
        "mins",
        "minute",
        "minutes" -> if (amount == 1) "1 minute" else "$amount minutes"
        "s",
        "sec",
        "secs",
        "second",
        "seconds" -> if (amount == 1) "1 second" else "$amount seconds"
        else -> return null
      }

    return Triple(seconds, label, name)
  }

  data class AlarmTime(val hour: Int, val minutes: Int, val name: String?) {
    val label: String
      get() = "$hour:${minutes.toString().padStart(2, '0')}"
  }

  companion object {
    private val ALARM_PATTERN =
      Regex(
        """^(?:(?:alarm|wekker)\s+)?(\d{1,2})[:.](\d{2})(?:\s*(am|pm))?(?:\s+(.+))?$""",
        RegexOption.IGNORE_CASE,
      )

    /**
     * Reads a clock time ("22:10", "22.10", "7:30pm", optionally prefixed with "alarm" or "wekker"
     * and followed by a label) as an alarm, in 24-hour time.
     */
    fun parseAlarmQuery(query: String): AlarmTime? {
      val match = ALARM_PATTERN.matchEntire(query.trim()) ?: return null
      var hour = match.groupValues[1].toInt()
      val minutes = match.groupValues[2].toInt()
      val meridiem = match.groupValues[3].lowercase()
      if (minutes > 59) return null
      if (meridiem.isNotEmpty()) {
        if (hour !in 1..12) return null
        hour = hour % 12 + if (meridiem == "pm") 12 else 0
      } else if (hour > 23) {
        return null
      }
      val name = match.groupValues[4].trim().takeIf { it.isNotEmpty() }
      return AlarmTime(hour, minutes, name)
    }
  }
}
