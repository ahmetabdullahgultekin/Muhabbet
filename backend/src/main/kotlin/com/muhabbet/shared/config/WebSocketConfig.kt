package com.muhabbet.shared.config

import com.muhabbet.messaging.adapter.`in`.websocket.ChatWebSocketHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
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
     *
     * **Not registered under the `test` profile**, and that is not a convenience.
     * `ServletServerContainerFactoryBean` reaches into the running servlet container for
     * `jakarta.websocket.server.ServerContainer`, which only an *embedded* one publishes. Every
     * integration test here is a plain `@SpringBootTest` — `webEnvironment = MOCK` — so there is no
     * embedded container and the bean fails the whole application context with
     *
     *     Attribute 'jakarta.websocket.server.ServerContainer' not found in ServletContext
     *
     * That took out all 32 tests in the ten `@Testcontainers` classes the first time CI managed to
     * run them (#563 had been failing every job in *Set up job* before any step, so the regression
     * shipped unseen). It never affected production: Tomcat is real there, which is why the
     * deployed fix verified — a 9,999-character message was acked over a live socket.
     *
     * A profile check rather than `@ConditionalOnBean(ServletWebServerFactory::class)`, which reads
     * better and would be wrong: `@ConditionalOnBean` is only dependable inside auto-configuration,
     * and in a user `@Configuration` it is evaluated before `ServletWebServerFactoryAutoConfiguration`
     * has registered anything — so it would silently answer "no" in production too, which is the
     * failure mode that cannot be seen from a green test run.
     *
     * What it costs: the buffer size is not exercised under the `test` profile. Nothing is lost
     * today — no test opens a real WebSocket — but a future test that wants to prove the 64 K limit
     * must ask for a real server (`webEnvironment = RANDOM_PORT`) and its own profile.
     */
    @Bean
    @Profile("!test")
    fun webSocketContainer(): ServletServerContainerFactoryBean =
        ServletServerContainerFactoryBean().apply {
            setMaxTextMessageBufferSize(MAX_MESSAGE_BUFFER)
            setMaxBinaryMessageBufferSize(MAX_MESSAGE_BUFFER)
        }
}
