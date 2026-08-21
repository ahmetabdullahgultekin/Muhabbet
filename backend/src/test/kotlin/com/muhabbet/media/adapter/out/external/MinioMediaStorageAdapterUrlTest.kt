package com.muhabbet.media.adapter.out.external

import com.muhabbet.shared.config.MediaProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * `resolveObjectKey` decides whether a URL a client sent us names one of our own blobs, and every
 * media message in the app depends on it answering yes. A rule that is too strict does not fail
 * loudly — it silently refuses photos (#679's likeliest way of going wrong), so the shapes MinIO
 * actually produces are pinned here.
 *
 * `init()` is never called: this method touches only the configured names, not the MinIO client.
 */
class MinioMediaStorageAdapterUrlTest {

    private val properties = MediaProperties(
        minio = MediaProperties.MinioProperties(
            endpoint = "http://minio:9000",
            publicEndpoint = "https://cdn-muhabbet.example.test",
            bucket = "muhabbet-media"
        )
    )

    private val adapter = MinioMediaStorageAdapter(properties)

    @Test
    fun `should resolve the key from a presigned URL on the public endpoint`() {
        val url = "https://cdn-muhabbet.example.test/muhabbet-media/images/u1/photo.jpg" +
            "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=deadbeef"

        assertEquals("images/u1/photo.jpg", adapter.resolveObjectKey(url))
    }

    /**
     * `publicEndpoint` is optional, and where it is unset `getPresignedUrl` hands back the internal
     * address unrewritten. Refusing that would make every local and test-profile media send fail.
     */
    @Test
    fun `should resolve the key from a URL on the internal endpoint`() {
        assertEquals(
            "thumbnails/u1/photo.jpg",
            adapter.resolveObjectKey("http://minio:9000/muhabbet-media/thumbnails/u1/photo.jpg")
        )
    }

    @Test
    fun `should not resolve a URL on a host that merely starts with ours`() {
        assertNull(adapter.resolveObjectKey("https://cdn-muhabbet.example.test.attacker.test/muhabbet-media/images/u1/x.jpg"))
    }

    @Test
    fun `should not resolve a URL on an unrelated host`() {
        assertNull(adapter.resolveObjectKey("https://tracker.attacker.example/muhabbet-media/images/u1/x.jpg"))
    }

    /** Our host, but outside the bucket — nothing there is a media object. */
    @Test
    fun `should not resolve a path outside the bucket`() {
        assertNull(adapter.resolveObjectKey("https://cdn-muhabbet.example.test/other-bucket/images/u1/x.jpg"))
    }

    @Test
    fun `should not resolve the bucket root with no object`() {
        assertNull(adapter.resolveObjectKey("https://cdn-muhabbet.example.test/muhabbet-media/"))
    }

    @Test
    fun `should not resolve a string that is not a URL`() {
        assertNull(adapter.resolveObjectKey("not a url at all"))
    }

    /** A scheme swap is a different origin, not a detail — plain HTTP to our CDN is not our CDN. */
    @Test
    fun `should not resolve a URL whose scheme differs from the endpoint`() {
        assertNull(adapter.resolveObjectKey("http://cdn-muhabbet.example.test/muhabbet-media/images/u1/x.jpg"))
    }
}
