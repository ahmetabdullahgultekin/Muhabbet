package com.muhabbet.app.platform

expect fun compressImage(bytes: ByteArray, maxDimension: Int = 1280, quality: Int = 80): ByteArray
