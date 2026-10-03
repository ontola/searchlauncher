package com.searchlauncher.app.ui.browser

import android.app.DownloadManager

// Completed transfers retain their identity until the corresponding history record is deleted.
// In particular, registration and the next asynchronous DownloadManager query are not atomic.
internal fun updatePageDownload(key: String, name: String, fraction: Float?) {
  val previous = pendingPageDownloads[key] ?: PageDownloadProgress(name, fraction)
  pendingPageDownloads[key] = previous.copy(name = name, fraction = fraction)
}

internal fun completePageDownload(key: String, id: Long, name: String, bytes: Long) {
  val previous = pendingPageDownloads[key] ?: PageDownloadProgress(name, 1f)
  pendingPageDownloads[key] =
    previous.copy(
      name = name,
      fraction = 1f,
      completed =
        BrowserDownload(
          id,
          name,
          DownloadManager.STATUS_SUCCESSFUL,
          bytes,
          bytes,
          System.currentTimeMillis(),
        ),
    )
}

internal fun finishPageDownload(key: String) {
  if (pendingPageDownloads[key]?.completed == null) pendingPageDownloads.remove(key)
}

internal fun acknowledgePageDownloadHistory(history: List<BrowserDownload>) {
  val ids = history.map { it.id }.toSet()
  for ((key, transfer) in pendingPageDownloads.toMap()) {
    val id = transfer.completed?.id ?: continue
    if (id in ids && !transfer.seenInHistory)
      pendingPageDownloads[key] = transfer.copy(seenInHistory = true)
    else if (id !in ids && transfer.seenInHistory) pendingPageDownloads.remove(key)
  }
}

internal fun forgetPageDownload(id: Long) {
  pendingPageDownloads.entries
    .filter { it.value.completed?.id == id }
    .map { it.key }
    .forEach { pendingPageDownloads.remove(it) }
}

internal data class DownloadRow(
  val key: String,
  val item: BrowserDownload,
  val progress: PageDownloadProgress? = null,
)

/** Keep existing rows in place; insert new records by age (including a late first history poll). */
internal fun reconcileDownloadRows(
  previous: List<DownloadRow>,
  history: List<BrowserDownload>,
  transfers: Map<String, PageDownloadProgress>,
): List<DownloadRow> {
  val importedIds = transfers.values.mapNotNull { it.completed?.id }.toSet()
  val byId = history.associateBy { it.id }
  val candidates =
    transfers.map { (key, transfer) ->
      val complete = transfer.completed
      DownloadRow(
        "page:$key",
        complete?.let { byId[it.id] ?: it }
          ?: BrowserDownload(
            -1,
            transfer.name,
            DownloadManager.STATUS_RUNNING,
            0,
            -1,
            transfer.startedAt,
          ),
        if (complete == null) transfer else null,
      )
    } + history.filter { it.id !in importedIds }.map { DownloadRow("download:${it.id}", it) }
  val byKey = candidates.associateBy { it.key }
  val existingKeys = previous.map { it.key }.toSet()
  val newRows =
    candidates
      .filter { it.key !in existingKeys }
      .sortedByDescending { row ->
        transfers[row.key.removePrefix("page:")]?.startedAt ?: row.item.updated
      }
  val ordered = previous.mapNotNull { byKey[it.key] }.toMutableList()
  fun age(row: DownloadRow) =
    transfers[row.key.removePrefix("page:")]?.startedAt ?: row.item.updated
  for (row in newRows) {
    val index = ordered.indexOfFirst { age(it) < age(row) }.takeIf { it >= 0 } ?: ordered.size
    ordered.add(index, row)
  }
  return ordered
}
