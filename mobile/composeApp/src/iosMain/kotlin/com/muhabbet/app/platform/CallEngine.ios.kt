package com.muhabbet.app.platform

/**
 * iOS CallEngine stub.
 *
 * LiveKit Swift SDK integration requires CocoaPods/SPM bridge.
 * This stub allows the app to compile for iOS while calls
 * are only functional on Android.
 */
actual class CallEngine actual constructor() {

    private var connected = false

    actual suspend fun connect(serverUrl: String, token: String) {
        // Stub: LiveKit Swift SDK not yet bridged
        connected = true
    }

    actual fun disconnect() {
        connected = false
    }

    actual fun isConnected(): Boolean = connected

    actual fun setMuted(muted: Boolean) {
        // Stub
    }

    actual fun setSpeaker(enabled: Boolean) {
        // Stub
    }
}
