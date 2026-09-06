package com.searchlauncher.app.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

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
      else -> {
        val typoScore = typoFuzzyScore(preparedQuery, preparedTarget, targetWords)
        when {
          // 6. Typo-tolerant match. Keep this below intentional prefix/acronym/contains matches.
          typoScore > 0 -> typoScore

          // 7. Subsequence (Scattered characters)
          // Fast path: if target doesn't contain all query characters, skip isSubsequence
          queryMask != 0L && (queryMask and targetMask) != queryMask -> 0
          isSubsequence(preparedQuery, preparedTarget) ->
            maxOf(10, 60 - (preparedTarget.length - preparedQuery.length))
          else -> 0
        }
      }
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

  private fun typoFuzzyScore(query: String, target: String, targetWords: List<String>): Int {
    if (query.length < 3) return 0

    val maxDistance = if (query.length <= 4) 1 else 2
    var bestDistance = Int.MAX_VALUE
    fun consider(candidate: String) {
      if (candidate.length < 3 || candidate.length > MAX_TYPO_TARGET_LENGTH) return
      if (abs(candidate.length - query.length) > maxDistance) return

      val distance = boundedDamerauLevenshtein(query, candidate, maxDistance)
      // maxDistance + 1 is the "too far" sentinel, not a valid typo match.
      if (distance <= maxDistance && distance < bestDistance) bestDistance = distance
    }

    for (word in targetWords) {
      consider(word)
      if (bestDistance == 0) break
    }
    if (bestDistance != 0 && (targetWords.size != 1 || targetWords[0] != target)) {
      consider(target)
    }
    if (bestDistance != 0 && ' ' in target) {
      // Reject impossible lengths before allocating a compact copy for every indexed document.
      val compactLength = target.count { it != ' ' }
      if (
        compactLength in 3..MAX_TYPO_TARGET_LENGTH &&
          abs(compactLength - query.length) <= maxDistance
      ) {
        consider(target.replace(" ", ""))
      }
    }

    return when (bestDistance) {
      1 -> 68
      2 -> 58
      else -> 0
    }
  }

  private fun boundedDamerauLevenshtein(a: String, b: String, maxDistance: Int): Int {
    if (a == b) return 0
    if (abs(a.length - b.length) > maxDistance) return maxDistance + 1

    var previousPrevious = IntArray(b.length + 1)
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)

    for (i in 1..a.length) {
      current[0] = i
      var rowMin = current[0]

      for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        var value = min(min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost)

        if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
          value = min(value, previousPrevious[j - 2] + 1)
        }

        current[j] = value
        if (value < rowMin) rowMin = value
      }

      if (rowMin > maxDistance) return maxDistance + 1

      val temp = previousPrevious
      previousPrevious = previous
      previous = current
      current = temp
    }

    return previous[b.length]
  }

  private const val MAX_TYPO_TARGET_LENGTH = 32
}
