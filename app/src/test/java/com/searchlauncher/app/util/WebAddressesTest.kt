package com.searchlauncher.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebAddressesTest {
  @Test
  fun pageAddressDropsSchemeAndTrailingSlash() {
    assertEquals("atomic.place", displayPageAddress("https://atomic.place/"))
    assertEquals("atomic.place/docs", displayPageAddress("https://atomic.place/docs/"))
    assertEquals("atomic.place/a/b", displayPageAddress("http://atomic.place/a/b"))
    assertEquals("Example.COM/Path", displayPageAddress("HTTPS://Example.COM/Path"))
    assertEquals("atomic.place/docs/?q=1", displayPageAddress("https://atomic.place/docs/?q=1"))
    assertEquals("about:blank", displayPageAddress("  about:blank  "))
    assertEquals("", displayPageAddress("   "))
  }

  @Test
  fun recognizesMultiLabelHosts() {
    assertEquals(
      "https://ontola.staging.atomicserver.eu",
      webAddressUrl("ontola.staging.atomicserver.eu"),
    )
    assertEquals(
      "https://ontola.staging.atomicserver.eu/dashboard",
      webAddressUrl("  ontola.staging.atomicserver.eu/dashboard  "),
    )
    assertEquals(
      "https://www.ontola.staging.atomicserver.eu:8443/a?q=1#top",
      webAddressUrl("www.ontola.staging.atomicserver.eu:8443/a?q=1#top"),
    )
    assertEquals("https://a.b.c.d.e.f.example.co.uk", webAddressUrl("a.b.c.d.e.f.example.co.uk"))
  }

  @Test
  fun keepsExplicitHttpUrls() {
    assertEquals(
      "http://ontola.staging.atomicserver.eu/path",
      webAddressUrl("http://ontola.staging.atomicserver.eu/path"),
    )
    assertEquals("HTTPS://Example.COM/Path", webAddressUrl("HTTPS://Example.COM/Path"))
    assertEquals(
      "https://user:pass@example.com:8443/a/b?x=1#y",
      webAddressUrl("https://user:pass@example.com:8443/a/b?x=1#y"),
    )
    assertEquals("http://printer/admin", webAddressUrl("http://printer/admin"))
    assertEquals("http://[::1]/", webAddressUrl("http://[::1]/"))
    assertEquals("http://[2001:db8::1]:8080/path", webAddressUrl("http://[2001:db8::1]:8080/path"))
  }

  @Test
  fun recognizesLocalAndIpHosts() {
    assertEquals("https://localhost", webAddressUrl("localhost"))
    assertEquals("https://LocalHost:3000/app", webAddressUrl("LocalHost:3000/app"))
    assertEquals("https://192.168.1.1", webAddressUrl("192.168.1.1"))
    assertEquals("https://10.0.0.1:8080/status", webAddressUrl("10.0.0.1:8080/status"))
    assertEquals("https://[::1]", webAddressUrl("[::1]"))
    assertEquals("https://printer.local", webAddressUrl("printer.local"))
    assertEquals("https://my_server.internal", webAddressUrl("my_server.internal"))
  }

  @Test
  fun recognizesInternationalAndPunycodeNames() {
    assertEquals("https://münchen.de", webAddressUrl("münchen.de"))
    assertEquals("https://пример.рф/путь", webAddressUrl("пример.рф/путь"))
    assertEquals("https://xn--fsqu00a.xn--0zwm56d", webAddressUrl("xn--fsqu00a.xn--0zwm56d"))
    assertEquals("https://example.com.", webAddressUrl("example.com."))
  }

  @Test
  fun leavesSearchesAndNonHttpSchemesAlone() {
    assertNull(webAddressUrl("keyboard first launcher"))
    assertNull(webAddressUrl("random query"))
    assertNull(webAddressUrl("hello"))
    assertNull(webAddressUrl("user@example.com"))
    assertNull(webAddressUrl("3.14"))
    assertNull(webAddressUrl("192.168.1"))
    assertNull(webAddressUrl("256.1.1.1"))
    assertNull(webAddressUrl("example.com:99999"))
    assertNull(webAddressUrl("ftp://example.com"))
    assertNull(webAddressUrl("http://"))
    assertNull(webAddressUrl(""))
  }
}
