package com.muhabbet.app.ui.chat

import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.datetime.Instant

/**
 * A photo about to be sent: the bubble drawn immediately, and the frame put on the wire.
 *
 * The pair is the point. They are two descriptions of the same act, and for view-once they had
 * drifted apart in the worst possible direction — the bubble said "sealed" and the frame said
 * nothing (#515), so the sender was shown a guarantee that was never requested of the server.
 */
internal data class OutgoingPhoto(
    val optimistic: Message,
    val frame: WsMessage.SendMessage
)

/**
 * Builds both halves of a photo send from one set of facts.
 *
 * Extracted from the gallery and camera handlers in `ChatScreen`, which were byte-for-byte the same
 * eleven lines apart from which picker produced the bytes. Two copies is exactly how the original
 * defect could be half-fixed: a flag added to one path and forgotten on the other looks identical in
 * review and behaves differently on a phone.
 *
 * It takes no view of *whether* the photo should be view-once — that is the composer's state. It
 * guarantees only that whatever is decided reaches both the screen and the socket, which is the part
 * that was not true. `PhotoSendTest` asserts it.
 */
/**
 * @param caption what the sender actually typed alongside the picture, or empty when they typed
 *   nothing — which is every photo this version can send, since there is no caption field.
 *
 *   It used to be the *word* "Photo", resolved from `stringResource` on the sending device and put
 *   on the wire as the message body (#534). That is not a caption; it is a label, and a label
 *   belongs to whoever is reading. Stored as the body it was frozen in the sender's language
 *   forever: wrong for a recipient in the other language from the moment it was sent, and immune to
 *   the sender switching their own. The label is now drawn from `contentType` at render time, on
 *   the device doing the rendering, which leaves this parameter free to mean what its name says.
 */
internal fun outgoingPhoto(
    messageId: String,
    requestId: String,
    conversationId: String,
    senderId: String,
    caption: String,
    mediaUrl: String,
    thumbnailUrl: String?,
    viewOnce: Boolean,
    sentAt: Instant
): OutgoingPhoto = OutgoingPhoto(
    optimistic = Message(
        id = messageId,
        conversationId = conversationId,
        senderId = senderId,
        contentType = ContentType.IMAGE,
        content = caption,
        mediaUrl = mediaUrl,
        thumbnailUrl = thumbnailUrl,
        status = MessageStatus.SENDING,
        clientTimestamp = sentAt,
        viewOnce = viewOnce
    ),
    frame = WsMessage.SendMessage(
        requestId = requestId,
        messageId = messageId,
        conversationId = conversationId,
        content = caption,
        contentType = ContentType.IMAGE,
        mediaUrl = mediaUrl,
        thumbnailUrl = thumbnailUrl,
        viewOnce = viewOnce
    )
)
