package com.muhabbet.messaging.domain.port.out

import java.util.UUID

/**
 * What a message is allowed to point its `mediaUrl` at (#679).
 *
 * A `message.send` frame carries the URL the recipient's device will fetch. Until this port existed
 * that string went from the sender's socket into `messages.media_url` and back out to every
 * recipient without anyone asking what it named, so a sender could aim every recipient's phone at
 * an address of their choosing — a request the recipient never made, made at the moment the bubble
 * composed. That leaks the recipient's IP to whoever owns the address and reports the instant they
 * opened the chat, which is a read receipt taken behind the back of the read-receipt setting #377
 * added a server-side gate for.
 *
 * Two questions, because there are two kinds of send and they can be answered with different
 * strength:
 *
 *  * A **fresh upload** carries the `mediaId` the upload response issued (#541), so the server can
 *    look the blob up and mint the URL itself — [resolveOwnUpload]. Nothing the client wrote is
 *    kept. This is the shape #719 asks for and the only one that makes `media_url` a server-owned
 *    value rather than a client-owned one.
 *  * A **forward** deliberately carries no `mediaId` — the blob belongs to whoever first sent it,
 *    and claiming it would let the forwarder destroy someone else's file. Neither does a GIF or a
 *    sticker, which the app sends straight off GIPHY's CDN, nor anything sent by a client built
 *    before `mediaId` existed. For those the URL is still the client's, and the only thing that can
 *    be checked is where it points: [isAllowedOrigin].
 *
 * The two are kept on one port because they are one decision — "may this message carry this
 * media" — asked twice. Splitting them would put two adapters between the same two modules.
 */
interface MediaAttachmentPolicyPort {

    /**
     * The URLs this server will publish for [mediaId], minted now, or null when it cannot vouch for
     * the id: no such object, an object [senderId] did not upload, or storage that would not answer.
     *
     * Null is "ask the other question", never "allow it" — the caller falls back to
     * [isAllowedOrigin] on whatever the client sent, which is no weaker than the rule that
     * applies to a forward.
     */
    fun resolveOwnUpload(mediaId: UUID, senderId: UUID): ResolvedAttachment?

    /**
     * True when [url] sits on an origin this deployment publishes media from: our own media host,
     * plus the closed list of third-party origins the app itself sends from — GIPHY, because the
     * GIF and sticker picker hands the client a CDN URL and never re-hosts it. The list is
     * `MediaProperties.attachmentOrigins`, and it is a *list* rather than a relaxation because
     * every harm in #679 needs the sender to own the server the recipient talks to.
     *
     * Deliberately narrower than "is this URL safe to fetch". `SsrfGuard` answers that one, and it
     * is the right guard for the server's own outbound fetches (link previews), where the danger is
     * an internal address. It is the wrong guard here: the fetch happens on the recipient's phone,
     * so a perfectly ordinary public host — an attacker's logging endpoint — is exactly the payload,
     * and every private-range check in the world passes it. What makes a URL acceptable here is not
     * that it is harmless but that the sender did not get to choose it.
     */
    fun isAllowedOrigin(url: String): Boolean
}

/** URLs the server resolved for a media object it confirmed the sender uploaded. */
data class ResolvedAttachment(val mediaUrl: String, val thumbnailUrl: String?)
