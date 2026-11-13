package com.muhabbet.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * The three numbers that decide when a socket is considered alive, bound from
 * `muhabbet.websocket.*` (#909).
 *
 * `application.yml` declared `heartbeat-interval-seconds: 30` / `heartbeat-timeout-seconds: 90` for
 * months while nothing read them — `WebSocketSessionManager` hardcoded the same values — so an
 * operator who tuned them changed nothing (#918). They are now the only source, and the invariants
 * between them are checked at bind time, because each one is only meaningful relative to another:
 *
 * - [heartbeatInterval] is how often the liveness sweep runs and pings every socket. The mobile
 *   client's own `Ping` cadence (30 s in `WsClient`) should match it.
 * - [heartbeatTimeout] is how long a socket may go without any inbound frame (text, ping reply or
 *   client heartbeat) before it is closed. It must cover several intervals, or one late heartbeat
 *   kills a healthy connection.
 * - [presenceTtl] is the lifetime of the Redis `presence:{id}` key. The sweep refreshes it for every
 *   user who still has a live socket, so it must outlive one interval; it is also the longest a
 *   crashed instance's users go on looking online, so it should not be much longer than that.
 */
@ConfigurationProperties("muhabbet.websocket")
data class WebSocketProperties(
    val heartbeatInterval: Duration = Duration.ofSeconds(30),
    val heartbeatTimeout: Duration = Duration.ofSeconds(90),
    val presenceTtl: Duration = Duration.ofSeconds(60)
) {
    init {
        require(!heartbeatInterval.isNegative && !heartbeatInterval.isZero) {
            "muhabbet.websocket.heartbeat-interval must be positive"
        }
        require(heartbeatTimeout >= heartbeatInterval.multipliedBy(2)) {
            "muhabbet.websocket.heartbeat-timeout ($heartbeatTimeout) must be at least twice the " +
                "heartbeat-interval ($heartbeatInterval), or one late heartbeat closes a healthy socket"
        }
        require(presenceTtl > heartbeatInterval) {
            "muhabbet.websocket.presence-ttl ($presenceTtl) must exceed the heartbeat-interval " +
                "($heartbeatInterval), or presence expires between two refreshing sweeps"
        }
    }
}
