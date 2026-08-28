package com.muhabbet.media.domain.port.out

import java.io.InputStream

interface MediaStoragePort {
    fun putObject(key: String, inputStream: InputStream, contentType: String, sizeBytes: Long)
    fun getPresignedUrl(key: String, expirySeconds: Int = 604800): String
    fun deleteObject(key: String)

    /**
     * The object's bytes, read through the server rather than handed out as a URL, or null if it is
     * no longer there.
     *
     * The whole point of the view-once path (#541): a presigned URL is a credential with a lifetime,
     * and anything with a lifetime outlives the burn. Reading here means the recipient can be shown
     * the photo in the same response that destroys it, so there is no URL left to keep.
     *
     * Not for ordinary media — those are rendered straight from MinIO and must stay that way, or
     * every image in every chat becomes a byte stream through this process.
     */
    fun getObject(key: String): ByteArray?
}
