package com.searchlauncher.app.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Builds AppSearch documents for files in the device Downloads folder.
 *
 * This is a pure reader: it queries [MediaStore] and returns documents. Persisting them (and
 * checking storage/media permissions) is the caller's responsibility.
 *
 * On Android 13+ the platform only surfaces media the app has been granted, plus files this app
 * itself downloaded. PDFs and other documents from other apps need the All files access permission,
 * which this indexer does not request.
 */
class DownloadIndexer(private val context: Context) {

  fun readFingerprint(): String? {
    return try {
      val entries = queryEntries()
      if (entries.isEmpty()) "0/0"
      else "${entries.size}/${entries.maxOf { it.dateModifiedSeconds }}"
    } catch (e: Exception) {
      android.util.Log.w(TAG, "Failed to read downloads fingerprint", e)
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

  internal fun documentFrom(entry: DownloadEntry): AppSearchDocument {
    val uri = ContentUris.withAppendedId(entry.collectionUri, entry.id)
    val subtitle = formatSubtitle(entry)
    return AppSearchDocument(
      namespace = NAMESPACE,
      id = entry.id.toString(),
      name = entry.displayName,
      score = 3,
      intentUri = uri.toString(),
      description = "${entry.mimeType.orEmpty()}|$subtitle",
    )
  }

  private fun queryEntries(): List<DownloadEntry> {
    val seen = HashSet<Long>()
    val out = ArrayList<DownloadEntry>(MAX_FILES)
    collectFrom(
      uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
      extraSelection = null,
      extraArgs = null,
      seen = seen,
      out = out,
    )
    if (out.size < MAX_FILES) {
      collectFrom(
        uri = MediaStore.Files.getContentUri("external"),
        extraSelection =
          "(${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} = ?)",
        extraArgs = arrayOf("Download/%", "Download/"),
        seen = seen,
        out = out,
      )
    }
    return out
  }

  private fun collectFrom(
    uri: android.net.Uri,
    extraSelection: String?,
    extraArgs: Array<String>?,
    seen: MutableSet<Long>,
    out: MutableList<DownloadEntry>,
  ) {
    if (out.size >= MAX_FILES) return
    val remaining = MAX_FILES - out.size
    val pendingClause =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "${MediaStore.MediaColumns.IS_PENDING}=0"
      } else {
        null
      }
    val selection =
      listOfNotNull(pendingClause, extraSelection).joinToString(" AND ").ifEmpty { null }
    val projection =
      arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.SIZE,
      )
    val sort = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
    val cursor =
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          val args =
            Bundle().apply {
              putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, remaining)
              putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort)
              if (selection != null) {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                extraArgs?.let {
                  putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, it)
                }
              }
            }
          context.contentResolver.query(uri, projection, args, null)
        } else {
          context.contentResolver.query(
            uri,
            projection,
            selection,
            extraArgs,
            "$sort LIMIT $remaining",
          )
        }
      } catch (e: SecurityException) {
        android.util.Log.w(TAG, "No access to $uri", e)
        null
      } catch (e: Exception) {
        android.util.Log.w(TAG, "Failed querying $uri", e)
        null
      } ?: return

    cursor.use {
      val idIdx = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
      val nameIdx = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
      val mimeIdx = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
      val modifiedIdx = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
      val sizeIdx = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
      while (it.moveToNext() && out.size < MAX_FILES) {
        val id = it.getLong(idIdx)
        if (!seen.add(id)) continue
        val name = it.getString(nameIdx)?.takeIf { n -> n.isNotBlank() } ?: continue
        out.add(
          DownloadEntry(
            id = id,
            displayName = name,
            mimeType = if (mimeIdx >= 0) it.getString(mimeIdx) else null,
            dateModifiedSeconds =
              if (modifiedIdx >= 0 && !it.isNull(modifiedIdx)) it.getLong(modifiedIdx) else 0L,
            sizeBytes = if (sizeIdx >= 0 && !it.isNull(sizeIdx)) it.getLong(sizeIdx) else 0L,
            collectionUri = uri,
          )
        )
      }
    }
  }

  companion object {
    const val NAMESPACE = "downloads"
    const val MAX_FILES = 400
    private const val TAG = "DownloadIndexer"

    fun formatSubtitle(entry: DownloadEntry): String {
      val size = formatSize(entry.sizeBytes)
      val whenText = formatModified(entry.dateModifiedSeconds)
      val kind = mimeLabel(entry.mimeType)
      return listOfNotNull(kind, size, whenText).joinToString(" · ")
    }

    fun mimeLabel(mime: String?): String? {
      if (mime.isNullOrBlank()) return null
      return when {
        mime.startsWith("image/") -> "Image"
        mime.startsWith("video/") -> "Video"
        mime.startsWith("audio/") -> "Audio"
        mime == "application/pdf" -> "PDF"
        mime.contains("zip") || mime.contains("compressed") -> "Archive"
        mime.startsWith("text/") -> "Text"
        else -> mime.substringAfter('/', mime).uppercase(Locale.US)
      }
    }

    fun formatSize(bytes: Long): String? {
      if (bytes <= 0L) return null
      if (bytes < 1024) return "$bytes B"
      val kb = bytes / 1024.0
      if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
      val mb = kb / 1024.0
      return if (mb < 1024) String.format(Locale.US, "%.1f MB", mb)
      else String.format(Locale.US, "%.1f GB", mb / 1024.0)
    }

    fun formatModified(epochSeconds: Long): String? {
      if (epochSeconds <= 0L) return null
      val then = TimeUnit.SECONDS.toMillis(epochSeconds)
      val delta = System.currentTimeMillis() - then
      val days = TimeUnit.MILLISECONDS.toDays(delta)
      return when {
        delta < TimeUnit.HOURS.toMillis(1) -> "Just now"
        delta < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(delta)}h ago"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> {
          val fmt = java.text.SimpleDateFormat("d MMM", Locale.getDefault())
          fmt.format(java.util.Date(then))
        }
      }
    }
  }
}

data class DownloadEntry(
  val id: Long,
  val displayName: String,
  val mimeType: String?,
  val dateModifiedSeconds: Long,
  val sizeBytes: Long,
  val collectionUri: android.net.Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
)
