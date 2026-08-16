package com.muhabbet.shared.config

import com.muhabbet.messaging.adapter.out.external.RedisBroadcastListener
import com.muhabbet.messaging.adapter.out.external.RedisMessageBroadcaster
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer

/**
 * Wires the Redis Pub/Sub subscriber for cross-instance WebSocket fan-out.
 *
 * [RedisMessageBroadcaster] publishes to `ws:broadcast:{userId}` when a recipient is NOT
 * connected to the publishing instance. Without a registered listener container nothing
 * subscribed to those channels, so cross-instance delivery silently dropped — the
 * "horizontal WS scaling" path existed only on the publish side. This container subscribes
 * every instance to the pattern and routes each message to [RedisBroadcastListener.handleMessage],
 * which delivers to a local WS session if the user is connected here.
 *
 * Messages are published via StringRedisTemplate, so the body is read back as a UTF-8 string
 * (not the JDK default serializer) to match.
 *
 * The listener is registered as a plain [MessageListener] rather than a `MessageListenerAdapter`.
 * That is not a style choice. Given a two-argument listener method, `MessageListenerAdapter` passes
 * the **pattern the subscription matched** as the second argument, not the channel the message
 * arrived on — so `handleMessage` received the literal `ws:broadcast:*`, `UUID.fromString("*")`
 * threw, and every Redis-routed delivery was discarded while logging
 * `Invalid userId in Redis channel: ws:broadcast:*`. A plain listener hands over the raw
 * [org.springframework.data.redis.connection.Message], whose `channel` is the real one.
 */
@Configuration
class RedisConfig {

    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
        listener: RedisBroadcastListener
    ): RedisMessageListenerContainer = RedisMessageListenerContainer().apply {
        setConnectionFactory(connectionFactory)
        addMessageListener(
            MessageListener { message, _ ->
                listener.handleMessage(
                    String(message.body, Charsets.UTF_8),
                    String(message.channel, Charsets.UTF_8)
                )
            },
            PatternTopic("${RedisMessageBroadcaster.WS_CHANNEL_PREFIX}*")
        )
    }
}
