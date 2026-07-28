package com.searchlauncher.app.ui.browser

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Arrays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Domain-based content blocker backed by a hosts-format filter list.
 *
 * Requests are matched by host rather than by full Adblock Plus rule syntax: that covers the vast
 * majority of ads and trackers (which live on dedicated domains) while keeping lookups to a binary
 * search, which matters because [shouldBlock] runs for every subresource on every page.
 *
 * Domains are stored as sorted 64-bit hashes instead of strings — roughly 1 MB for a 130k-entry
 * list instead of ~5 MB, and the parsed form is cached to disk so start-up never re-parses the
 * multi-megabyte source list.
 */
internal object AdBlocker {
  /** StevenBlack's unified hosts list: widely trusted, plain hosts format, updated frequently. */
  const val DEFAULT_FILTER_LIST_URL =
    "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"

  private const val CACHE_FILE_NAME = "adblock_domains.bin"
  private const val CACHE_MAGIC = 0x41444231 // "ADB1"
  private const val REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
  private const val MAX_DOWNLOAD_BYTES = 32L * 1024 * 1024

  @Volatile private var domainHashes: LongArray? = null
  @Volatile private var loadAttempted = false

  val domainCount: Int
    get() = domainHashes?.size ?: 0

  /**
   * Loads the cached list into memory if needed, downloading it when absent or older than the
   * refresh interval. Safe to call repeatedly; work only happens once per process.
   */
  suspend fun ensureLoaded(context: Context) {
    if (domainHashes != null || loadAttempted) return
    loadAttempted = true
    withContext(Dispatchers.IO) {
      val cache = cacheFile(context)
      if (cache.exists()) {
        domainHashes = runCatching { readCache(cache) }.getOrNull()
        if (System.currentTimeMillis() - cache.lastModified() < REFRESH_INTERVAL_MS) {
          return@withContext
        }
      }
      // A failed refresh keeps whatever list is already loaded.
      update(context)
    }
  }

  /** Downloads and installs the filter list, returning the number of blocked domains. */
  suspend fun update(context: Context): Result<Int> =
    withContext(Dispatchers.IO) {
      runCatching {
        val hashes = download(DEFAULT_FILTER_LIST_URL)
        check(hashes.isNotEmpty()) { "Filter list contained no domains" }
        writeCache(cacheFile(context), hashes)
        domainHashes = hashes
        loadAttempted = true
        hashes.size
      }
    }

  /** True when [requestUrl]'s host, or any parent domain of it, is on the filter list. */
  fun shouldBlock(requestUrl: String): Boolean {
    val hashes = domainHashes ?: return false
    if (hashes.isEmpty()) return false
    val host = runCatching { URI(requestUrl).host }.getOrNull() ?: return false
    return matchesBlockedDomain(host, hashes)
  }

  /** Empty 200 response. Ad scripts and pixels treat this as a successful but empty fetch. */
  fun blockedResponse(): WebResourceResponse =
    WebResourceResponse(
      "text/plain",
      "utf-8",
      200,
      "OK",
      emptyMap(),
      ByteArrayInputStream(ByteArray(0)),
    )

  private fun cacheFile(context: Context): File = File(context.filesDir, CACHE_FILE_NAME)

  private fun download(url: String): LongArray {
    val connection =
      (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        requestMethod = "GET"
      }
    try {
      check(connection.responseCode == HttpURLConnection.HTTP_OK) {
        "Filter list download failed with HTTP ${connection.responseCode}"
      }
      val domains = LongBuffer()
      var readBytes = 0L
      BufferedInputStream(connection.inputStream).bufferedReader().forEachLine { line ->
        readBytes += line.length + 1
        check(readBytes <= MAX_DOWNLOAD_BYTES) { "Filter list exceeded size limit" }
        parseHostsLine(line)?.let { domains.add(domainHash(it)) }
      }
      return domains.toSortedDistinctArray()
    } finally {
      connection.disconnect()
    }
  }

  private fun readCache(file: File): LongArray? {
    DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
      if (input.readInt() != CACHE_MAGIC) return null
      val count = input.readInt()
      if (count <= 0) return null
      return LongArray(count) { input.readLong() }
    }
  }

  private fun writeCache(file: File, hashes: LongArray) {
    val temporary = File(file.parentFile, "${file.name}.tmp")
    DataOutputStream(temporary.outputStream().buffered()).use { output ->
      output.writeInt(CACHE_MAGIC)
      output.writeInt(hashes.size)
      hashes.forEach(output::writeLong)
    }
    check(temporary.renameTo(file)) { "Could not replace filter list cache" }
  }

  /** Growable long array; avoids boxing ~100k values while parsing. */
  private class LongBuffer {
    private var values = LongArray(4096)
    private var size = 0

    fun add(value: Long) {
      if (size == values.size) values = values.copyOf(size * 2)
      values[size++] = value
    }

    fun toSortedDistinctArray(): LongArray {
      val sorted = values.copyOf(size)
      sorted.sort()
      if (sorted.isEmpty()) return sorted
      var unique = 1
      for (index in 1 until sorted.size) {
        if (sorted[index] != sorted[unique - 1]) sorted[unique++] = sorted[index]
      }
      return sorted.copyOf(unique)
    }
  }
}

/** Hosts entries that map loopback names rather than naming an ad server. */
private val IGNORED_BLOCKLIST_HOSTS =
  setOf("localhost", "localhost.localdomain", "local", "broadcasthost", "ip6-localhost")

private val BLOCKLIST_IP_PREFIXES = setOf("0.0.0.0", "127.0.0.1", "::1", "::")

/**
 * Extracts the domain from a hosts line ("0.0.0.0 ads.example.com # comment"). Also accepts plain
 * domain-per-line lists so alternative filter sources work unchanged.
 */
internal fun parseHostsLine(rawLine: String): String? {
  val line = rawLine.substringBefore('#').trim()
  if (line.isEmpty()) return null
  val tokens = line.split(' ', '\t').filter { it.isNotEmpty() }
  val domain =
    when {
      tokens.size >= 2 && tokens[0] in BLOCKLIST_IP_PREFIXES -> tokens[1]
      tokens.size == 1 -> tokens[0]
      else -> return null
    }.lowercase()

  if (domain in IGNORED_BLOCKLIST_HOSTS || !domain.contains('.')) return null
  if (domain.any { it !in 'a'..'z' && it !in '0'..'9' && it != '.' && it != '-' }) return null
  return domain
}

/** FNV-1a: stable across processes and runs, unlike [String.hashCode]'s 32-bit output. */
internal fun domainHash(domain: String): Long {
  var result = -0x340d631b7bdddcdbL
  for (char in domain) {
    result = result xor char.code.toLong()
    result *= 0x100000001b3L
  }
  return result
}

/**
 * Matches [host] and each of its parent domains against [sortedHashes]. Bare single labels are
 * never matched, so a stray "com" entry in a filter list cannot take down the web.
 */
internal fun matchesBlockedDomain(host: String, sortedHashes: LongArray): Boolean {
  val normalizedHost = host.lowercase()
  var start = 0
  while (start < normalizedHost.length) {
    val candidate = normalizedHost.substring(start)
    if (!candidate.contains('.')) return false
    if (Arrays.binarySearch(sortedHashes, domainHash(candidate)) >= 0) return true
    start = normalizedHost.indexOf('.', start).takeIf { it >= 0 }?.plus(1) ?: return false
  }
  return false
}

/** Builds the sorted hash array [matchesBlockedDomain] expects from a list of domains. */
internal fun blocklistHashesOf(domains: List<String>): LongArray =
  domains.map(::domainHash).toLongArray().also(LongArray::sort)
