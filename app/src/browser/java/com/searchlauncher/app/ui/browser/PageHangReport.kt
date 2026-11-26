package com.searchlauncher.app.ui.browser

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.searchlauncher.app.util.FeedbackReporter
import com.searchlauncher.app.util.FeedbackResult
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import io.sentry.protocol.SentryId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reporting for pages that stop drawing.
 *
 * A Gecko page that has dropped its paint is covered by the tab's saved preview until it draws
 * again. When it never does (a stuck compositor, a hung content process) the browser just shows
 * that soft picture and ignores touch, and nothing reached Sentry: the main-thread watchdog only
 * sees hangs on the main thread, and Android's ANR check never fires because the app is fine. This
 * catches it from the paint side instead.
 */
internal object PageHangReport {
  /**
   * How long a page that is not loading may sit on screen without a paint. Resuming a drawn page
   * normally paints within a few frames.
   */
  const val STUCK_AFTER_MS = 3_000L

  /** A page that is still loading may take its time over a first paint on a slow connection. */
  const val STUCK_WHILE_LOADING_AFTER_MS = 10_000L

  /**
   * Sends a report without asking, like a crash report: only when crash reporting is on, and with
   * the host rather than the page address. Returns the event id so a report the user sends after it
   * can point to it.
   */
  fun reportAutomatically(details: Map<String, String>): String? {
    if (!Sentry.isEnabled()) return null
    return runCatching {
        val event =
          SentryEvent().apply {
            level = SentryLevel.WARNING
            message = Message().apply { message = "Page not painted while on screen" }
            setTag("hang", "paint_timeout")
            setTag("page_loading", details["loading"] ?: "unknown")
            details.forEach { (key, value) -> setExtra(key, value) }
            fingerprints = listOf("gecko-paint-timeout")
          }
        Sentry.captureEvent(event).takeIf { it != SentryId.EMPTY_ID }?.toString()
      }
      .getOrNull()
  }
}

/** Starts the not-responding timer whenever [page] is on screen without a paint. */
@Composable
internal fun WatchForStuckPaint(page: GeckoPage, suppressed: Boolean) {
  val waiting = page.shown && page.awaitingPaint && page.error == null && !suppressed
  LaunchedEffect(waiting, page.loading) {
    if (!waiting) return@LaunchedEffect
    delay(
      if (page.loading) PageHangReport.STUCK_WHILE_LOADING_AFTER_MS
      else PageHangReport.STUCK_AFTER_MS
    )
    page.markNotResponding()
  }
}

/** Sits over the top of a page that has not drawn, offering a way out and a way to tell us. */
@Composable
internal fun NotRespondingBar(onReload: () -> Unit, onReport: () -> Unit, modifier: Modifier) {
  Column(
    modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.errorContainer)
      .padding(horizontal = 16.dp, vertical = 12.dp)
  ) {
    Text(
      "This page isn't responding",
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onErrorContainer,
    )
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = onReload) { Text("Reload") }
      OutlinedButton(onClick = onReport) { Text("Report") }
    }
  }
}

/**
 * "Report a problem with this page": opened from the browser menu, and from [NotRespondingBar].
 * Device and app details always go along, as in Send feedback; the page address and the page's
 * state can be left out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageReportSheet(page: GeckoPage, privateMode: Boolean, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var message by remember { mutableStateOf("") }
  var includeUrl by remember { mutableStateOf(!privateMode) }
  var includeState by remember { mutableStateOf(true) }
  var sending by remember { mutableStateOf(false) }
  // Taken when the sheet opens: by the time Send is pressed the page may have recovered.
  val stuck = remember { page.notResponding }
  val state = remember { page.diagnostics(includeUrl = true) }
  val hangEventId = remember { page.hangEventId }

  ModalBottomSheet(onDismissRequest = { if (!sending) onDismiss() }, sheetState = sheetState) {
    Column(
      Modifier.verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp)
        .padding(bottom = 16.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("Report a problem with this page", style = MaterialTheme.typography.titleLarge)
      OutlinedTextField(
        value = message,
        onValueChange = { message = it },
        label = { Text("What went wrong? (optional)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 5,
        enabled = !sending,
      )
      CheckRow("Page address", includeUrl, enabled = !sending) { includeUrl = it }
      CheckRow("Browser state and recent events", includeState, enabled = !sending) {
        includeState = it
      }
      Text(
        "Device and app version are always included. This is sent to Sentry and does not turn on crash reporting.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
      ) {
        TextButton(onClick = onDismiss, enabled = !sending) { Text("Cancel") }
        Button(
          enabled = !sending,
          onClick = {
            sending = true
            val appContext = context.applicationContext
            val extras = buildMap {
              if (includeState) putAll(state - "page_url" - "page_host")
              if (includeUrl) {
                put("page_url", state["page_url"].orEmpty())
                put("page_host", state["page_host"].orEmpty())
              }
              hangEventId?.let { put("hang_event_id", it) }
            }
            val tags = mapOf("feedback" to "page", "page_not_responding" to stuck.toString())
            scope.launch {
              val result =
                withContext(Dispatchers.IO + NonCancellable) {
                  FeedbackReporter.submit(
                    appContext,
                    message,
                    contactEmail = null,
                    fallbackMessage =
                      if (stuck) "Page report: not responding" else "Page report: no description",
                    extraTags = tags,
                    extraExtras = extras,
                    includeBrowserLog = includeState,
                  )
                }
              sending = false
              when (result) {
                FeedbackResult.Sent -> {
                  Toast.makeText(appContext, "Report sent", Toast.LENGTH_SHORT).show()
                  onDismiss()
                }
                else ->
                  Toast.makeText(appContext, "Could not send the report", Toast.LENGTH_LONG).show()
              }
            }
          },
        ) {
          Text(if (sending) "Sending…" else "Send")
        }
      }
    }
  }
}

@Composable
private fun CheckRow(
  label: String,
  checked: Boolean,
  enabled: Boolean,
  onChange: (Boolean) -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
    Text(label, style = MaterialTheme.typography.bodyMedium)
  }
}
