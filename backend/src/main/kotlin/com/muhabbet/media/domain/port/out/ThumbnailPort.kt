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

/**
 * The image declares more pixels than the server will decode. Raised from the header, before any
 * pixel data is read, because decoding allocates by pixel count and a tiny file can declare an
 * enormous canvas.
 */
class ImageTooLargeException : RuntimeException("Image dimensions exceed the configured pixel limit")
