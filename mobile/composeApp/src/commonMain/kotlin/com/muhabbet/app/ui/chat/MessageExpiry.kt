package com.muhabbet.app.ui.chat

import com.muhabbet.shared.model.Message
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant

/**
 * When a disappearing message leaves the screen.
 *
 * Pure and separate from `ChatScreen` so the rule can be tested without a device, which matters
 * more than usual here: the defect in #513 was invisible to every test that existed because the
 * only thing that ever removed an expired message was navigating away and re-fetching.
 *
 * The client removing messages on its own is not a substitute for the server's sweep — the server
 * still deletes the row, and still tells everyone through `WsMessage.MessageExpired`. It is what
 * makes the removal happen *at the deadline* rather than up to a minute later plus a round trip,
 * for the person sitting and watching the timer they just set.
 */

/**
 * How far past the deadline a message is kept before the client removes it.
 *
 * The two clocks need not agree, and the direction of the error is not symmetric. Removing a
 * message *early* hides one the server still serves, so it vanishes and then reappears on the next
 * reload — a visibly broken chat. Removing one *late* matches what the server itself does: its
 * sweep is a once-a-minute `fixedDelay` job, so the row already outlives its deadline by up to a
 * minute server-side. Erring late is therefore both the safer failure and the more truthful one.
 *
 * Five seconds covers ordinary phone-to-server drift. A device whose clock is wrong by more than
 * that is covered by the other half of the fix: the server's broadcast carries no timestamp the
 * client has to trust, so it removes the message regardless of what the device thinks the time is.
 */
internal val EXPIRY_CLOCK_GRACE: Duration = 5.seconds

/** Whether [now] is far enough past this message's deadline for the client to act on it. */
internal fun Message.hasExpiredBy(now: Instant): Boolean {
    val deadline = expiresAt ?: return false
    return now >= deadline + EXPIRY_CLOCK_GRACE
}

/**
 * The list without the messages whose time is up.
 *
 * A message with no `expiresAt` is untouched — that is every message in a chat with no timer set,
 * and also every message read back from the on-device cache, which has no column for the deadline.
 * Those are removed by the server's broadcast or by the next fetch instead.
 */
internal fun List<Message>.dropExpired(now: Instant): List<Message> = filterNot { it.hasExpiredBy(now) }

/**
 * The instant at which this list next changes by itself, or null if it never does.
 *
 * Returns the *earliest* deadline rather than the next one still in the future: a deadline already
 * past means there is work to do right now, and the caller's `delay` of a non-positive duration
 * simply does not wait. Answering "the soonest thing that has to happen" keeps that decision in one
 * place instead of splitting it between this function and the timer that calls it.
 */
internal fun List<Message>.nextExpiryAt(): Instant? =
    mapNotNull { it.expiresAt }.minOrNull()?.plus(EXPIRY_CLOCK_GRACE)
