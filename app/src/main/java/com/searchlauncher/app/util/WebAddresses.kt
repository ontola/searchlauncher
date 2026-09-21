package com.searchlauncher.app.util

/**
 * Turns a typed query into an http(s) address, or returns null when it should stay a search.
 *
 * [android.util.Patterns.WEB_URL] is not a reliable check. Its expression is built around a
 * character class Android's regex engine mishandles, so real hosts — multi-label names such as
 * `ontola.staging.atomicserver.eu`, `localhost`, bare IP addresses — fail to match on some releases
 * while shorter names still match. This looks at the shape of the string instead: an explicit
 * http(s) URL, or a host the browser can open.
 */
internal fun webAddressUrl(input: String): String? {
  val trimmed = input.trim()
  if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
  val schemeLength = httpSchemeLength(trimmed)
  if (schemeLength != null) {
    if (!isHttpRequestTarget(trimmed.substring(schemeLength), explicitScheme = true)) return null
    return trimmed
  }
  // `ftp://…` already names a scheme. Prefixing https:// would make a nonsense address.
  if ("://" in trimmed) return null
  if (!isHttpRequestTarget(trimmed, explicitScheme = false)) return null
  return "https://$trimmed"
}

/** Length of a leading `http://` or `https://`, matched without regard to case. */
private fun httpSchemeLength(value: String): Int? {
  if (value.length >= 8 && value.regionMatches(0, "https://", 0, 8, ignoreCase = true)) return 8
  if (value.length >= 7 && value.regionMatches(0, "http://", 0, 7, ignoreCase = true)) return 7
  return null
}

/**
 * [value] is `host[:port][/path][?query][#fragment]`, optionally with userinfo when the caller
 * already saw an http(s) scheme.
 *
 * Without a scheme, a single label is only accepted for `localhost`: `printer` is a search, while
 * `printer.local` and `192.168.0.1` are addresses. An explicit scheme opts into single-label hosts
 * (`http://printer`) and `user:pass@host`.
 */
private fun isHttpRequestTarget(value: String, explicitScheme: Boolean): Boolean {
  if (value.isEmpty()) return false
  var rest = value
  val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
  val authority = if (authorityEnd < 0) rest else rest.substring(0, authorityEnd)
  val at = authority.lastIndexOf('@')
  if (at >= 0) {
    if (!explicitScheme || at == 0 || at == authority.lastIndex) return false
    rest = rest.substring(at + 1)
  }

  val afterHost =
    if (rest.startsWith('[')) {
      val bracket = rest.indexOf(']')
      if (bracket < 2 || !isIpv6(rest.substring(1, bracket))) return false
      rest.substring(bracket + 1)
    } else {
      val hostEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' || it == ':' }
      val host = if (hostEnd < 0) rest else rest.substring(0, hostEnd)
      if (!isHostname(host, requireDottedName = !explicitScheme)) return false
      if (hostEnd < 0) "" else rest.substring(hostEnd)
    }
  if (afterHost.isEmpty()) return true
  if (afterHost[0] == ':') return hasPort(afterHost.substring(1))
  return afterHost[0] == '/' || afterHost[0] == '?' || afterHost[0] == '#'
}

private fun hasPort(value: String): Boolean {
  val digits = value.takeWhile { it.isDigit() }
  if (digits.isEmpty() || digits.length > 5) return false
  val port = digits.toIntOrNull() ?: return false
  if (port !in 0..65535) return false
  val remainder = value.substring(digits.length)
  return remainder.isEmpty() || remainder[0] == '/' || remainder[0] == '?' || remainder[0] == '#'
}

/**
 * A DNS name, IPv4 address, or `localhost`. A trailing dot (the absolute form, `example.com.`) is
 * kept by the caller and ignored here. [requireDottedName] demands a multi-label name whose last
 * label looks like a TLD, so `3.14` and `readme` stay searches while `atomicserver.eu` does not.
 */
private fun isHostname(host: String, requireDottedName: Boolean): Boolean {
  if (host.equals("localhost", ignoreCase = true)) return true
  val name = host.removeSuffix(".")
  if (name.isEmpty() || name.endsWith('.') || name.length > 253) return false
  if (isIpv4(name)) return true
  if (name.startsWith('.') || ".." in name) return false
  val labels = name.split('.')
  if (labels.any { !isDnsLabel(it) }) return false
  if (!requireDottedName) return true
  if (labels.size < 2) return false
  val tld = labels.last()
  return tld.length >= 2 && tld.any { it.isLetter() }
}

/** One DNS label: 1–63 characters, not starting or ending with a hyphen. `_` is allowed. */
private fun isDnsLabel(label: String): Boolean {
  if (label.isEmpty() || label.length > 63) return false
  if (label.first() == '-' || label.last() == '-') return false
  return label.all { it.isLetterOrDigit() || it == '-' || it == '_' }
}

private fun isIpv4(value: String): Boolean {
  val parts = value.split('.')
  if (parts.size != 4) return false
  return parts.all { part ->
    part.isNotEmpty() &&
      part.length <= 3 &&
      part.all { it.isDigit() } &&
      (part.toIntOrNull() ?: -1) in 0..255
  }
}

/** Textual IPv6, including the mixed form that ends in an IPv4 address. Not a full RFC parser. */
private fun isIpv6(value: String): Boolean {
  if (value.isEmpty() || value.length > 45 || ':' !in value || ":::" in value) return false
  val firstCompression = value.indexOf("::")
  if (firstCompression >= 0 && value.indexOf("::", firstCompression + 2) >= 0) return false
  val head =
    if ('.' in value) {
      val lastColon = value.lastIndexOf(':')
      if (lastColon < 0 || !isIpv4(value.substring(lastColon + 1))) return false
      value.substring(0, lastColon)
    } else {
      value
    }
  if (head.count { it == ':' } > 7) return false
  return head.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }
}
