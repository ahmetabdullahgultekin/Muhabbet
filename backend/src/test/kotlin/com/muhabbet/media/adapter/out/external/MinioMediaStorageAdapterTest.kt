package com.muhabbet.media.adapter.out.external

import com.muhabbet.shared.config.MediaProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the Finding-B mitigation (V&V 2026-06-08): presigned GET URLs carry a signed
 * `response-content-disposition=attachment` override so a document uploaded with an inline-rendering
 * content type (`text/html` / `image/svg+xml`) downloads instead of rendering from the media origin.
 *
 * MinIO's `getPresignedObjectUrl` does a network round-trip in the SDK, so these tests exercise the
 * two extracted seams (args building + public-endpoint rewrite) directly — no live MinIO needed.
 */
class MinioMediaStorageAdapterTest {

    private fun adapter(publicEndpoint: String? = null): MinioMediaStorageAdapter {
        val props = MediaProperties(
            minio = MediaProperties.MinioProperties(
                endpoint = "http://minio:9000",
                publicEndpoint = publicEndpoint,
                accessKey = "minioadmin",
                secretKey = "minioadmin",
                bucket = "muhabbet-media"
            )
        )
        // init() is not called: it connects to MinIO. The presign-args / rewrite seams don't need the
        // client, and init() fails soft (logs a warning) anyway when MinIO is unreachable.
        return MinioMediaStorageAdapter(props)
    }

    @Test
    fun `should attach attachment disposition override to presigned get args`() {
        val args = adapter().buildPresignedGetArgs("documents/u/abc.html", 3600)

        val params = args.extraQueryParams()
        assertTrue(params.containsKey("response-content-disposition")) {
            "presigned args missing response-content-disposition override"
        }
        assertEquals("attachment", params.getFirst("response-content-disposition"))
    }

    @Test
    fun `should request attachment uniformly regardless of object key type`() {
        // Images/audio get the same disposition — the in-app loader uses the bytes, not the header.
        val imageArgs = adapter().buildPresignedGetArgs("images/u/photo.jpg", 60)
        assertEquals("attachment", imageArgs.extraQueryParams().getFirst("response-content-disposition"))
    }

    @Test
    fun `should preserve attachment disposition query param through public endpoint rewrite`() {
        // Simulate a presigned URL on the internal endpoint with the signed disposition query param.
        val internalUrl = "http://minio:9000/muhabbet-media/documents/u/abc.html" +
            "?X-Amz-Signature=deadbeef&response-content-disposition=attachment"

        val rewritten = adapter(publicEndpoint = "https://media.muhabbet.app").rewriteToPublicEndpoint(internalUrl)

        assertTrue(rewritten.startsWith("https://media.muhabbet.app")) {
            "public endpoint rewrite did not apply: $rewritten"
        }
        assertTrue(rewritten.contains("response-content-disposition=attachment")) {
            "attachment disposition was stripped by the endpoint rewrite: $rewritten"
        }
    }
}
