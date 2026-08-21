package com.muhabbet.media.adapter.out.external

import com.muhabbet.shared.config.MediaProperties
import io.minio.BucketExistsArgs
import io.minio.MinioClient
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.health.contributor.Status
import org.springframework.stereotype.Component

/**
 * Reports whether the media bucket is actually reachable.
 *
 * Every photo, voice note, document and profile picture in this product lives in MinIO, and until
 * this existed nothing checked it after boot. `MinioMediaStorageAdapter` touches it once in
 * `@PostConstruct` and only logs a warning if that fails, so MinIO could be down, out of disk, or
 * have lost the bucket entirely while `/actuator/health` kept answering UP — the container was
 * reported healthy and a deploy of a build with completely broken media sailed through its gate
 * (#494). The one component whose failure is most likely to be silent was the one component nothing
 * watched.
 *
 * ## Why this reports [MEDIA_UNAVAILABLE] and not DOWN
 *
 * Both gates that consume health read **only the HTTP status** — the deploy workflow polls with
 * `curl -sf` and the compose healthcheck with `curl -f`, neither looks at the body. A DOWN
 * contributor takes the aggregate DOWN, which is 503, which fails the container healthcheck and
 * makes the deploy roll back. That would be the wrong answer twice over: restarting the backend
 * does not fix MinIO, and neither does reverting to the previous image. MinIO being down should
 * break media, not revert a release.
 *
 * Spring has no "registered but excluded from the aggregate" contributor, so the lever is the
 * status itself. `SimpleStatusAggregator` filters the reported statuses to those named in
 * `management.endpoint.health.status.order` and takes the lowest-ranked of what remains, so
 * precedence is position in that list. `MEDIA_UNAVAILABLE` is declared *after* `UP`: with the
 * database and Redis healthy, a media outage leaves the aggregate UP and the endpoint answering
 * 200, while the component itself reads MEDIA_UNAVAILABLE for anyone looking.
 *
 * The hazard is moving it *up* — next to `DOWN` — which would turn every media outage into a failed
 * container healthcheck and a rolled-back release. `HealthStatusOrderTest` drives the real
 * aggregator with the real configured order and fails if that ever happens.
 *
 * The component is still visible to an admin on `/actuator/health`, and to whatever monitoring is
 * pointed at it — which is the whole point.
 */
@Component
class MinioHealthIndicator(
    private val mediaProperties: MediaProperties,
    private val probe: BucketProbe
) : HealthIndicator {

    /**
     * The one question worth asking of MinIO, behind an interface so a test can answer it without a
     * server. Deliberately not reusing the adapters' clients: both hold theirs in a `private
     * lateinit` with no accessor, and a health check wants its own short timeouts anyway.
     */
    fun interface BucketProbe {
        /** @return true if the bucket exists. Throws if MinIO cannot be reached. */
        fun bucketExists(): Boolean
    }

    override fun health(): Health {
        val bucket = mediaProperties.minio.bucket
        return try {
            if (probe.bucketExists()) {
                Health.up().withDetail("bucket", bucket).build()
            } else {
                // Reachable but wrong: the server answered and the bucket is not there. Uploads
                // will fail for every user, and no amount of restarting fixes it.
                Health.status(MEDIA_UNAVAILABLE)
                    .withDetail("bucket", bucket)
                    .withDetail("reason", "bucket does not exist")
                    .build()
            }
        } catch (e: Exception) {
            // Never rethrows. An exception out of a HealthIndicator is rendered as DOWN, which is
            // precisely the outcome this class exists to avoid.
            Health.status(MEDIA_UNAVAILABLE)
                .withDetail("bucket", bucket)
                .withDetail("reason", e.message ?: e::class.simpleName ?: "unreachable")
                .build()
        }
    }

    companion object {
        /**
         * Must match the entry in `management.endpoint.health.status.order`, which must sit after
         * `UP`. See the class comment for what happens if it does not.
         */
        val MEDIA_UNAVAILABLE: Status = Status("MEDIA_UNAVAILABLE", "Media storage is not reachable")
    }
}

/**
 * The real probe. A bean of its own rather than a second constructor on the indicator: two
 * constructors would leave Spring to choose between them, and a wrong choice is a context failure
 * that only an integration test would catch — and those need Docker.
 *
 * Its client is built lazily so an unreachable MinIO cannot fail the Spring context at startup, and
 * given short timeouts so an unreachable MinIO cannot hold the health thread for the SDK's default —
 * `/actuator/health` is polled by the compose healthcheck every 30 seconds and by the deploy gate
 * every 10, and neither can afford to block on a dead peer.
 */
@Component
class MinioBucketProbe(private val mediaProperties: MediaProperties) : MinioHealthIndicator.BucketProbe {

    private val client: MinioClient by lazy {
        MinioClient.builder()
            .endpoint(mediaProperties.minio.endpoint)
            .credentials(mediaProperties.minio.accessKey, mediaProperties.minio.secretKey)
            .build()
            .apply { setTimeout(PROBE_TIMEOUT_MS, PROBE_TIMEOUT_MS, PROBE_TIMEOUT_MS) }
    }

    override fun bucketExists(): Boolean =
        client.bucketExists(BucketExistsArgs.builder().bucket(mediaProperties.minio.bucket).build())

    private companion object {
        /** Connect, write and read. Well inside the compose healthcheck's own 10-second timeout. */
        const val PROBE_TIMEOUT_MS = 2_000L
    }
}
