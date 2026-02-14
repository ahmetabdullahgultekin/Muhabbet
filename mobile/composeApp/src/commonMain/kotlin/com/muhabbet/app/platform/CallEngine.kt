package com.muhabbet.app.platform

/**
 * Platform-abstraction for voice/video call media management.
 *
 * Android: LiveKit SDK handles WebRTC, ICE, SRTP, etc.
 * iOS: Stub until LiveKit Swift SDK is bridged.
 *
 * Lifecycle:
 *   connect(serverUrl, token) → setMuted/setSpeaker → disconnect()
 */
expect class CallEngine() {

    /** Connect to a LiveKit room using the server-issued token. */
    suspend fun connect(serverUrl: String, token: String)

    /** Disconnect from the call room and release media resources. */
    fun disconnect()

    /** True if currently connected to a room. */
    fun isConnected(): Boolean

    /** Mute/unmute local microphone. */
    fun setMuted(muted: Boolean)

    /** Enable/disable speaker output. */
    fun setSpeaker(enabled: Boolean)
}
