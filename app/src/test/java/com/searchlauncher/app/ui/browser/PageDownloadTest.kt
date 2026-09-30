package com.searchlauncher.app.ui.browser

import org.junit.Assert.*
import org.junit.Test

class PageDownloadTest {
  @Test
  fun onlyPageOwnedSchemesUsePageTransfer() {
    assertTrue(isPageDownload("blob:https://example.com/123"))
    assertTrue(isPageDownload("data:text/plain;base64,aGk="))
    assertFalse(isPageDownload("https://example.com/file.zip"))
    assertFalse(isPageDownload("file:///data/private"))
    assertFalse(isPageDownload("javascript:alert(1)"))
  }

  @Test
  fun exportNamesCannotEscapeTheirDirectory() {
    assertEquals("export.json", safePageDownloadName("../../export.json"))
    assertEquals("export.json", safePageDownloadName("C:\\temp\\export.json"))
    assertEquals("download", safePageDownloadName(".."))
    assertEquals("download", safePageDownloadName(""))
    assertEquals("a_b.txt", safePageDownloadName("a\u0000b.txt"))
    assertEquals(160, safePageDownloadName("a".repeat(500)).length)
  }

  @Test
  fun contentDispositionNamesIncludeTheExtendedForm() {
    assertEquals(
      "local bartender.jpg",
      contentDispositionFileName(
        "attachment; filename=\"fallback.jpg\"; filename*=UTF-8''local%20bartender.jpg"
      ),
    )
    assertEquals("photo.png", contentDispositionFileName("inline; filename=\"photo.png\""))
    assertEquals("report.pdf", contentDispositionFileName("attachment; filename=report.pdf"))
    assertEquals("a+b.txt", contentDispositionFileName("attachment; filename*=UTF-8''a+b.txt"))
    assertNull(contentDispositionFileName("attachment"))
    assertNull(contentDispositionFileName(null))
  }
}
