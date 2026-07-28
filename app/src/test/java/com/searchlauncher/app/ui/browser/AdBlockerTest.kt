package com.searchlauncher.app.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerTest {
  private val blocklist = blocklistHashesOf(listOf("doubleclick.net", "ads.example.com"))

  @Test
  fun `matches exact blocked domains`() {
    assertTrue(matchesBlockedDomain("doubleclick.net", blocklist))
    assertTrue(matchesBlockedDomain("ads.example.com", blocklist))
  }

  @Test
  fun `matches subdomains of blocked domains`() {
    assertTrue(matchesBlockedDomain("stats.g.doubleclick.net", blocklist))
    assertTrue(matchesBlockedDomain("eu.ads.example.com", blocklist))
  }

  @Test
  fun `does not match unrelated or parent domains`() {
    assertFalse(matchesBlockedDomain("example.com", blocklist))
    assertFalse(matchesBlockedDomain("wikipedia.org", blocklist))
    // Suffix collisions must not match: "notdoubleclick.net" merely ends with a blocked name.
    assertFalse(matchesBlockedDomain("notdoubleclick.net", blocklist))
  }

  @Test
  fun `never matches a bare public suffix`() {
    val tldList = blocklistHashesOf(listOf("net"))
    assertFalse(matchesBlockedDomain("doubleclick.net", tldList))
    assertFalse(matchesBlockedDomain("net", tldList))
  }

  @Test
  fun `host matching is case insensitive`() {
    assertTrue(matchesBlockedDomain("Stats.G.DoubleClick.NET", blocklist))
  }

  @Test
  fun `empty blocklist blocks nothing`() {
    assertFalse(matchesBlockedDomain("doubleclick.net", LongArray(0)))
  }

  @Test
  fun `parses hosts file lines`() {
    assertEquals("ads.example.com", parseHostsLine("0.0.0.0 ads.example.com"))
    assertEquals("ads.example.com", parseHostsLine("127.0.0.1\tads.example.com # tracker"))
    assertEquals("ads.example.com", parseHostsLine("ads.example.com"))
  }

  @Test
  fun `skips comments blanks and loopback entries`() {
    assertEquals(null, parseHostsLine("# This is a comment"))
    assertEquals(null, parseHostsLine("   "))
    assertEquals(null, parseHostsLine("127.0.0.1 localhost"))
    assertEquals(null, parseHostsLine("::1 ip6-localhost"))
    // A bare hostname with no dot is never a blockable domain.
    assertEquals(null, parseHostsLine("0.0.0.0 broadcasthost"))
  }

  @Test
  fun `rejects malformed domains`() {
    assertEquals(null, parseHostsLine("0.0.0.0 not a domain"))
    assertEquals(null, parseHostsLine("0.0.0.0 bad_domain.com"))
    assertEquals(null, parseHostsLine("0.0.0.0 http://example.com/path"))
  }

  @Test
  fun `domain hashes are stable and distinct`() {
    assertEquals(domainHash("doubleclick.net"), domainHash("doubleclick.net"))
    assertTrue(domainHash("doubleclick.net") != domainHash("doubleclick.com"))
  }
}
