package com.muhabbet.messaging.domain.port.out

import java.util.UUID

/**
 * Port for managing voice/video call rooms.
 * MVP: LiveKit Cloud implementation.
 * Can be swapped to self-hosted LiveKit or raw WebRTC later.
 */
interface CallRoomProvider {

    /**
     * Create a call room and return the room name/ID.
     */
    fun createRoom(callId: String, callerId: UUID, calleeId: UUID): CallRoom

    /**
     * Generate a participant token for joining a room.
     */
    fun generateParticipantToken(roomName: String, userId: UUID, displayName: String?): String

    /**
     * End/close a room.
     */
    fun closeRoom(roomName: String)

    /**
     * Check if a room exists and is active.
     */
    fun isRoomActive(roomName: String): Boolean
}

data class CallRoom(
    val roomName: String,
    val serverUrl: String
)
