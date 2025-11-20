package com.searchlauncher.app.util

import java.util.Locale

object FuzzyMatch {
    fun calculateScore(query: String, target: String): Int {
        if (query.isEmpty()) return 0

        val q = query.lowercase(Locale.getDefault()).trim()
        val t = target.lowercase(Locale.getDefault()).trim()
        val words = t.split(Regex("\\s+")) // Handles multiple spaces safely

        return when {
            // 1. Exact Match
            q == t -> 100

            // 2. Prefix Match (Starts with query)
            t.startsWith(q) -> 90

            // 3. Word Boundary (e.g., "Store" in "Play Store")
            words.any { it.startsWith(q) } -> 85

            // 4. Acronym Match (e.g., "ps" -> "Play Store")
            words.mapNotNull { it.firstOrNull() }.joinToString("").startsWith(q) -> 80

            // 5. Contains Match (e.g., "goog" in "com.google.android")
            t.contains(q) -> 70

            // 6. Subsequence (Scattered characters, e.g., "spf" -> "Spotify")
            isSubsequence(q, t) -> maxOf(10, 60 - (t.length - q.length))

            else -> 0
        }
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