package com.searchlauncher.app.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Builds AppSearch documents for files in the device Downloads folder.
 *
 * This is a pure reader: it queries [MediaStore] and returns documents. Persisting them (and
 * checking storage/media permissions) is the caller's responsibility.
 *
 * Two sources, in order of preference. A SAF grant on the Downloads folder ([grantedTreeUri]) sees
 * every file in it. Without one, MediaStore is all that is left, and without a storage or media
 * grant it typically only surfaces files this app itself downloaded — every PDF, archive and
 * document belonging to another app is filtered out before the cursor sees it. That gap is why the
 * tree grant exists: it covers those without asking for All files access.
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
    val subtitle = formatSubtitle(entry)
    return AppSearchDocument(
      namespace = NAMESPACE,
      id = entry.key,
      name = entry.displayName,
      score = 3,
      intentUri = entry.uri.toString(),
      description = "${entry.mimeType.orEmpty()}|$subtitle",
    )
  }

  /**
   * The Downloads folder the user granted us, or null when there is no usable grant.
   *
   * Checks the grant is still held rather than trusting the stored string: the user can revoke it
   * from system settings, and a stale uri would silently return nothing.
   */
  fun grantedTreeUri(): Uri? {
    val saved =
      context
        .getSharedPreferences(Prefs.Launcher.FILE, Context.MODE_PRIVATE)
        .getString(Prefs.Launcher.DOWNLOADS_TREE_URI, null) ?: return null
    val uri = runCatching { Uri.parse(saved) }.getOrNull() ?: return null
    val held =
      runCatching {
          context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
          }
        }
        .getOrDefault(false)
    return if (held) uri else null
  }

  private fun collectFromTree(treeUri: Uri): List<DownloadEntry> {
    val rootId =
      runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return emptyList()
    val out = ArrayList<DownloadEntry>(MAX_TREE_SCAN)
    collectChildren(treeUri, rootId, out, depth = 0)
    out.sortByDescending { it.dateModifiedSeconds }
    return if (out.size > MAX_FILES) out.subList(0, MAX_FILES).toList() else out
  }

  private fun collectChildren(
    treeUri: Uri,
    parentDocumentId: String,
    out: MutableList<DownloadEntry>,
    depth: Int,
  ) {
    if (out.size >= MAX_TREE_SCAN || depth > MAX_TREE_DEPTH) return
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
    val projection =
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_SIZE,
      )
    // Recursed after the cursor closes: providers cap how many cursors they will keep open, and a
    // deep Downloads folder would otherwise hold one per level.
    val subdirectories = ArrayList<String>()
    try {
      context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
        val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val modifiedIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        if (idIdx < 0 || nameIdx < 0) return
        while (cursor.moveToNext() && out.size < MAX_TREE_SCAN) {
          val documentId = cursor.getString(idIdx) ?: continue
          val name = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: continue
          val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
          if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
            subdirectories.add(documentId)
            continue
          }
          if (name.startsWith(".")) continue
          out.add(
            DownloadEntry(
              displayName = name,
              mimeType = mime,
              dateModifiedSeconds =
                if (modifiedIdx >= 0 && !cursor.isNull(modifiedIdx)) {
                  TimeUnit.MILLISECONDS.toSeconds(cursor.getLong(modifiedIdx))
                } else {
                  0L
                },
              sizeBytes =
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L,
              documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
              documentId = documentId,
            )
          )
        }
      }
    } catch (e: Exception) {
      android.util.Log.w(TAG, "Failed listing $parentDocumentId", e)
      return
    }
    for (child in subdirectories) collectChildren(treeUri, child, out, depth + 1)
  }

  private fun queryEntries(): List<DownloadEntry> {
    grantedTreeUri()?.let { tree ->
      val fromTree = collectFromTree(tree)
      if (fromTree.isNotEmpty()) return fromTree
    }
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
    /** Scanned before sorting by date, so the newest [MAX_FILES] are the ones actually kept. */
    const val MAX_TREE_SCAN = 4000
    const val MAX_TREE_DEPTH = 4
    private const val TAG = "DownloadIndexer"

    /**
     * Records a Downloads folder the user just picked.
     *
     * The grant has to be taken persistably or it dies with the process, leaving the stored uri
     * pointing at something we can no longer read.
     */
    fun rememberTree(context: Context, uri: Uri): Boolean {
      return try {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        context
          .getSharedPreferences(Prefs.Launcher.FILE, Context.MODE_PRIVATE)
          .edit()
          .putString(Prefs.Launcher.DOWNLOADS_TREE_URI, uri.toString())
          .apply()
        true
      } catch (e: Exception) {
        android.util.Log.w(TAG, "Could not persist Downloads grant", e)
        false
      }
    }

    /** Opens the picker on the Downloads folder, so the user only has to confirm. */
    fun initialPickerUri(): Uri? =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching {
            DocumentsContract.buildDocumentUri(
              "com.android.externalstorage.documents",
              "primary:${Environment.DIRECTORY_DOWNLOADS}",
            )
          }
          .getOrNull()
      } else {
        null
      }

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
  val displayName: String,
  val mimeType: String?,
  val dateModifiedSeconds: Long,
  val sizeBytes: Long,
  val id: Long = 0L,
  val collectionUri: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
  /** Set for files found through the granted tree, which MediaStore may not be able to see. */
  val documentUri: Uri? = null,
  val documentId: String? = null,
) {
  /**
   * Stable document id. Tree and MediaStore ids never collide: one is a path, the other a number.
   */
  val key: String
    get() = documentId ?: id.toString()

  val uri: Uri
    get() = documentUri ?: ContentUris.withAppendedId(collectionUri, id)
}
