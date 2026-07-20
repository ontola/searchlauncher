package com.searchlauncher.app.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserNavigationTest {
  @Test
  fun keepsHttpUrls() {
    assertEquals("https://example.com/page", browserDestination("https://example.com/page"))
    assertEquals("http://example.com", browserDestination("http://example.com"))
  }

  @Test
  fun addsHttpsToHostNames() {
    assertEquals("https://example.com/page", browserDestination("example.com/page"))
  }

  @Test
  fun turnsOtherInputIntoWebSearch() {
    assertEquals(
      "https://www.google.com/search?q=keyboard+first+launcher",
      browserDestination("keyboard first launcher"),
    )
  }

  @Test
  fun parsesOpaquePageBackgroundColors() {
    assertEquals(0xfff0f1f2.toInt(), parseCssColor("\"rgb(240, 241, 242)\""))
    assertEquals(0x80336699.toInt(), parseCssColor("\"rgba(51, 102, 153, 0.5)\""))
    assertEquals(null, parseCssColor("\"rgba(0, 0, 0, 0)\""))
  }

  @Test
  fun normalizesBrowserOrigins() {
    assertEquals("https://example.com", browserOrigin("https://Example.com/path?q=1"))
    assertEquals("https://example.com:8443", browserOrigin("https://example.com:8443/path"))
    assertEquals(null, browserOrigin("file:///tmp/page.html"))
  }

  @Test
  fun createsDesktopUserAgentWithoutMobileAndroidTokens() {
    assertEquals(
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36",
      desktopUserAgent(
        "Mozilla/5.0 (Linux; Android 16; Phone Build/ABC; wv) AppleWebKit/537.36 Version/4.0 Chrome/140.0 Mobile Safari/537.36"
      ),
    )
  }

  @Test
  fun managesBrowserTabOrderAndActiveTab() {
    val tabs = BrowserTabs("https://one.example")
    tabs.add("https://two.example")
    tabs.add("https://three.example")

    assertEquals(2, tabs.activeIndex)
    assertEquals("https://three.example", tabs.active.url)

    tabs.activate(0)
    assertEquals("https://one.example", tabs.active.url)

    tabs.closeActive()
    assertEquals(2, tabs.items.size)
    assertEquals("https://two.example", tabs.active.url)
  }
}
