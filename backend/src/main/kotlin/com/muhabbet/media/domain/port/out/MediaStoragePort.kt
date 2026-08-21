package com.muhabbet.media.domain.port.out

import java.io.InputStream

interface MediaStoragePort {
    fun putObject(key: String, inputStream: InputStream, contentType: String, sizeBytes: Long)
    fun getPresignedUrl(key: String, expirySeconds: Int = 604800): String
    fun deleteObject(key: String)

    /**
     * The object key [url] addresses, or null when [url] does not address this store — a different
     * origin, a path outside the bucket, or something that is not a URL at all.
     *
     * The inverse of [getPresignedUrl], and deliberately implemented beside it: the adapter that
     * decides how a stored key becomes a public address is the only place that can reverse the
     * decision without guessing. Callers use it to find out whether a URL a client handed them
     * names one of our own blobs before they trust it.
     */
    fun resolveObjectKey(url: String): String?
}
