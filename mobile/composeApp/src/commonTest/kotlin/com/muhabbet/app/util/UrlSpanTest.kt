package com.muhabbet.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where a URL inside message text starts and ends (#362).
 *
 * This decides two things at once: which characters the chat bubble underlines and makes tappable,
 * and which string the link-preview card fetches. They used to be decided by two different regexes.
 */
class UrlSpanTest {

    @Test
    fun findsAUrlOnItsOwn() {
        val span = findUrlSpans("https://muhabbet.com").single()

        assertEquals("https://muhabbet.com", span.url)
        assertEquals(0, span.start)
        assertEquals(20, span.endExclusive)
    }

    @Test
    fun findsAUrlInsideASentence() {
        val text = "Şuna bak https://muhabbet.com hemen"

        val span = findUrlSpans(text).single()

        assertEquals("https://muhabbet.com", span.url)
        assertEquals("https://muhabbet.com", text.substring(span.start, span.endExclusive))
    }

    @Test
    fun findsEveryUrlInTheText() {
        val urls = findUrlSpans("https://a.example ve http://b.example").map { it.url }

        assertEquals(listOf("https://a.example", "http://b.example"), urls)
    }

    @Test
    fun keepsANonAsciiTurkishPath() {
        // The regex this replaced in LinkPreviewCard was ASCII-only and cut the path at "ü".
        assertEquals(
            "https://site.com/ürünler",
            findUrlSpans("https://site.com/ürünler").single().url
        )
    }

    @Test
    fun dropsATurkishCaseSuffixAttachedWithAnApostrophe() {
        // "Look at muhabbet.com" in Turkish attaches the dative suffix to the address itself.
        // Swallowing it produced a 404 for every such message.
        val text = "https://muhabbet.com'a bak"

        val span = findUrlSpans(text).single()

        assertEquals("https://muhabbet.com", span.url)
        // The apostrophe and suffix stay visible as ordinary text outside the tappable range.
        assertEquals("'a bak", text.substring(span.endExclusive))
    }

    @Test
    fun dropsATurkishSuffixAttachedWithATypographicApostrophe() {
        assertEquals(
            "https://muhabbet.com",
            findUrlSpans("https://muhabbet.com’dan indirdim").single().url
        )
    }

    @Test
    fun dropsSentencePunctuationTheUrlWouldOtherwiseSwallow() {
        assertEquals("https://muhabbet.com", findUrlSpans("Adres https://muhabbet.com.").single().url)
        assertEquals("https://muhabbet.com", findUrlSpans("https://muhabbet.com, sonra").single().url)
        assertEquals("https://muhabbet.com", findUrlSpans("Gerçekten https://muhabbet.com!").single().url)
        assertEquals("https://muhabbet.com", findUrlSpans("Şurada mı https://muhabbet.com?").single().url)
    }

    @Test
    fun keepsAQueryStringAndFragment() {
        assertEquals(
            "https://muhabbet.com/ara?q=kedi&sayfa=2#sonuc",
            findUrlSpans("https://muhabbet.com/ara?q=kedi&sayfa=2#sonuc").single().url
        )
    }

    @Test
    fun keepsABracketThatBelongsToTheUrl() {
        assertEquals(
            "https://tr.wikipedia.org/wiki/Kedi_(hayvan)",
            findUrlSpans("https://tr.wikipedia.org/wiki/Kedi_(hayvan)").single().url
        )
    }

    @Test
    fun dropsABracketThatCloseTheSentenceAroundTheUrl() {
        assertEquals(
            "https://muhabbet.com",
            findUrlSpans("(bkz: https://muhabbet.com)").single().url
        )
    }

    @Test
    fun findsNothingInTextWithoutAUrl() {
        assertTrue(findUrlSpans("Merhaba, nasılsın? İyi günler.").isEmpty())
        assertNull(firstUrlOrNull("Merhaba"))
    }

    @Test
    fun ignoresASchemeWithNothingAfterIt() {
        assertTrue(findUrlSpans("https://").isEmpty())
        // Trimming can also empty a match out. Without the length guard this would underline
        // "https://" and hand the platform a link to nowhere.
        assertTrue(findUrlSpans("https://'a").isEmpty())
        assertTrue(findUrlSpans("https://.").isEmpty())
    }

    @Test
    fun firstUrlOrNullReturnsTheSameStringTheBubbleWouldLink() {
        // The preview card and the tappable range must never disagree about the address.
        val text = "Bence https://muhabbet.com'a bak."

        assertEquals(findUrlSpans(text).first().url, firstUrlOrNull(text))
    }

    @Test
    fun parseFormattedTextStillMarksLinksAndKeepsFormatting() {
        // The URL detector is shared with parseFormattedText; changing it must not change how
        // *bold* and _italic_ are segmented.
        val segments = parseFormattedText("*kalın* https://muhabbet.com _eğik_")

        assertTrue(segments.any { it.isBold && it.text == "kalın" })
        assertTrue(segments.any { it.isItalic && it.text == "eğik" })
        assertEquals("https://muhabbet.com", segments.single { it.isLink }.linkUrl)
    }
}
