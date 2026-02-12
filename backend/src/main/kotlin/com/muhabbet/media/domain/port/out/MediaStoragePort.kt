package com.muhabbet.media.domain.port.out

import java.io.InputStream

interface MediaStoragePort {
    fun putObject(key: String, inputStream: InputStream, contentType: String, sizeBytes: Long)
    fun getPresignedUrl(key: String, expirySeconds: Int = 3600): String
    fun deleteObject(key: String)
}
