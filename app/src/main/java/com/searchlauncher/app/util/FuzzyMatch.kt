package com.searchlauncher.app.util

import java.util.Locale

object FuzzyMatch {
    fun calculateScore(query: String, target: String): Int {
        if (query.isEmpty()) return 0
        val q = query.lowercase(Locale.getDefault())
        val t = target.lowercase(Locale.getDefault())

        // 1. Exact Match
        if (q == t) return 100

        // 2. Prefix Match
        if (t.startsWith(q)) return 90

        // 3. Word Boundary Match (e.g. "ps" -> "Play Store")
        // Split target into words
        val words = t.split(" ")
        if (words.any { it.startsWith(q) }) return 85

        // Check acronyms (first letter of each word)
        var acronym = ""
        words.forEach { if(it.isNotEmpty()) acronym += it[0] }
        if (acronym.startsWith(q)) return 80

        // 4. Contains Match
        if (t.contains(q)) return 70

        // 5. Subsequence Match (scattered characters)
        // "spf" -> "Spotify"
        var qIdx = 0
        var tIdx = 0
        var firstMatchIdx = -1

        while (qIdx < q.length && tIdx < t.length) {
            if (q[qIdx] == t[tIdx]) {
                if (firstMatchIdx == -1) firstMatchIdx = tIdx
                qIdx++
            }
            tIdx++
        }

        if (qIdx == q.length) {
            // Found all characters in order
            val matchSpread = (tIdx - 1) - firstMatchIdx + 1
            // Score based on compactness.
            // Ideally spread == query.length.
            // Score = 60 - (penalty for spread)
            // Penalty = spread - query.length
            val penalty = matchSpread - q.length
            // Only accept if spread isn't too huge (e.g. "g" ... ... "t" ... "s" in a long text)
            if (penalty < 20) { // Tunable
                return maxOf(10, 60 - penalty)
            }
        }

        return 0
    }
}
