package com.muhabbet.app.platform

actual fun compressImage(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray {
    // iOS stub — return input unchanged
    return bytes
}
