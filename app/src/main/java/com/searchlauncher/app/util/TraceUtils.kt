package com.searchlauncher.app.util

import android.os.Trace

inline fun <T> traceSection(name: String, block: () -> T): T {
  Trace.beginSection(name.take(127))
  return try {
    block()
  } finally {
    Trace.endSection()
  }
}

@PublishedApi internal val asyncTraceCookies = java.util.concurrent.atomic.AtomicInteger()

/** Async sections remain valid when a coroutine suspends or resumes on another thread. */
suspend inline fun <T> traceAsyncSection(name: String, block: suspend () -> T): T {
  if (!Trace.isEnabled()) return block()
  val sectionName = name.take(127)
  val cookie = asyncTraceCookies.getAndIncrement()
  Trace.beginAsyncSection(sectionName, cookie)
  return try {
    block()
  } finally {
    Trace.endAsyncSection(sectionName, cookie)
  }
}
