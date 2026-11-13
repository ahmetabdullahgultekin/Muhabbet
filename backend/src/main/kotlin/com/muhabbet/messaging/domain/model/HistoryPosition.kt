package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.UUID

/**
 * A position in one conversation's history: the `(serverTimestamp, id)` of a message (#750, #876).
 *
 * The pair, not the timestamp alone. Paging on `server_timestamp < :before` with no tie-break
 * drops every row that shares the boundary row's timestamp — they are neither on the page that
 * ended at it nor, being equal rather than smaller, on the next one. `id` is unique, so the pair is
 * a total order and a keyset over it can neither skip nor repeat a row.
 *
 * On the wire it is opaque: [encode] produces a versioned, URL-safe token and clients hand it back
 * unread. The version prefix is what lets the shape change again (to the per-conversation sequence
 * #750 proposes) without a client noticing.
 */
data class MessageCursor(val serverTimestamp: Instant, val id: UUID) {

    fun encode(): String =
        ENCODER.encodeToString("$VERSION$SEPARATOR$serverTimestamp$SEPARATOR$id".toByteArray(Charsets.UTF_8))

    companion object {
        private const val VERSION = "v1"
        private const val SEPARATOR = '|'
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()

        /**
         * The smallest UUID, which makes a legacy cursor exactly as exclusive as it used to be: with
         * it, `(ts', id') < (ts, MIN)` holds precisely when `ts' < ts`.
         */
        private val MIN_ID = UUID(0L, 0L)

        fun of(message: Message): MessageCursor = MessageCursor(message.serverTimestamp, message.id)

        /**
         * Null for anything this server did not mint — a caller turns that into a 400. The previous
         * behaviour, falling back to the newest page, turned a corrupted cursor into a duplicate page
         * and an infinite scroll.
         *
         * A bare ISO-8601 instant is still accepted. That is what every cursor issued before this
         * change looked like, and a client mid-scroll across a deploy holds one; it decodes to the
         * same exclusive bound it always meant. This is the expand half of the change — the branch
         * can go once no client older than this release is in use.
         */
        fun decode(raw: String): MessageCursor? = decodeV1(raw) ?: decodeLegacy(raw)

        private fun decodeV1(raw: String): MessageCursor? {
            val text = try {
                String(DECODER.decode(raw), Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                return null
            }
            val parts = text.split(SEPARATOR)
            if (parts.size != 3 || parts[0] != VERSION) return null
            return try {
                MessageCursor(Instant.parse(parts[1]), UUID.fromString(parts[2]))
            } catch (_: DateTimeParseException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun decodeLegacy(raw: String): MessageCursor? = try {
            MessageCursor(Instant.parse(raw), MIN_ID)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

/** Which way a history request travels from its cursor. */
enum class HistoryDirection {
    /** Towards older messages — scrolling up. The default, and the only one the app sends today. */
    BEFORE,

    /** Towards newer messages — filling a gap after a reconnect, or reading on from a jump. */
    AFTER;

    companion object {
        fun fromWire(value: String): HistoryDirection? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/**
 * What one history request asks for (#876). Replaces a `cursor: String?` and a `direction: String`
 * that the domain never read: a client asking for newer messages used to receive older ones, with
 * `hasMore = true`, and page the wrong way.
 *
 * Results are returned in the direction of travel — newest first for [Latest] and [Before], oldest
 * first for [After] — so a client appends each page to the end it is reading towards, and the next
 * cursor is always the last item of the page.
 */
sealed interface HistoryPosition {

    /** The newest page. */
    data object Latest : HistoryPosition

    /** Messages strictly older than [cursor]. */
    data class Before(val cursor: MessageCursor) : HistoryPosition

    /** Messages strictly newer than [cursor]. */
    data class After(val cursor: MessageCursor) : HistoryPosition

    companion object {
        /**
         * Null for [HistoryDirection.AFTER] without a cursor: "newer than nothing" has no meaning a
         * client could want, and guessing one (the oldest page? the newest?) is how the old
         * contract ended up silently doing something other than what was asked.
         */
        fun of(direction: HistoryDirection, cursor: MessageCursor?): HistoryPosition? = when (direction) {
            HistoryDirection.BEFORE -> if (cursor == null) Latest else Before(cursor)
            HistoryDirection.AFTER -> cursor?.let { After(it) }
        }
    }
}
