package com.muhabbet.shared.config

import com.muhabbet.media.adapter.out.external.MinioHealthIndicator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.health.actuate.endpoint.SimpleStatusAggregator
import org.springframework.boot.health.contributor.Status
import org.yaml.snakeyaml.Yaml

/**
 * Pins the configured health-status precedence that decides whether a media outage can fail a
 * deploy.
 *
 * `SimpleStatusAggregator.getAggregateStatus` is, verbatim from the 4.1 bytecode:
 *
 * ```
 * statuses.stream().filter(this::contains).min(comparator).orElse(Status.UNKNOWN)
 * ```
 *
 * where `contains` is `order.contains(uniformCode(status))` and the comparator ranks by
 * `order.indexOf(code)`. Two consequences, and both are worth knowing because both are easy to
 * guess wrong:
 *
 *  - **Position in `order` is the whole of the precedence.** `MEDIA_UNAVAILABLE` is placed after
 *    `UP`, so when the database and Redis are healthy and only MinIO is not, the aggregate stays UP
 *    — the compose healthcheck passes and `deploy-hetzner.yml` does not roll back a release that is
 *    fine. Moving it up next to `DOWN` would silently turn every media outage into a failed deploy.
 *  - **A status absent from `order` is filtered out, not promoted.** It cannot affect the aggregate
 *    at all. (The `indexOf` in the comparator never sees it, so its `-1` is unreachable.)
 *
 * Nothing else in the suite covers this, and getting it wrong breaks releases rather than tests, so
 * these drive the real aggregator with the order read out of the real `application.yml`.
 */
class HealthStatusOrderTest {

    private val configuredOrder: List<String> = run {
        val yaml = checkNotNull(javaClass.getResourceAsStream("/application.yml")) {
            "application.yml not on the test classpath"
        }.use { Yaml().load<Map<String, Any>>(it) }

        @Suppress("UNCHECKED_CAST")
        fun dig(vararg path: String): Any? =
            path.fold(yaml as Any?) { node, key -> (node as? Map<String, Any>)?.get(key) }

        val order = dig("management", "endpoint", "health", "status", "order")
        assertNotNull(order, "management.endpoint.health.status.order is not configured")
        order.toString().split(",").map { it.trim() }
    }

    private val aggregator get() = SimpleStatusAggregator(configuredOrder)

    @Test
    fun `should rank the media status below UP when the health order is configured`() {
        val media = configuredOrder.indexOf(MinioHealthIndicator.MEDIA_UNAVAILABLE.code)
        val up = configuredOrder.indexOf(Status.UP.code)

        assertTrue(media >= 0, "MEDIA_UNAVAILABLE should be declared in the order rather than left implicit")
        assertTrue(
            media > up,
            "MEDIA_UNAVAILABLE must rank below UP. Above it, a MinIO outage becomes the aggregate " +
                "status, which fails the container healthcheck and rolls back deploys."
        )
    }

    @Test
    fun `should stay UP when media is unavailable and everything else is healthy`() {
        val aggregate = aggregator.getAggregateStatus(
            setOf(Status.UP, MinioHealthIndicator.MEDIA_UNAVAILABLE)
        )

        // The property the deploy gate depends on: a media outage must not fail the release.
        assertEquals(Status.UP, aggregate)
    }

    @Test
    fun `should report DOWN when the database is down even if media is also unavailable`() {
        val aggregate = aggregator.getAggregateStatus(
            setOf(Status.DOWN, Status.UP, MinioHealthIndicator.MEDIA_UNAVAILABLE)
        )

        // The other half: keeping MinIO out of the aggregate must not blunt the checks that are
        // supposed to fail a deploy.
        assertEquals(Status.DOWN, aggregate)
    }

    @Test
    fun `should ignore a status that is absent from the order rather than rank it`() {
        // Recorded because it is the opposite of what the comparator alone suggests: `contains`
        // filters unlisted statuses out before `min` ever runs, so an unknown status cannot take
        // the aggregate. Worth a test — it is the difference between "the order line is a nicety"
        // and "the order line is the only thing holding the deploy gate up", and it is the former.
        val aggregate = aggregator.getAggregateStatus(setOf(Status.UP, Status("SOMETHING_UNLISTED")))

        assertEquals(Status.UP, aggregate)
    }

    @Test
    fun `should fall back to UNKNOWN when no reported status appears in the order`() {
        val aggregate = aggregator.getAggregateStatus(setOf(Status("ONE_UNLISTED"), Status("ANOTHER")))

        assertEquals(Status.UNKNOWN, aggregate)
    }
}
