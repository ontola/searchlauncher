package com.searchlauncher.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FuzzyMatchTest {

    @Test
    fun `Exact match returns 100`() {
        assertEquals(100, FuzzyMatch.calculateScore("spotify", "Spotify"))
        assertEquals(100, FuzzyMatch.calculateScore("Camera", "  camera"))
    }

    @Test
    fun `Prefix match returns 90`() {
        assertEquals(90, FuzzyMatch.calculateScore("spot", "   Spotify"))
        assertEquals(90, FuzzyMatch.calculateScore("goo", "Google"))
    }

    @Test
    fun `Word Boundary match returns 85`() {
        // Matches start of the second word
        assertEquals(85, FuzzyMatch.calculateScore("store", "Play Store"))
        assertEquals(85, FuzzyMatch.calculateScore("crush", "Candy Crush"))
    }

    @Test
    fun `Acronym match returns 80`() {
        // "P"lay "S"tore
        assertEquals(80, FuzzyMatch.calculateScore("ps", "Play Store"))
        // "C"all "o"f "D"uty
        assertEquals(80, FuzzyMatch.calculateScore("cod", "Call of Duty"))
    }

    @Test
    fun `Contains match returns 70`() {
        // "book" is inside "Facebook", but not at the start
        assertEquals(70, FuzzyMatch.calculateScore("book", "Facebook"))
        assertEquals(70, FuzzyMatch.calculateScore("tube", "YouTube"))
    }

    @Test
    fun `Subsequence match returns calculated score`() {
        // Logic: 60 - (Length Difference)
        // T: "Spotify" (7 chars)
        // Q: "spf" (3 chars)
        // Diff: 4
        // Expected: 60 - 4 = 56
        assertEquals(56, FuzzyMatch.calculateScore("spf", "Spotify"))
    }

    @Test
    fun `No match returns 0`() {
        assertEquals(0, FuzzyMatch.calculateScore("xyz", "Spotify"))
        assertEquals(0, FuzzyMatch.calculateScore("music", "Calculator"))
    }

    @Test
    fun `Empty query returns 0`() {
        assertEquals(0, FuzzyMatch.calculateScore("", "    Spotify  "))
        assertEquals(0, FuzzyMatch.calculateScore("", "Spotify"))
    }

    @Test
    fun `Priority Order Check`() {
        // "Settings" starts with "Se" ( 90)
        // "Se" is also a subsequence
        // Should return 90, not the subsequence score
        assertEquals(90, FuzzyMatch.calculateScore("Se", "Settings"))
    }
}