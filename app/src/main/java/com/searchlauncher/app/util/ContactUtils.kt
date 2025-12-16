package com.searchlauncher.app.util

object ContactUtils {
  /**
   * Returns a list of indexable variants for a phone number. Includes:
   * - Pure digits (e.g. "31612345678")
   * - Permission-based local variants (e.g. "316..." -> "06...")
   */
  fun getIndexableVariants(original: String?): List<String> {
    val normalized = normalizePhoneNumber(original) ?: return emptyList()
    val variants = mutableListOf<String>()
    variants.add(normalized)

    // Handle common Dutch format: +31 6... -> 06...
    if (normalized.startsWith("31")) {
      variants.add("0" + normalized.substring(2))
    }

    return variants
  }

  /**
   * Normalizes a phone number for indexing. Strips all non-digit characters. Returns the normalized
   * string if it contains digits.
   */
  fun normalizePhoneNumber(original: String?): String? {
    if (original.isNullOrEmpty()) return null
    val normalized = original.replace(Regex("[^0-9]"), "")
    if (normalized.isEmpty()) return null
    return normalized
  }
}
