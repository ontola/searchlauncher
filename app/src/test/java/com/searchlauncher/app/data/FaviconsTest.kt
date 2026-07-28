package com.searchlauncher.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FaviconsTest {
  @Test
  fun `extracts host from page urls`() {
    assertEquals("example.com", faviconHost("https://example.com/some/page?q=1#top"))
    assertEquals("news.example.com", faviconHost("http://news.example.com"))
  }

  @Test
  fun `pages on the same site share one host key`() {
    assertEquals(faviconHost("https://example.com/a"), faviconHost("https://example.com/b?x=y"))
  }

  @Test
  fun `host is normalised so keys do not fragment`() {
    assertEquals("example.com", faviconHost("https://EXAMPLE.com/Page"))
    assertEquals("example.com", faviconHost("  https://example.com/page  "))
  }

  @Test
  fun `returns null when there is no usable host`() {
    assertEquals(null, faviconHost("about:blank"))
    assertEquals(null, faviconHost("not a url"))
    assertEquals(null, faviconHost(""))
  }

  @Test
  fun `cache keys are namespaced per host`() {
    assertEquals("favicon_example.com", faviconCacheKey("example.com"))
    assert(faviconCacheKey("a.com") != faviconCacheKey("b.com"))
  }
}
