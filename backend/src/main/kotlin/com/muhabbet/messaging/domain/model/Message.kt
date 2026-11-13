package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

enum class ContentType {
    TEXT, IMAGE, VOICE, VIDEO, DOCUMENT, LOCATION, CONTACT, POLL, STICKER, GIF
}

enum class DeliveryStatus {
    SENT, DELIVERED, READ
}

data class Message(
    val id: UUID,
    val conversationId: UUID,
    val senderId: UUID,
    val contentType: ContentType = ContentType.TEXT,
    val content: String,
    val replyToId: UUID? = null,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    /**
     * The `media_files` row this message's blob lives in (#541).
     *
     * [mediaUrl] is a rendering convenience — a presigned URL that expires on its own schedule and,
     * for a view-once photo, outlived the burn by seven days. This is the reference that lets the
     * bytes actually be destroyed.
     *
     * **Client-asserted, and worth nothing on its own.** `ViewOnceService` confirms the object was
     * this message's sender's upload immediately before it deletes anything; nowhere else acts on
     * it. Any future reader owes the same check. Null for text, and for every message sent before
     * V24.
     */
    val mediaId: UUID? = null,
    val serverTimestamp: Instant = Instant.now(),
    val clientTimestamp: Instant,
    val isDeleted: Boolean = false,
    val deletedAt: Instant? = null,
    val editedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val forwardedFrom: UUID? = null,
    // View-Once Media
    val viewOnce: Boolean = false,
    val viewedAt: Instant? = null,
    val viewedBy: UUID? = null,
    // Message Scheduling
    val scheduledAt: Instant? = null,
    val isScheduled: Boolean = false
) {
    /** Where this message is in its life, read from the stored flags (#872). */
    val lifecycle: MessageLifecycle
        get() = when {
            isDeleted -> MessageLifecycle.Deleted
            isScheduled -> MessageLifecycle.Scheduled(scheduledAt ?: serverTimestamp)
            else -> MessageLifecycle.Live
        }

    /**
     * Scheduled -> Live, if due at [now]. A delivered scheduled message is a message *sent at
     * delivery time*: it is re-stamped with [now] — so it sorts where it lands and background sync
     * (`since = last sync`) returns it — and its disappearing deadline, if the chat has one, is
     * counted from [now] too. Both used to keep their scheduling-time values, so a message
     * scheduled for next week in a 24-hour chat was swept before it was ever delivered.
     *
     * Pure: the caller must persist the result with a write conditional on the row still being
     * scheduled, which is what makes delivery exactly-once.
     */
    fun deliverDue(now: Instant, disappearAfterSeconds: Int?): ScheduledDelivery = when (val state = lifecycle) {
        MessageLifecycle.Live, MessageLifecycle.Deleted -> ScheduledDelivery.NotScheduled
        is MessageLifecycle.Scheduled ->
            if (state.at.isAfter(now)) {
                ScheduledDelivery.NotDue
            } else {
                ScheduledDelivery.Due(
                    copy(
                        isScheduled = false,
                        serverTimestamp = now,
                        expiresAt = disappearAfterSeconds?.let { now.plusSeconds(it.toLong()) }
                    )
                )
            }
    }
}

/** A message's lifecycle: scheduled for later, live in the conversation, or deleted. */
sealed interface MessageLifecycle {
    data class Scheduled(val at: Instant) : MessageLifecycle
    data object Live : MessageLifecycle
    data object Deleted : MessageLifecycle
}

/** The outcome of [Message.deliverDue]. */
sealed interface ScheduledDelivery {
    /** The message as it must be stored and fanned out on delivery. */
    data class Due(val message: Message) : ScheduledDelivery
    data object NotDue : ScheduledDelivery
    data object NotScheduled : ScheduledDelivery
}

data class MessageDeliveryStatus(
    val messageId: UUID,
    val userId: UUID,
    val status: DeliveryStatus = DeliveryStatus.SENT,
    val updatedAt: Instant = Instant.now()
)
