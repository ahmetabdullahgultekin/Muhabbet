package com.muhabbet.shared.protocol

import com.muhabbet.shared.model.CallEndReason
import com.muhabbet.shared.model.CallType
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.model.PresenceStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * All WebSocket messages exchanged between client and server.
 * This sealed hierarchy is the SINGLE SOURCE OF TRUTH for the WS protocol.
 * Both backend and mobile use these exact types.
 */
@Serializable
sealed class WsMessage {

    // ─── Client → Server ─────────────────────────────────

    /** Client sends a new message */
    @Serializable
    @SerialName("message.send")
    data class SendMessage(
        val requestId: String,              // client-generated, for ACK correlation
        val messageId: String,              // UUIDv7, idempotency key
        val conversationId: String,
        val content: String,
        val contentType: ContentType = ContentType.TEXT,
        val replyToId: String? = null,
        val mediaUrl: String? = null,
        val thumbnailUrl: String? = null,
        val forwardedFrom: String? = null,  // original messageId if forwarded
        val viewOnce: Boolean = false,
        val scheduledAt: Long? = null        // epoch millis, null = send immediately
    ) : WsMessage()

    /** Client acknowledges received message (delivered/read) */
    @Serializable
    @SerialName("message.ack")
    data class AckMessage(
        val messageId: String,
        val conversationId: String,
        val status: MessageStatus           // DELIVERED or READ
    ) : WsMessage()

    /** Client typing indicator */
    @Serializable
    @SerialName("presence.typing")
    data class TypingIndicator(
        val conversationId: String,
        val isTyping: Boolean
    ) : WsMessage()

    /** Client requests to go online (sent on connect) */
    @Serializable
    @SerialName("presence.online")
    data object GoOnline : WsMessage()

    /**
     * Client reports which conversation, if any, is on screen and foregrounded right now.
     *
     * The signal a socket alone cannot give: [WebSocketSessionManager.isOnline] only ever meant "a
     * socket is open," which is true for a phone with the screen off or reading a different chat,
     * not just for one looking at this exact conversation. #618 shipped a double tick with no
     * notification precisely because push suppression had nothing finer than that to consult.
     *
     * [conversationId] is `null` when the app is backgrounded or no chat screen is open — clearing
     * it explicitly rather than relying on disconnect, because the socket usually stays up while the
     * app is merely backgrounded (see the reap threshold in `WebSocketSessionManager`).
     */
    @Serializable
    @SerialName("presence.conversation_focus")
    data class ConversationFocus(
        val conversationId: String? = null
    ) : WsMessage()

    /** Client heartbeat / ping */
    @Serializable
    @SerialName("ping")
    data object Ping : WsMessage()

    // ─── Call Signaling (Bidirectional) ─────────────────────

    /** Client initiates a call to another user */
    @Serializable
    @SerialName("call.initiate")
    data class CallInitiate(
        val callId: String,
        val targetUserId: String,
        val callType: CallType,
        val sdpOffer: String? = null
    ) : WsMessage()

    /** Client answers (accepts/declines) an incoming call */
    @Serializable
    @SerialName("call.answer")
    data class CallAnswer(
        val callId: String,
        val accepted: Boolean,
        val sdpAnswer: String? = null
    ) : WsMessage()

    /** Client sends an ICE candidate for WebRTC negotiation */
    @Serializable
    @SerialName("call.ice")
    data class CallIceCandidate(
        val callId: String,
        val candidate: String,
        val sdpMid: String? = null,
        val sdpMLineIndex: Int? = null
    ) : WsMessage()

    /** Client or server ends a call */
    @Serializable
    @SerialName("call.end")
    data class CallEnd(
        val callId: String,
        val reason: CallEndReason = CallEndReason.ENDED
    ) : WsMessage()

    /** Server notifies callee about an incoming call */
    @Serializable
    @SerialName("call.incoming")
    data class CallIncoming(
        val callId: String,
        val callerId: String,
        val callerName: String?,
        val callType: CallType
    ) : WsMessage()

    /** Server sends LiveKit room credentials to both call participants */
    @Serializable
    @SerialName("call.room")
    data class CallRoomInfo(
        val callId: String,
        val serverUrl: String,
        val token: String,
        val roomName: String
    ) : WsMessage()

    // ─── Server → Client ─────────────────────────────────

    /**
     * Server delivers a new message to recipient.
     *
     * [viewOnce] exists because without it the live path could not seal anything: the recipient
     * builds their bubble from this frame, and a frame that does not mention view-once produces an
     * ordinary photo bubble. The flag reached the database and stopped there (#515).
     *
     * For a view-once message [mediaUrl] and [thumbnailUrl] are **null on purpose**. The blob URL is
     * released once, by `POST /api/v1/messages/{id}/view-once`, to the member who opens it. Shipping
     * it here would mean the seal only ever hid a URL the recipient already held.
     */
    @Serializable
    @SerialName("message.new")
    data class NewMessage(
        val messageId: String,
        val conversationId: String,
        val senderId: String,
        val senderName: String?,
        val content: String,
        val contentType: ContentType,
        val replyToId: String? = null,
        val mediaUrl: String? = null,
        val thumbnailUrl: String? = null,
        val serverTimestamp: Long,           // epoch millis
        val forwardedFrom: String? = null,
        val viewOnce: Boolean = false,
        /**
         * Epoch millis at which a disappearing message is due to vanish, null if it never is.
         *
         * The recipient builds their bubble from this frame, so without it a message that arrives
         * while the chat is open can never be removed on time — which was half of #513. The other
         * half is [MessageExpired], for the messages whose deadline passes while nothing is
         * listening.
         */
        val expiresAt: Long? = null
    ) : WsMessage()

