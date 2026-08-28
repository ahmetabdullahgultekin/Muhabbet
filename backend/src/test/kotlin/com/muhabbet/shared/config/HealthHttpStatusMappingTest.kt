package com.muhabbet.shared.config

import com.muhabbet.media.adapter.out.external.MinioHealthIndicator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper
import org.springframework.boot.health.contributor.Status
import org.yaml.snakeyaml.Yaml

/**
 * Pins the HTTP status code `/actuator/health` answers with, which is the only thing either deploy
 * gate actually reads.
 *
 * `HealthStatusOrderTest` pins which [Status] wins the aggregate. This pins what that status turns
 * into on the wire, and the two are configured by different properties with **opposite** merge
 * behaviour — which is the trap this class exists to hold shut.
 *
 * `management.endpoint.health.status.http-mapping` does not extend the built-in mapping, it
 * replaces it. From the 4.1 bytecode, `HttpCodeStatusMapper.of(map)` returns
 * `SimpleHttpCodeStatusMapper.DEFAULT_MAPPINGS` only when the map is *empty*; for any non-empty map
 * it builds `SimpleHttpCodeStatusMapper(map)`, whose lookup is:
 *
 * ```
 * mappings.getOrDefault(getUniformCode(status.code), 200)
 * ```
 *
 * So the defaults `DOWN -> 503` and `OUT_OF_SERVICE -> 503` survive only while nothing else is
 * configured. Adding a single unrelated entry — `MEDIA_UNAVAILABLE: 200`, added in good faith as
 * "belt and braces" — silently drops both, and `/actuator/health` then answers **200 with a body
 * saying DOWN**. `curl -sf` in `deploy-hetzner.yml` and `curl -f` in the compose healthcheck both
 * look at nothing but the code, so a release with a dead database would sail through the gate that
 * exists to catch exactly that, and the container would keep being reported healthy.
 *
 * That is the failure this suite has to catch, because in production it looks like everything is
 * fine. Anything added to that mapping block must therefore re-state DOWN and OUT_OF_SERVICE, and
 * these tests read the real `application.yml` so they fail if it ever stops doing so.
 */
class HealthHttpStatusMappingTest {

    private val configuredMapping: Map<String, Int> = run {
        val yaml = checkNotNull(javaClass.getResourceAsStream("/application.yml")) {
            "application.yml not on the test classpath"
        }.use { Yaml().load<Map<String, Any>>(it) }

        @Suppress("UNCHECKED_CAST")
        fun dig(vararg path: String): Any? =
            path.fold(yaml as Any?) { node, key -> (node as? Map<String, Any>)?.get(key) }

        val mapping = dig("management", "endpoint", "health", "status", "http-mapping")
        assertNotNull(mapping, "management.endpoint.health.status.http-mapping is not configured")

        @Suppress("UNCHECKED_CAST")
        (mapping as Map<String, Any>).mapValues { (_, v) -> v.toString().toInt() }
    }

    /** The real mapper, built the way Spring builds it, from the real configuration. */
    private val mapper: HttpCodeStatusMapper get() = HttpCodeStatusMapper.of(configuredMapping)

    @Test
    fun `should answer 503 when the aggregate is DOWN`() {
        // The single most important assertion about this endpoint: a dead Postgres or a dead Redis
        // has to fail the container healthcheck and roll the deploy back. It does that by way of
        // the status code and nothing else.
        assertEquals(
            503,
            mapper.getStatusCode(Status.DOWN),
            "DOWN must map to 503. A custom http-mapping REPLACES the built-in one, so every " +
                "entry added to that block has to re-state DOWN or the deploy gate stops working " +
                "while still reporting success."
        )
    }

    @Test
    fun `should answer 503 when the aggregate is OUT_OF_SERVICE`() {
        assertEquals(503, mapper.getStatusCode(Status.OUT_OF_SERVICE))
    }

    @Test
    fun `should answer 200 when media is unavailable`() {
        // The other half of #494: a MinIO outage is visible on the endpoint but must not fail a
        // deploy, because neither restarting the backend nor reverting the release fixes storage.
        assertEquals(200, mapper.getStatusCode(MinioHealthIndicator.MEDIA_UNAVAILABLE))
    }

    @Test
    fun `should answer 200 when everything is up`() {
        assertEquals(200, mapper.getStatusCode(Status.UP))
    }

    @Test
    fun `should keep the built-in failure mappings alongside any custom entry`() {
        // Stated as a property rather than a value, so it still holds for whatever gets added next:
        // if the block is non-empty at all, it owes an explicit entry for both built-in failures.
        listOf(Status.DOWN.code, Status.OUT_OF_SERVICE.code).forEach { code ->
            assertEquals(
                503,
                configuredMapping[code],
                "$code is missing from management.endpoint.health.status.http-mapping. A non-empty " +
                    "mapping discards SimpleHttpCodeStatusMapper.DEFAULT_MAPPINGS entirely and " +
                    "unlisted statuses fall back to 200."
            )
        }
    }
}
