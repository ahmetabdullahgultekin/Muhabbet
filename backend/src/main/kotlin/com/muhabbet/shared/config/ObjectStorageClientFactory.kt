package com.muhabbet.shared.config

import io.minio.MinioClient
import java.net.URI

/**
 * The one place an S3 client for media storage is built, and the one place a presigned URL is
 * moved onto the public host. Three adapters used to build their own client, so a setting such as
 * the signing region had three chances to be forgotten.
 *
 * The client speaks plain S3 only (bucket check, put, get, delete, presign), so the store behind
 * it can be any S3-compatible server; production runs Garage.
 */
object ObjectStorageClientFactory {

    /**
     * Fails fast on a missing endpoint or credential: a backend that starts with blank credentials
     * looks healthy and then fails every upload. Nothing here connects to the store, so an
     * unreachable store still cannot fail startup.
     */
    fun create(storage: MediaProperties.MinioProperties): MinioClient {
        requireSet(storage.endpoint, "MINIO_ENDPOINT")
        requireSet(storage.accessKey, "MINIO_ACCESS_KEY")
        requireSet(storage.secretKey, "MINIO_SECRET_KEY")
        requireSet(storage.bucket, "MINIO_BUCKET")

        val builder = MinioClient.builder()
            .endpoint(storage.endpoint)
            .credentials(storage.accessKey, storage.secretKey)
        // Without a region the SDK asks the store with GetBucketLocation before the first signed
        // request. Garage accepts only its own region ("garage"), so production states it.
        storage.region?.takeIf { it.isNotBlank() }?.let { builder.region(it) }

        return builder.build().apply {
            if (storage.pathStyle) disableVirtualStyleEndpoint() else enableVirtualStyleEndpoint()
        }
    }

    /**
     * Moves a URL presigned against the internal endpoint onto the public host, keeping path and
     * query (and so the signature) byte for byte.
     *
     * SigV4 signs the Host header, and the signed host is the internal one. The proxy behind the
     * public host must therefore send the internal host upstream (Traefik `passHostHeader: false`),
     * or the store rejects the signature.
     *
     * Scheme and authority are taken from the signed URL itself rather than string-matched against
     * the configured endpoint, so a trailing slash or letter case in `MINIO_ENDPOINT` cannot turn
     * the rewrite into a silent no-op that hands clients an internal Docker address.
     */
    fun toPublicUrl(storage: MediaProperties.MinioProperties, presignedUrl: String): String {
        val publicBase = storage.publicEndpoint?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            ?: return presignedUrl
        val signed = URI(presignedUrl)
        val signedOrigin = "${signed.scheme}://${signed.rawAuthority}"
        return publicBase + presignedUrl.substring(signedOrigin.length)
    }

    private fun requireSet(value: String, variable: String) {
        check(value.isNotBlank()) { "Object storage is not configured: $variable is empty" }
    }
}