    /**
     * Server tells every member that a disappearing message's time is up.
     *
     * Deliberately **not** [MessageDeleted]. A deletion is an act by a person, which is why that
     * frame carries `deletedBy` and why clients render its result as a "this message was deleted"
     * tombstone. An expiry is nobody's act and leaves no tombstone: the server drops the row from
     * every read path, so a tombstone would sit there until the next reload and then silently
     * disappear — the same "it only updates when you look away" complaint one level down.
     *
     * It exists alongside the client's own timer rather than instead of it, and each covers the
     * other's blind spot. The timer is exact while the chat is open and needs no round trip; this
     * frame is what removes a message whose deadline passed while the app was asleep or whose clock
     * disagreed with the server's.
     *
     * A client too old to know this frame drops it in `WsClient`'s per-frame `catch` and carries on
     * — it simply keeps the behaviour it has today.
     */
    @Serializable
    @SerialName("message.expired")
    data class MessageExpired(
        val messageId: String,
        val conversationId: String,
        val expiredAt: Long
    ) : WsMessage()

    /** Server notifies sender about delivery status change */
    @Serializable
    @SerialName("message.status")
    data class StatusUpdate(
        val messageId: String,
        val conversationId: String,
        val userId: String,                 // who triggered the status change
        val status: MessageStatus,
        val timestamp: Long
    ) : WsMessage()

    /** Server ACK for client's SendMessage */
    @Serializable
    @SerialName("ack")
    data class ServerAck(
        val requestId: String,              // correlates to SendMessage.requestId
        val messageId: String,
        val status: AckStatus,
        val serverTimestamp: Long? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null
    ) : WsMessage()

    /** Server notifies about presence changes */
    @Serializable
    @SerialName("presence.update")
    data class PresenceUpdate(
        val userId: String,
        val conversationId: String? = null, // null = global, set = typing in specific convo
        val status: PresenceStatus,
        val lastSeenAt: Long? = null
    ) : WsMessage()

    // ─── Group Events (Server → Client) ────────────────────

    /** Server notifies that members were added to a group */
    @Serializable
    @SerialName("group.member_added")
    data class GroupMemberAdded(
        val conversationId: String,
        val addedBy: String,
        val members: List<GroupMemberInfo>
    ) : WsMessage()

    /** Server notifies that a member was removed from a group */
    @Serializable
    @SerialName("group.member_removed")
    data class GroupMemberRemoved(
        val conversationId: String,
        val removedBy: String,
        val userId: String
    ) : WsMessage()

    /** Server notifies that group info was updated */
    @Serializable
    @SerialName("group.info_updated")
    data class GroupInfoUpdated(
        val conversationId: String,
        val updatedBy: String,
        val name: String? = null,
        val avatarUrl: String? = null,
        val description: String? = null
    ) : WsMessage()

    /** Server notifies that a member's role changed */
    @Serializable
    @SerialName("group.role_updated")
    data class GroupRoleUpdated(
        val conversationId: String,
        val updatedBy: String,
        val userId: String,
        val newRole: String
    ) : WsMessage()

    /** Server notifies that a member left the group */
    @Serializable
    @SerialName("group.member_left")
    data class GroupMemberLeft(
        val conversationId: String,
        val userId: String
    ) : WsMessage()

    // ─── Message Management (Server → Client) ──────────────

    /** Server notifies that a message was deleted */
    @Serializable
    @SerialName("message.deleted")
    data class MessageDeleted(
        val messageId: String,
        val conversationId: String,
        val deletedBy: String,
        val timestamp: Long
    ) : WsMessage()

    /** Server notifies that a message was edited */
    @Serializable
    @SerialName("message.edited")
    data class MessageEdited(
        val messageId: String,
        val conversationId: String,
        val editedBy: String,
        val newContent: String,
        val editedAt: Long
    ) : WsMessage()

    /** Server notifies about a reaction on a message */
    @Serializable
    @SerialName("message.reaction")
    data class MessageReaction(
        val messageId: String,
        val conversationId: String,
        val userId: String,
        val emoji: String,
        val action: String  // "add" or "remove"
    ) : WsMessage()

    /** Server heartbeat response */
    @Serializable
    @SerialName("pong")
    data object Pong : WsMessage()

    /** Server error (non-ACK errors like auth failure) */
    @Serializable
    @SerialName("error")
    data class Error(
        val code: String,
        val message: String
    ) : WsMessage()

    // ─── Security & Auth (Server → Client) ──────────────────

    /** Server notifies about a security key change */
    @Serializable
    @SerialName("security.key_changed")
    data class SecurityKeyChanged(
        val userId: String,
        val conversationId: String?,
        val timestamp: Long
    ) : WsMessage()

    /** Server sends login approval request to existing device */
    @Serializable
    @SerialName("auth.login_approval")
    data class LoginApprovalRequest(
        val approvalId: String,
        val deviceName: String?,
        val platform: String?,
        val timestamp: Long
    ) : WsMessage()

    /** Client responds to login approval */
    @Serializable
    @SerialName("auth.login_response")
    data class LoginApprovalResponse(
        val approvalId: String,
        val approved: Boolean
    ) : WsMessage()

    /** Server notifies about a group call starting */
    @Serializable
    @SerialName("call.group_start")
    data class GroupCallStarted(
        val callId: String,
        val conversationId: String,
        val callerId: String,
        val callerName: String?,
        val callType: CallType,
        val participantCount: Int
    ) : WsMessage()
}

@Serializable
data class GroupMemberInfo(
    val userId: String,
    val displayName: String?,
    val role: String
)

@Serializable
enum class AckStatus {
    OK,
    ERROR
}

/**
 * JSON serializer configured for the WS protocol.
 * Use this SAME instance on both backend and mobile.
 */
val wsJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = false
}
