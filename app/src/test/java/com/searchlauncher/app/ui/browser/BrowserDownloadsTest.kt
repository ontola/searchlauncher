package com.searchlauncher.app.ui.browser

import android.app.DownloadManager
import android.database.MatrixCursor
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserDownloadsTest {
  @Test
  fun appDoesNotRequestBroadStoragePermissions() {
    val context =
      androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    val permissions =
      context.packageManager
        .getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        .requestedPermissions
        .orEmpty()
    for (permission in
      listOf(
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
      )) {
      assertFalse(permission, permission in permissions)
    }
  }

  @Test
  fun downloadsPageIsDiscoverableWithoutMatchingUnrelatedQueries() {
    val context =
      androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    for (query in listOf("download", "Downloads", " files ")) {
      val result = com.searchlauncher.app.ui.createDownloadsResult(context, query)
      assertEquals("Downloads", result?.title)
      val intent =
        android.content.Intent.parseUri(result!!.deepLink, android.content.Intent.URI_INTENT_SCHEME)
      assertEquals("com.searchlauncher.action.OPEN_DOWNLOADS", intent.action)
    }
    assertNull(com.searchlauncher.app.ui.createDownloadsResult(context, ""))
    assertNull(com.searchlauncher.app.ui.createDownloadsResult(context, "weather"))
  }

  @Test
  fun apkUsesInstallerMimeAndGrantsFileAccessEvenWithGenericServerType() {
    val uri = android.net.Uri.parse("content://downloads/my_downloads/42")
    val intent = downloadOpenIntent(uri, "Preview.APK", "application/octet-stream")
    assertEquals(android.content.Intent.ACTION_VIEW, intent.action)
    assertEquals(APK_MIME_TYPE, intent.type)
    assertEquals(uri, intent.data)
    assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
    assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    assertEquals("application/pdf", downloadOpenIntent(uri, "document.pdf", "application/pdf").type)
  }

  @Test
  fun historyIncludesCompletedAndFailedDownloadsAlongsideLiveProgress() {
    val cursor =
      MatrixCursor(
        arrayOf(
          DownloadManager.COLUMN_ID,
          DownloadManager.COLUMN_TITLE,
          DownloadManager.COLUMN_STATUS,
          DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
          DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
          DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP,
        )
      )
    cursor.addRow(arrayOf<Any>(1L, "old.apk", DownloadManager.STATUS_SUCCESSFUL, 100L, 100L, 10L))
    cursor.addRow(arrayOf<Any>(3L, "new.apk", DownloadManager.STATUS_RUNNING, 25L, 100L, 30L))
    cursor.addRow(arrayOf<Any>(2L, "failed.zip", DownloadManager.STATUS_FAILED, 0L, -1L, 20L))
    val manager = mockk<DownloadManager>()
    every { manager.query(any()) } returns cursor
    val results = readBrowserDownloads(manager)
    assertEquals(listOf(3L, 2L, 1L), results.map { it.id })
    assertEquals(25L, results.first().bytes)
    assertTrue(results.first().active)
    assertFalse(results[1].active)
    assertFalse(results.last().active)
    assertTrue(cursor.isClosed)
  }
}
