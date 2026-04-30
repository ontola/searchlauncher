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
