package com.searchlauncher.app.util

import java.util.Locale

object FuzzyMatch {
  fun calculateScore(
    preparedQuery: String,
    preparedTarget: String,
    targetWords: List<String>,
    acronym: String,
    queryMask: Long = 0L,
    targetMask: Long = 0L,
  ): Int {
    if (preparedQuery.isEmpty()) return 0

    return when {
      // 1. Exact Match
      preparedQuery == preparedTarget -> 100

      // 2. Prefix Match (Starts with query)
      preparedTarget.startsWith(preparedQuery) -> 90

      // 3. Word Boundary (e.g., "Store" in "Play Store")
      targetWords.any { it.startsWith(preparedQuery) } -> 85

      // 4. Acronym Match (Use pre-computed acronym)
      acronym.startsWith(preparedQuery) -> 80

      // 5. Contains Match (e.g., "goog" in "com.google.android")
      preparedTarget.contains(preparedQuery) -> 70

      // 6. Subsequence (Scattered characters)
      // Fast path: if target doesn't contain all query characters, skip isSubsequence
      queryMask != 0L && (queryMask and targetMask) != queryMask -> 0
      isSubsequence(preparedQuery, preparedTarget) ->
        maxOf(10, 60 - (preparedTarget.length - preparedQuery.length))
      else -> 0
    }
  }

  // Backward compatibility
  fun calculateScore(query: String, target: String): Int {
    val q = query.lowercase(Locale.getDefault()).trim()
    val t = target.lowercase(Locale.getDefault()).trim()
    val words = t.split(' ').filter { it.isNotEmpty() }
    val acronym = words.mapNotNull { it.firstOrNull() }.joinToString("")
    return calculateScore(q, t, words, acronym)
  }

  // Checks if characters of Q appear in T in the correct order
  private fun isSubsequence(q: String, t: String): Boolean {
    var i = 0
    for (char in t) {
      if (i < q.length && char == q[i]) i++
    }
    return i == q.length
  }
}
