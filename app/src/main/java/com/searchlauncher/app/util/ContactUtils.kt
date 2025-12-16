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

    // Handle 00 prefix (e.g. 0031...) by stripping 00
    val clean = if (normalized.startsWith("00")) normalized.substring(2) else normalized

    // Standard International (e.g. 31...)
    if (clean != normalized) variants.add(clean)

    // Handle common Dutch format: 31 6... -> 06...
    if (clean.startsWith("316")) {
      variants.add("0" + clean.substring(2))
    }
    // Handle generic replacement of country code if needed?
    // For now, focusing on the specific user request (06... matching +31...)
    if (normalized.startsWith("316")) { // Handle +31 (normalized to 31)
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
