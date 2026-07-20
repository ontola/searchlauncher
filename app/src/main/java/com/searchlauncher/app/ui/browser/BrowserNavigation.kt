package com.searchlauncher.app.ui.browser

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun browserDestination(input: String): String {
  val trimmed = input.trim()
  if (
    trimmed.startsWith("https://", ignoreCase = true) ||
      trimmed.startsWith("http://", ignoreCase = true)
  ) {
    return trimmed
  }

  if (!trimmed.contains(' ') && (trimmed.contains('.') || trimmed.startsWith("localhost"))) {
    return "https://$trimmed"
  }

  val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())
  return "https://www.google.com/search?q=$encoded"
}
