package com.searchlauncher.app.ui.browser

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class BrowserDownload(
  val id: Long,
  val name: String,
  val status: Int,
  val bytes: Long,
  val total: Long,
  val updated: Long,
) {
  val active: Boolean
    get() =
      status == DownloadManager.STATUS_PENDING ||
        status == DownloadManager.STATUS_RUNNING ||
        status == DownloadManager.STATUS_PAUSED
}

/** DownloadManager retains this app's download records across browser and process restarts. */
internal fun readBrowserDownloads(manager: DownloadManager): List<BrowserDownload> =
  buildList {
      manager.query(DownloadManager.Query())?.use { cursor ->
        fun long(column: String) = cursor.getLong(cursor.getColumnIndexOrThrow(column))
        while (cursor.moveToNext()) {
          add(
            BrowserDownload(
              long(DownloadManager.COLUMN_ID),
              cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                ?: "Download",
              long(DownloadManager.COLUMN_STATUS).toInt(),
              long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
              long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
              long(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP),
            )
          )
        }
      }
    }
    .sortedByDescending { it.id }

internal const val APK_MIME_TYPE = "application/vnd.android.package-archive"

internal fun downloadOpenIntent(uri: Uri, name: String, mimeType: String?): Intent =
  Intent(Intent.ACTION_VIEW)
    .setDataAndType(
      uri,
      if (name.endsWith(".apk", ignoreCase = true)) APK_MIME_TYPE
      else mimeType ?: "application/octet-stream",
    )
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    .apply { clipData = ClipData.newRawUri(name, uri) }

@Composable
internal fun BrowserDownloadsScreen(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val manager = remember { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
  var downloads by remember { mutableStateOf<List<BrowserDownload>>(emptyList()) }
  var pendingDocumentUri by rememberSaveable { mutableStateOf<String?>(null) }
  var loaded by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf(false) }
  var openError by remember { mutableStateOf<String?>(null) }
  var pendingApkId by rememberSaveable { mutableStateOf<Long?>(null) }
  var pendingApkName by rememberSaveable { mutableStateOf("") }
  var pendingChooser by rememberSaveable { mutableStateOf(false) }
  var deleteTarget by remember { mutableStateOf<BrowserDownload?>(null) }
  val scope = rememberCoroutineScope()
  fun openDownload(id: Long, name: String, chooser: Boolean = false, documentUri: String? = null) {
    openError = null
    try {
      val uri =
        documentUri?.let(Uri::parse)
          ?: manager.getUriForDownloadedFile(id)
          ?: error("File unavailable")
      context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {} ?: error("File unavailable")
      val mime =
        if (documentUri != null) context.contentResolver.getType(uri)
        else manager.getMimeTypeForDownloadedFile(id)
      val intent = downloadOpenIntent(uri, name, mime)
      context.startActivity(if (chooser) Intent.createChooser(intent, "Open with") else intent)
    } catch (_: android.content.ActivityNotFoundException) {
      openError = "No app is available to open this file."
    } catch (_: SecurityException) {
      openError =
        "Android blocked access to this file. Check the install permission or download it again."
    } catch (_: Exception) {
      openError = "This downloaded file is unavailable. Download it again."
    }
  }
  val allowInstalls =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      val id = pendingApkId
      pendingApkId = null
      if (id != null) {
        if (context.packageManager.canRequestPackageInstalls())
          openDownload(id, pendingApkName, pendingChooser, pendingDocumentUri)
        else
          openError =
            "Installation is not allowed yet. Tap Install APK and enable Allow from this source."
      }
    }
  fun requestOpen(item: BrowserDownload, chooser: Boolean = false, documentUri: String? = null) {
    val isApk =
      item.name.endsWith(".apk", ignoreCase = true) ||
        ((if (documentUri != null) context.contentResolver.getType(Uri.parse(documentUri))
        else manager.getMimeTypeForDownloadedFile(item.id)) == APK_MIME_TYPE)
    if (isApk && !chooser && !context.packageManager.canRequestPackageInstalls()) {
      pendingChooser = chooser
      pendingApkId = item.id
      pendingApkName = item.name
      pendingDocumentUri = documentUri
      openError = null
      try {
        allowInstalls.launch(
          Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
          )
        )
      } catch (_: Exception) {
        pendingApkId = null
        openError =
          "Open Android Settings → Install unknown apps and allow this browser to install APKs."
      }
    } else openDownload(item.id, item.name, chooser, documentUri)
  }
  fun showInFiles() {
    try {
      context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
    } catch (_: Exception) {
      openError = "No Files app is available to show Downloads."
    }
  }
  val openDocument =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        try {
          val name =
            context.contentResolver
              .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
              ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "File"
          requestOpen(
            BrowserDownload(0, name, DownloadManager.STATUS_SUCCESSFUL, 0, 0, 0),
            documentUri = uri.toString(),
          )
        } catch (_: Exception) {
          openError = "This file could not be opened. Please select it again."
        }
      }
    }
  LaunchedEffect(manager) {
    while (true) {
      val result = withContext(Dispatchers.IO) { runCatching { readBrowserDownloads(manager) } }
      result.onSuccess { downloads = it }.onFailure { error = true }
      if (result.isSuccess) error = false
      loaded = true
      delay(750)
    }
  }
  BackHandler(onBack = onDismiss)
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Downloads", style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onDismiss) { Text("Done") }
      }
      Text(
        "History and progress for files downloaded with SearchLauncher.",
        style = MaterialTheme.typography.bodyMedium,
      )
      Row {
        TextButton(onClick = { openDocument.launch(arrayOf("*/*")) }) { Text("Open a file") }
        TextButton(onClick = ::showInFiles) { Text("Browse all downloads") }
      }
      openError?.let {
        Text(
          it,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(vertical = 8.dp),
        )
      }
      pendingPageDownloads.values.forEach { export ->
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
          Text(export.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
          val fraction = export.fraction
          if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
          else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
      }
      when {
        !loaded -> CircularProgressIndicator()
        error -> Text("Couldn’t load downloads. Retrying…")
        downloads.isEmpty() -> if (pendingPageDownloads.isEmpty()) Text("No downloads yet")
        else ->
          LazyColumn(Modifier.weight(1f)) {
            items(downloads, key = { it.id }) { item ->
              DownloadCard(
                item = item,
                onOpen = { requestOpen(item) },
                onOpenWith = { requestOpen(item, chooser = true) },
                onShowInFiles = ::showInFiles,
                onDelete = { deleteTarget = item },
              )
            }
          }
      }
    }
  }
  deleteTarget?.let { item ->
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      title = { Text(if (item.active) "Cancel download?" else "Delete download?") },
      text = { Text("${item.name} will be removed from Downloads, including the saved file.") },
      confirmButton = {
        TextButton(
          onClick = {
            deleteTarget = null
            scope.launch {
              val removed =
                withContext(Dispatchers.IO) {
                  runCatching { manager.remove(item.id) }.getOrDefault(0)
                }
              if (removed > 0) {
                downloads = downloads.filterNot { it.id == item.id }
              } else openError = "Couldn’t delete this download. Try removing it in Files."
            }
          }
        ) {
          Text(if (item.active) "Cancel download" else "Delete")
        }
      },
      dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Keep") } },
    )
  }
}

