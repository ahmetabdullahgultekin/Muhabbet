package com.muhabbet.shared.config

import com.muhabbet.messaging.adapter.out.external.RedisBroadcastListener
import com.muhabbet.messaging.adapter.out.external.RedisMessageBroadcaster
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter
import org.springframework.data.redis.serializer.StringRedisSerializer

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
 * Messages are published via StringRedisTemplate, so the adapter deserializes with a
 * String serializer (not the JDK default) to match.
 */
@Configuration
class RedisConfig {

    @Bean
    fun redisBroadcastListenerAdapter(listener: RedisBroadcastListener): MessageListenerAdapter =
        MessageListenerAdapter(listener, "handleMessage").apply {
            setSerializer(StringRedisSerializer())
        }

    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
        redisBroadcastListenerAdapter: MessageListenerAdapter
    ): RedisMessageListenerContainer = RedisMessageListenerContainer().apply {
        setConnectionFactory(connectionFactory)
        addMessageListener(
            redisBroadcastListenerAdapter,
            PatternTopic("${RedisMessageBroadcaster.WS_CHANNEL_PREFIX}*")
        )
    }
}
