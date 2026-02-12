package com.muhabbet.media.domain.port.out

import java.io.InputStream

interface ThumbnailPort {
    fun generateThumbnail(inputStream: InputStream, contentType: String, maxWidth: Int, maxHeight: Int): ThumbnailResult
}

data class ThumbnailResult(
    val data: ByteArray,
    val contentType: String = "image/jpeg",
    val width: Int,
    val height: Int
)
