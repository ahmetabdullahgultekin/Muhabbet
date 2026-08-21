package com.muhabbet.messaging.domain.service

import java.security.SecureRandom
import java.util.Base64

/** Bytes of entropy behind an invite token. 32 is 256 bits — not guessable, not enumerable. */
private const val INVITE_TOKEN_BYTES = 32

/**
 * Mints an invite token for a group ([InviteLinkService]) or a community ([CommunityInviteService]).
 *
 * One function for both because a token is the entire authorisation a link carries: whoever holds it
 * is admitted. That makes "how much entropy, from which generator" a security parameter rather than
 * a formatting choice, and a second copy is a second place for it to be weakened by accident.
 *
 * URL-safe and unpadded so the value drops into a `muhabbet://` link and a query string without
 * escaping, and fits the `VARCHAR(64)` column both tables declare (32 bytes base64 → 43 characters).
 */
internal fun generateInviteToken(): String {
    val bytes = ByteArray(INVITE_TOKEN_BYTES)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
