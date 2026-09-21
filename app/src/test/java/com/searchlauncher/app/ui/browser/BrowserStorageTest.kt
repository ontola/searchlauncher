package com.searchlauncher.app.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserStorageTest {
  @Test
  fun combinesProtocolsAndPortsAndSortsByUsage() {
    assertEquals(
      listOf(SiteStorage("example.com", 150), SiteStorage("other.test", 90)),
      groupSiteStorage(
        listOf(
          "https://EXAMPLE.com" to 100L,
          "http://example.com:8080" to 50L,
          "https://other.test" to 90L,
        )
      ),
    )
  }

  @Test
  fun doesNotConfuseSiblingDomainsOrMultiPartSuffixes() {
    assertEquals(
      listOf(
        SiteStorage("a.co.uk", 10),
        SiteStorage("b.co.uk", 10),
        SiteStorage("sub.a.co.uk", 10),
      ),
      groupSiteStorage(
        listOf("https://sub.a.co.uk" to 10L, "https://b.co.uk" to 10L, "https://a.co.uk" to 10L)
      ),
    )
  }

  @Test
  fun ignoresNonWebOriginsAndInvalidSizes() {
    assertEquals(
      listOf(SiteStorage("localhost", 0)),
      groupSiteStorage(
        listOf("file:///tmp/a" to 40L, "not a url" to 10L, "https://localhost:8080" to -1L)
      ),
    )
  }
}
