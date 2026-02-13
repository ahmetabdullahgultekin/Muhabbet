package com.muhabbet.shared.analytics

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Lightweight analytics event tracker.
 * Logs structured events for Grafana/Loki ingestion.
 * No external dependency — just structured JSON logging.
 */
@Component
class AnalyticsEventTracker {

    private val log = LoggerFactory.getLogger("analytics")

    fun track(event: AnalyticsEvent) {
        log.info(
            "event={} userId={} properties={} timestamp={}",
            event.name,
            event.userId,
            event.properties,
            event.timestamp
        )
    }

    fun trackUserAction(userId: UUID, action: String, properties: Map<String, Any?> = emptyMap()) {
        track(AnalyticsEvent(name = action, userId = userId.toString(), properties = properties))
    }

    fun trackSystemEvent(name: String, properties: Map<String, Any?> = emptyMap()) {
        track(AnalyticsEvent(name = name, properties = properties))
    }
}

data class AnalyticsEvent(
    val name: String,
    val userId: String? = null,
    val properties: Map<String, Any?> = emptyMap(),
    val timestamp: Instant = Instant.now()
)
