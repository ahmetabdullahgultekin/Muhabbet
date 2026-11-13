package com.muhabbet.messaging.domain.model

import com.muhabbet.shared.model.ContentType as SharedContentType
import com.muhabbet.shared.model.ConversationType as SharedConversationType
import com.muhabbet.shared.model.MemberRole as SharedMemberRole
import com.muhabbet.shared.model.MessageStatus

/**
 * The one place the messaging domain's enums meet the wire's (CLAUDE.md: mapper extensions, not
 * `valueOf` on names).
 *
 * Exhaustive `when` in both directions, deliberately. `SharedContentType.valueOf(contentType.name)`
 * — what this replaced — compiles whether or not the two enums agree and fails at runtime, once, in
 * production; a `when` fails the build the day a constant is added to either side.
 *
 * Inbound mappings return `null` for the wire's `UNKNOWN` (#875): a value a newer client sent that
 * this server cannot act on. Callers refuse it with `VALIDATION_ERROR` rather than guessing — the
 * send path used to fall back to TEXT, which stored a message of a type the sender never chose.
 */
fun ContentType.toShared(): SharedContentType = when (this) {
    ContentType.TEXT -> SharedContentType.TEXT
    ContentType.IMAGE -> SharedContentType.IMAGE
    ContentType.VOICE -> SharedContentType.VOICE
    ContentType.VIDEO -> SharedContentType.VIDEO
    ContentType.DOCUMENT -> SharedContentType.DOCUMENT
    ContentType.LOCATION -> SharedContentType.LOCATION
    ContentType.CONTACT -> SharedContentType.CONTACT
    ContentType.POLL -> SharedContentType.POLL
    ContentType.STICKER -> SharedContentType.STICKER
    ContentType.GIF -> SharedContentType.GIF
}

fun SharedContentType.toDomainOrNull(): ContentType? = when (this) {
    SharedContentType.TEXT -> ContentType.TEXT
    SharedContentType.IMAGE -> ContentType.IMAGE
    SharedContentType.VOICE -> ContentType.VOICE
    SharedContentType.VIDEO -> ContentType.VIDEO
    SharedContentType.DOCUMENT -> ContentType.DOCUMENT
    SharedContentType.LOCATION -> ContentType.LOCATION
    SharedContentType.CONTACT -> ContentType.CONTACT
    SharedContentType.POLL -> ContentType.POLL
    SharedContentType.STICKER -> ContentType.STICKER
    SharedContentType.GIF -> ContentType.GIF
    SharedContentType.UNKNOWN -> null
}

fun DeliveryStatus.toShared(): MessageStatus = when (this) {
    DeliveryStatus.SENT -> MessageStatus.SENT
    DeliveryStatus.DELIVERED -> MessageStatus.DELIVERED
    DeliveryStatus.READ -> MessageStatus.READ
}

fun MemberRole.toShared(): SharedMemberRole = when (this) {
    MemberRole.OWNER -> SharedMemberRole.OWNER
    MemberRole.ADMIN -> SharedMemberRole.ADMIN
    MemberRole.MEMBER -> SharedMemberRole.MEMBER
}

fun SharedMemberRole.toDomainOrNull(): MemberRole? = when (this) {
    SharedMemberRole.OWNER -> MemberRole.OWNER
    SharedMemberRole.ADMIN -> MemberRole.ADMIN
    SharedMemberRole.MEMBER -> MemberRole.MEMBER
    SharedMemberRole.UNKNOWN -> null
}

fun SharedConversationType.toDomainOrNull(): ConversationType? = when (this) {
    SharedConversationType.DIRECT -> ConversationType.DIRECT
    SharedConversationType.GROUP -> ConversationType.GROUP
    SharedConversationType.CHANNEL -> ConversationType.CHANNEL
    SharedConversationType.UNKNOWN -> null
}
