package com.muhabbet.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * Bounded executor for fire-and-forget push notifications.
 *
 * Push delivery (FCM) is a blocking network call. Previously it ran synchronously
 * inside the message broadcast loop ([com.muhabbet.messaging.adapter.out.external.RedisMessageBroadcaster]),
 * so a slow FCM round-trip stalled message fan-out on the hot path. Offloading it to a
 * bounded pool keeps the broadcast loop responsive while capping thread usage under load.
 */
@Configuration
class AsyncConfig {

    @Bean(PUSH_EXECUTOR)
    fun pushExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = PUSH_CORE_POOL_SIZE
        maxPoolSize = PUSH_MAX_POOL_SIZE
        setQueueCapacity(PUSH_QUEUE_CAPACITY)
        setThreadNamePrefix("push-")
        initialize()
    }

    companion object {
        const val PUSH_EXECUTOR = "pushExecutor"
        private const val PUSH_CORE_POOL_SIZE = 2
        private const val PUSH_MAX_POOL_SIZE = 8
        private const val PUSH_QUEUE_CAPACITY = 500
    }
}
