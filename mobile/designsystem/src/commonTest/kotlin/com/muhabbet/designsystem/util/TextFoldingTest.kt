package com.muhabbet.designsystem.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextFoldingTest {

    /**
     * The bug this function exists for. `"İsmail".lowercase()` under root-locale rules is
     * `"i̇smail"` — a plain `i` followed by a combining dot — so the substring check against a
     * user's typed `"ismail"` failed, and the contact never appeared in search results.
     */
    @Test
    fun `should match a dotted capital I name when user types plain ascii i`() {
        assertTrue(foldForSearch("İsmail").contains(foldForSearch("ismail")))
        assertEquals("ismail", foldForSearch("İsmail"))
    }

    @Test
    fun `should drop the combining dot that root-locale lowercasing leaves behind`() {
        // Exactly what "İ".lowercase() produces; folding it must not leave the artefact behind.
        assertEquals("i", foldForSearch("i̇"))
    }

    @Test
    fun `should fold all four i-shaped characters together when searching`() {
        val folded = setOf("I", "ı", "İ", "i").map { foldForSearch(it) }.toSet()
        assertEquals(setOf("i"), folded)
    }

    @Test
    fun `should match a dotless i name when user types plain ascii i`() {
        assertTrue(foldForSearch("Işıl").contains(foldForSearch("isil")))
    }

    /**
     * Diacritic-insensitive on purpose: Turkish is routinely typed without diacritics, so a list
     * that only matches `şarkı` when you type `şarkı` is a list you cannot search.
     */
    @Test
    fun `should fold Turkish diacritics away so an ascii query matches`() {
        assertEquals("gscou", foldForSearch("ĞŞÇÖÜ"))
        assertEquals("sarki", foldForSearch("Şarkı"))
        assertTrue(foldForSearch("Gülşah").contains(foldForSearch("gulsah")))
    }

    @Test
    fun `should still match when the query itself carries the diacritics`() {
        assertTrue(foldForSearch("Gülşah").contains(foldForSearch("Gülşah")))
    }

    @Test
    fun `should leave a phone number unchanged`() {
        assertEquals("+905000000001", foldForSearch("+905000000001"))
    }

    @Test
    fun `should return empty for empty input`() {
        assertEquals("", foldForSearch(""))
    }
}