@Composable
private fun DownloadCard(
  item: BrowserDownload,
  onOpen: () -> Unit,
  onOpenWith: () -> Unit,
  onShowInFiles: () -> Unit,
  onDelete: () -> Unit,
) {
  val context = LocalContext.current
  var menu by remember { mutableStateOf(false) }
  val complete = item.status == DownloadManager.STATUS_SUCCESSFUL
  val percent =
    if (item.total > 0) ((item.bytes.toDouble() / item.total) * 100).toInt().coerceIn(0, 100)
    else null
  val status =
    when (item.status) {
      DownloadManager.STATUS_SUCCESSFUL -> "Complete"
      DownloadManager.STATUS_FAILED -> "Download failed"
      DownloadManager.STATUS_PAUSED -> "Waiting for connection"
      DownloadManager.STATUS_PENDING -> "Queued"
      else -> percent?.let { "Downloading · $it%" } ?: "Downloading"
    }
  Card(
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
          Icon(
            if (item.active) Icons.Default.Download else Icons.Default.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.padding(12.dp).size(24.dp),
          )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            item.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            status,
            style = MaterialTheme.typography.labelMedium,
            color =
              if (item.status == DownloadManager.STATUS_FAILED) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Box {
          IconButton(onClick = { menu = true }) {
            Icon(Icons.Default.MoreVert, "Actions for ${item.name}")
          }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (complete)
              DropdownMenuItem(
                text = { Text("Open with…") },
                onClick = {
                  menu = false
                  onOpenWith()
                },
              )
            DropdownMenuItem(
              text = { Text("Show in Files") },
              onClick = {
                menu = false
                onShowInFiles()
              },
            )
            DropdownMenuItem(
              text = { Text(if (item.active) "Cancel download" else "Delete") },
              onClick = {
                menu = false
                onDelete()
              },
            )
          }
        }
      }
      if (item.active) {
        if (item.total > 0)
          LinearProgressIndicator(
            progress = { (item.bytes.toFloat() / item.total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
          )
        else LinearProgressIndicator(Modifier.fillMaxWidth())
      }
      val bytes = Formatter.formatShortFileSize(context, item.bytes.coerceAtLeast(0))
      val total =
        if (item.active && item.total > 0)
          " / ${Formatter.formatShortFileSize(context, item.total)}"
        else ""
      Text(
        "$bytes$total · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.updated))}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (complete)
        FilledTonalButton(onClick = onOpen) {
          Text(if (item.name.endsWith(".apk", true)) "Install APK" else "Open file")
        }
    }
  }
}
