package com.muhabbet.shared.config

import com.muhabbet.messaging.adapter.`in`.websocket.ChatWebSocketHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val chatWebSocketHandler: ChatWebSocketHandler
) : WebSocketConfigurer {

    companion object {
        /**
         * Room for the largest frame [com.muhabbet.shared.validation.ValidationRules.MESSAGE_MAX_LENGTH]
         * can produce, with the envelope and every optional field on top, and room to spare.
         *
         * Tomcat's default is 8192 and nothing here used to raise it, so the transport rejected
         * messages the application had declared legal (#493). Measured against production before
         * the fix: the last frame accepted carried **7,949 characters**, and 9,000 closed the
         * socket with 1009 — while `MESSAGE_MAX_LENGTH` says 10,000.
         *
         * The unit is **characters, not bytes**: Tomcat decodes a text frame into a `CharBuffer` of
         * this size. That was worth measuring rather than assuming — the issue reasoned that
         * Turkish text, being two bytes per character in UTF-8, would halve the window, and it does
         * not: an ASCII body and a Turkish body were both cut off at the same 7,949 characters.
         *
         * Still bounded rather than unlimited. A frame is buffered whole before dispatch, so this
         * is memory an authenticated client can make the server hold; 64 K characters against the
         * existing 50-messages-per-10-seconds limiter is a ceiling, not an invitation. Oversized
         * content is still refused by `MessageService` with `MSG_CONTENT_TOO_LONG`, which is an
         * error the user can read — the point of this is that the socket survives long enough to
         * deliver it.
         */
        const val MAX_MESSAGE_BUFFER = 64 * 1024
    }

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(chatWebSocketHandler, "/ws")
            .setAllowedOrigins(
                "https://muhabbet.rollingcatsoftware.com"
            )
    }

    /**
     * Both limits are set explicitly. Setting only the text one would leave binary frames on the
     * inherited 8192 — a default nobody chose, which is precisely how this bug arrived.
     */
    @Bean
    fun webSocketContainer(): ServletServerContainerFactoryBean =
        ServletServerContainerFactoryBean().apply {
            setMaxTextMessageBufferSize(MAX_MESSAGE_BUFFER)
            setMaxBinaryMessageBufferSize(MAX_MESSAGE_BUFFER)
        }
}
