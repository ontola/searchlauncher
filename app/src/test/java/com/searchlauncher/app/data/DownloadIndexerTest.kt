package com.searchlauncher.app.data

import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadIndexerTest {

  @Test
  fun formatSize_skipsEmptyAndFormatsBuckets() {
    assertEquals(null, DownloadIndexer.formatSize(0))
    assertEquals("512 B", DownloadIndexer.formatSize(512))
    assertEquals("2 KB", DownloadIndexer.formatSize(2048))
    assertEquals("1.5 MB", DownloadIndexer.formatSize((1.5 * 1024 * 1024).toLong()))
  }

  @Test
  fun mimeLabel_mapsCommonTypes() {
    assertEquals("PDF", DownloadIndexer.mimeLabel("application/pdf"))
    assertEquals("Image", DownloadIndexer.mimeLabel("image/jpeg"))
    assertEquals("Video", DownloadIndexer.mimeLabel("video/mp4"))
    assertEquals("Archive", DownloadIndexer.mimeLabel("application/zip"))
  }

  @Test
  fun documentFrom_putsContentUriAndSearchableSubtitle() {
    val indexer = DownloadIndexer(ApplicationProvider.getApplicationContext())
    val doc =
      indexer.documentFrom(
        DownloadEntry(
          id = 42L,
          displayName = "ticket.pdf",
          mimeType = "application/pdf",
          dateModifiedSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
          sizeBytes = 2048,
        )
      )
    assertEquals("downloads", doc.namespace)
    assertEquals("42", doc.id)
    assertEquals("ticket.pdf", doc.name)
    assertTrue(doc.intentUri!!.contains("42"))
    assertTrue(doc.description!!.startsWith("application/pdf|"))
    assertTrue(doc.description!!.contains("PDF"))
  }
}
