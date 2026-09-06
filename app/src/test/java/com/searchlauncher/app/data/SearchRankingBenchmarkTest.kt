package com.searchlauncher.app.data

import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in host-JVM microbenchmark; these numbers are not Android frame or input latency. */
class SearchRankingBenchmarkTest {
  @Test
  fun measureRanking() {
    assumeTrue(System.getenv("SEARCHLAUNCHER_BENCHMARK") == "1")
    // Android's compile stubs omit java.management; the opt-in test runs on the host JDK.
    val allocations =
      Class.forName("java.lang.management.ManagementFactory")
        .getMethod("getThreadMXBean")
        .invoke(null)
    val allocationApi = Class.forName("com.sun.management.ThreadMXBean")
    allocationApi
      .getMethod("setThreadAllocatedMemoryEnabled", Boolean::class.javaPrimitiveType)
      .invoke(allocations, true)
    val readAllocatedBytes =
      allocationApi.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
    @Suppress("DEPRECATION") // Keep the host benchmark runnable on JDK 17.
    val threadId = Thread.currentThread().id
    val names =
      listOf(
        "Google Maps",
        "Spotify",
        "Settings",
        "WhatsApp Messenger",
        "Camera",
        "Calendar",
        "Jane de Vries",
        "John Smith",
        "Alice Johnson",
        "Sophie Williams",
        "Mohammed Ahmed",
        "Project meeting notes",
        "Quarterly budget report",
        "Holiday booking confirmation",
        "Team planning session",
        "Grocery shopping list",
        "Train ticket Amsterdam",
      )
    println("BENCH docs,query,p50_ms,p95_ms,bytes_per_query,result_count,checksum")
    for (size in listOf(1000, 10000)) {
      val snapshot =
        (0 until size).map { i ->
          val name = "${names[i % names.size]} ${i / names.size}".lowercase()
          val words = name.split(' ')
          val namespace = if (i % names.size in 6..10) "contacts" else "apps"
          SearchableDocument(
            doc =
              AppSearchDocument(namespace = namespace, id = i.toString(), score = 0, name = name),
            nameLower = name,
            targetWords = words,
            acronym = words.joinToString("") { it.take(1) },
            namespaceInt = if (namespace == "contacts") 5 else 1,
            normalizedPhone = if (namespace == "contacts") "316${10000000 + i}" else null,
            charMask = SearchRanker.calculateCharMask(name),
          )
        }
      val byId = snapshot.associateBy { "${it.doc.namespace}\u001F${it.doc.id}" }
      for (query in listOf("s", "set", "settings", "soptify", "jane", "316100", "qzx7")) {
        fun rank() =
          SearchRanker.rankCandidates(query, snapshot, true, emptyMap(), emptyMap(), byId)
        repeat(100) { sink = rank().size.toLong() }
        val times = LongArray(100)
        var checksum = 0L
        var count = 0
        val allocatedBefore = (readAllocatedBytes.invoke(allocations, threadId) as Long)
        repeat(times.size) { i ->
          val start = System.nanoTime()
          val results = rank()
          times[i] = System.nanoTime() - start
          count = results.size
          checksum +=
            results.fold(0L) { acc, result -> acc + result.first.doc.id.toLong() + result.second }
        }
        val bytes =
          ((readAllocatedBytes.invoke(allocations, threadId) as Long) - allocatedBefore) /
            times.size
        sink = checksum
        times.sort()
        println("BENCH $size,$query,${times[49] / 1e6},${times[94] / 1e6},$bytes,$count,$checksum")
      }
    }
  }

  companion object {
    @Volatile private var sink = 0L
  }
}
