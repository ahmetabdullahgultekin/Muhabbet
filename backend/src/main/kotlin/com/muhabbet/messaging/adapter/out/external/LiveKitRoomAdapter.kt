package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.port.out.CallRoom
import com.muhabbet.messaging.domain.port.out.CallRoomProvider
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID

/**
 * LiveKit Cloud integration for voice/video call rooms.
 * Uses LiveKit Server API for room management and JWT for participant tokens.
 *
 * Required config:
 *   muhabbet.livekit.api-key: LiveKit API Key
 *   muhabbet.livekit.api-secret: LiveKit API Secret
 *   muhabbet.livekit.server-url: wss://your-project.livekit.cloud
 */
@Component
@ConditionalOnProperty(name = ["muhabbet.livekit.enabled"], havingValue = "true", matchIfMissing = false)
class LiveKitRoomAdapter(
    @Value("\${muhabbet.livekit.api-key:}") private val apiKey: String,
    @Value("\${muhabbet.livekit.api-secret:}") private val apiSecret: String,
    @Value("\${muhabbet.livekit.server-url:}") private val serverUrl: String
) : CallRoomProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun createRoom(callId: String, callerId: UUID, calleeId: UUID): CallRoom {
        val roomName = "call-$callId"
        // LiveKit auto-creates rooms when the first participant joins with a valid token.
        // No explicit room creation API call needed for basic 1:1 calls.
        log.info("LiveKit room prepared: roomName={}, caller={}, callee={}", roomName, callerId, calleeId)
        return CallRoom(roomName = roomName, serverUrl = serverUrl)
    }

    override fun generateParticipantToken(roomName: String, userId: UUID, displayName: String?): String {
        val now = Date()
        val expiry = Date(now.time + 3600_000) // 1 hour

        // LiveKit access token is a JWT signed with the API secret
        val key = Keys.hmacShaKeyFor(apiSecret.toByteArray())
        val grants = mapOf(
            "roomJoin" to true,
            "room" to roomName,
            "canPublish" to true,
            "canSubscribe" to true
        )

        val token = Jwts.builder()
            .issuer(apiKey)
            .subject(userId.toString())
            .issuedAt(now)
            .expiration(expiry)
            .claim("name", displayName ?: userId.toString())
            .claim("video", grants)
            .signWith(key)
            .compact()

        log.debug("Generated LiveKit token for userId={} in room={}", userId, roomName)
        return token
    }

    override fun closeRoom(roomName: String) {
        // LiveKit auto-closes rooms when all participants leave.
        // For explicit cleanup, we'd use the LiveKit Server SDK's deleteRoom() API.
        log.info("LiveKit room close requested: roomName={}", roomName)
    }

    override fun isRoomActive(roomName: String): Boolean {
        // Would query LiveKit Server API: GET /twirp/livekit.RoomService/ListRooms
        // For MVP, assume room is active if call session exists in our DB
        return true
    }
}

/**
 * NoOp implementation when LiveKit is not configured.
 * Call signaling still works via WebSocket (SDP/ICE relay).
 */
@Component
@ConditionalOnProperty(name = ["muhabbet.livekit.enabled"], havingValue = "false", matchIfMissing = true)
class NoOpCallRoomProvider : CallRoomProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun createRoom(callId: String, callerId: UUID, calleeId: UUID): CallRoom {
        log.debug("NoOp call room: callId={} (peer-to-peer mode)", callId)
        return CallRoom(roomName = "p2p-$callId", serverUrl = "")
    }

    override fun generateParticipantToken(roomName: String, userId: UUID, displayName: String?): String = ""

    override fun closeRoom(roomName: String) {}

    override fun isRoomActive(roomName: String): Boolean = false
}
