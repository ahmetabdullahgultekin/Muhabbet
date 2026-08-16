package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.model.ContentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * Guards the resource bundles themselves. The adapter looks up `push.content.<TYPE>` by enum name,
 * so adding a [ContentType] without adding its line ships a push path that throws
 * `NoSuchMessageException` — and since every push is wrapped in a catch-and-log, the only symptom
 * would be a notification that silently never arrives.
 *
 * No Spring context: the bean is built the same way [NotificationTextConfig] builds it, so this
 * runs without Docker and without a container start.
 */
class NotificationTextCatalogTest {

    private val adapter = MessageSourceNotificationTextAdapter(NotificationTextConfig().notificationMessageSource())

    private val turkish = Locale.forLanguageTag("tr")
    private val english = Locale.ENGLISH

    @Test
    fun `should resolve a summary for every content type in Turkish`() {
        ContentType.entries.forEach { type ->
            val summary = adapter.contentSummary(type, turkish)
            assertTrue(summary.isNotBlank(), "No Turkish push summary for $type")
        }
    }

    @Test
    fun `should resolve a summary for every content type in English`() {
        ContentType.entries.forEach { type ->
            val summary = adapter.contentSummary(type, english)
            assertTrue(summary.isNotBlank(), "No English push summary for $type")
        }
    }

    @Test
    fun `should give each content type a distinct summary`() {
        val summaries = ContentType.entries.map { adapter.contentSummary(it, turkish) }

        assertEquals(
            summaries.size,
            summaries.toSet().size,
            "Two content types share a tray line, so the reader cannot tell them apart: $summaries"
        )
    }

    @Test
    fun `should read the bundles as UTF-8`() {
        // Turkish push text is full of ğ/ş/ı and the summaries lead with an emoji. Read as
        // ISO-8859-1 they arrive as mojibake, which no assertion on "is not blank" would catch.
        assertTrue(
            adapter.contentSummary(ContentType.IMAGE, turkish).contains("Fotoğraf"),
            "Turkish characters did not survive bundle loading"
        )
        assertFalse(
            adapter.contentSummary(ContentType.IMAGE, turkish).contains("Ã"),
            "Bundle was decoded with the wrong charset"
        )
    }

    @Test
    fun `should translate the summary when the locale differs`() {
        assertEquals("📷 Fotoğraf", adapter.contentSummary(ContentType.IMAGE, turkish))
        assertEquals("📷 Photo", adapter.contentSummary(ContentType.IMAGE, english))
    }

    @Test
    fun `should compose a group title from sender and group`() {
        assertEquals("Ayşe · Aile", adapter.groupTitle("Ayşe", "Aile", turkish))
        assertEquals("Ayşe · Aile", adapter.groupTitle("Ayşe", "Aile", english))
    }

    @Test
    fun `should fall back to the default bundle for a locale with no translation`() {
        // fallbackToSystemLocale is off, so an unknown locale must land on Turkish rather than on
        // whatever language the JVM happens to be running in.
        assertEquals(
            adapter.contentSummary(ContentType.VOICE, turkish),
            adapter.contentSummary(ContentType.VOICE, Locale.forLanguageTag("ja"))
        )
    }

    @Test
    fun `should resolve the unknown-sender stand-in in both locales`() {
        assertEquals("Bilinmeyen", adapter.unknownSender(turkish))
        assertEquals("Unknown", adapter.unknownSender(english))
    }
}
