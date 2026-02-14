package com.muhabbet.app.platform

import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android CallEngine powered by LiveKit Android SDK.
 *
 * Handles room connection, microphone management and speaker routing.
 * Video support can be added later by publishing a camera track.
 */
actual class CallEngine actual constructor() {

    private var room: Room? = null

    actual suspend fun connect(serverUrl: String, token: String) {
        withContext(Dispatchers.Main) {
            val lkRoom = LiveKit.create(
                appContext = LiveKit.appContext,
            )
            lkRoom.connect(serverUrl, token)
            room = lkRoom
        }
    }

    actual fun disconnect() {
        room?.disconnect()
        room = null
    }

    actual fun isConnected(): Boolean = room?.state == Room.State.CONNECTED

    actual fun setMuted(muted: Boolean) {
        val localParticipant = room?.localParticipant ?: return
        val audioTrack = localParticipant.trackPublications.values
            .mapNotNull { it.track as? LocalAudioTrack }
            .firstOrNull()
        audioTrack?.enabled = !muted
    }

    actual fun setSpeaker(enabled: Boolean) {
        // LiveKit Android SDK uses the system audio routing.
        // Speaker mode is handled at the AudioManager level by the SDK
        // when earpieceModeToggle is configured. For now, delegate to default behavior.
        room?.let {
            it.audioHandler?.let { handler ->
                // The SDK's AudioSwitchHandler manages routing automatically
            }
        }
    }
}
