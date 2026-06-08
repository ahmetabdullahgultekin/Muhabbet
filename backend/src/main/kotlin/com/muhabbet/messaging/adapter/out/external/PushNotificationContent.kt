package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Message

/**
 * Single source of truth for the user-visible push-notification body text derived from a message.
 *
 * Both broadcaster implementations (`RedisMessageBroadcaster` — `@Primary`, prod — and the
 * `WebSocketMessageBroadcaster` no-op fallback) format the push body here so a media message never
 * leaks its raw (often empty or a storage key) `content` into the notification. DRY: one place to
 * change if the placeholder copy evolves.
 */
internal object PushNotificationContent {

    private const val MAX_BODY_LENGTH = 100

    fun bodyFor(message: Message): String = when (message.contentType) {
        ContentType.IMAGE -> "📷 Fotoğraf"
        ContentType.VIDEO -> "🎥 Video"
        ContentType.VOICE -> "🎙️ Sesli mesaj"
        ContentType.DOCUMENT -> "📄 Belge"
        else -> message.content.take(MAX_BODY_LENGTH)
    }
}
