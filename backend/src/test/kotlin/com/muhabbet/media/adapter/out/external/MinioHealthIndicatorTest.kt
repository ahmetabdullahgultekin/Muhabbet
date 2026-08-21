package com.muhabbet.media.adapter.out.external

import com.muhabbet.shared.config.MediaProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Status
import java.io.IOException

/**
 * #494 — MinIO must be checked at runtime, and checking it must never be able to fail a deploy.
 *
 * The second half is the part with teeth. Both the compose healthcheck and `deploy-hetzner.yml` read
 * only the HTTP status of `/actuator/health`, so any contributor that reports DOWN takes the
 * aggregate to 503 and rolls the release back. Restarting the backend does not fix MinIO and neither
 * does reverting to the previous image, so this indicator must report a status that ranks below UP
 * instead — and must not reach DOWN by the back door of throwing, which the endpoint renders as
 * DOWN.
 */
class MinioHealthIndicatorTest {

    private val properties = MediaProperties(
        minio = MediaProperties.MinioProperties(bucket = "muhabbet-media")
    )

    private fun indicator(probe: MinioHealthIndicator.BucketProbe) =
        MinioHealthIndicator(properties, probe)

    @Test
    fun `should report UP when the media bucket exists`() {
        val health = indicator { true }.health()

        assertEquals(Status.UP, health.status)
        assertEquals("muhabbet-media", health.details["bucket"])
    }

    @Test
    fun `should report the media status when the bucket is missing`() {
        // Reachable, and wrong: the server answered and the bucket is gone. Every upload fails and
        // no restart repairs it, so it must be visible without being fatal.
        val health = indicator { false }.health()

        assertEquals(MinioHealthIndicator.MEDIA_UNAVAILABLE, health.status)
        assertEquals("bucket does not exist", health.details["reason"])
    }

    @Test
    fun `should report the media status when MinIO cannot be reached`() {
        val health = indicator { throw IOException("Connection refused") }.health()

        assertEquals(MinioHealthIndicator.MEDIA_UNAVAILABLE, health.status)
        assertEquals("Connection refused", health.details["reason"])
    }

    @Test
    fun `should never report DOWN when MinIO is unavailable`() {
        // Stated as its own test because DOWN is the outcome that rolls back a release. Both
        // failure shapes are checked, since it is the status that matters, not the cause.
        val statuses = listOf(
            indicator { false }.health().status,
            indicator { throw IllegalStateException("boom") }.health().status
        )

        assertTrue(statuses.none { it == Status.DOWN }, "a media failure must not take the aggregate DOWN")
        assertTrue(statuses.none { it == Status.OUT_OF_SERVICE }, "OUT_OF_SERVICE maps to 503 just as DOWN does")
    }

    @Test
    fun `should not propagate the exception when the probe throws`() {
        // An exception escaping a HealthIndicator is rendered as DOWN by the endpoint, which would
        // defeat everything above.
        indicator { throw IllegalArgumentException("bad endpoint") }.health()
    }
}
