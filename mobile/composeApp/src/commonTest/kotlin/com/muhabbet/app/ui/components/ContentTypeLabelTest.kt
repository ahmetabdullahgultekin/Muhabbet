package com.muhabbet.app.ui.components

import com.muhabbet.shared.model.ContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The rule behind #534: a message with no words of its own is *named* by the reading device, never
 * by whatever the sending device happened to write into the body.
 *
 * These assert which types are named and that no two share a name — not the rendered text.
 * Resolving the text needs a composition, and would in any case only ever prove the reader's own
 * locale, which was never the thing in doubt. What was wrong was that these types consulted the
 * stored body at all: the sender's app had written the word "Photo" into it, in the sender's
 * language, and the server kept that string forever.
 *
 * (The generated `Res.string.*` accessors are not visible from `commonTest`, so the resources
 * cannot be named here even if that were the better assertion.)
 */
class ContentTypeLabelTest {

    /**
     * Every type whose body is either absent, a label the sender's app invented, or a serialized
     * blob. None of these may be shown to a reader as-is.
     */
    private val named = listOf(
        ContentType.IMAGE,
        ContentType.VIDEO,
        ContentType.VOICE,
        ContentType.GIF,
        ContentType.STICKER,
        // Both of these put JSON in the body — without a label the list rendered a raw object.
        ContentType.LOCATION,
        ContentType.POLL
    )

    /**
     * TEXT is the message itself and DOCUMENT is the filename, both more use to a reader than the
     * word for their category. CONTACT has no label resource and falls through the same way.
     */
    private val deferring = listOf(ContentType.TEXT, ContentType.DOCUMENT, ContentType.CONTACT)

    @Test
    fun `should name the message from its type when the body is not readable text`() {
        named.forEach { type ->
            assertNotNull(
                contentTypeLabelResource(type),
                "$type has no label, so the list would fall back to the raw stored body"
            )
        }
    }

    @Test
    fun `should defer to the body when the message carries readable text of its own`() {
        deferring.forEach { type ->
            assertNull(contentTypeLabelResource(type), "$type should be shown as its own body")
        }
    }

    /**
     * A conversation read back from the on-device cache has no content type — there is no column
     * for it — so it must degrade to the stored preview rather than losing its subtitle entirely.
     */
    @Test
    fun `should defer to the body when the content type is unknown`() {
        assertNull(contentTypeLabelResource(null))
    }

    /**
     * A copy-paste in the `when` that gave two types the same label would be invisible in review
     * and would show a voice note as "Fotoğraf".
     */
    @Test
    fun `should give each named type a label of its own`() {
        val labels = named.map { contentTypeLabelResource(it) }

        assertEquals(named.size, labels.toSet().size, "two content types share a label resource")
    }

    /**
     * The guard against this fix rotting the way the bug arrived. A new `ContentType` added to the
     * shared enum and forgotten here would silently print whatever the sender's app put in the
     * body — which is the whole defect. The `when` is exhaustive so the compiler catches it first;
     * this catches someone answering the compiler with an `else`.
     */
    @Test
    fun `should have a considered answer for every content type the protocol defines`() {
        val covered = (named + deferring).toSet()

        assertEquals(
            emptySet(),
            ContentType.entries.toSet() - covered,
            "a content type exists that this test has no opinion about"
        )
    }
}
