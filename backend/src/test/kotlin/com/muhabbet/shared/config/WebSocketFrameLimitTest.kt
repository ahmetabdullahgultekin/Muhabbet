package com.muhabbet.shared.config

import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import com.muhabbet.shared.validation.ValidationRules
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The transport must accept everything the validator declares legal.
 *
 * Until #493 it did not: `MESSAGE_MAX_LENGTH` allowed 10,000 characters and Tomcat's untouched
 * 8192-character buffer closed the socket at about 7,949 — measured against production, not
 * inferred. What the user saw was not "message too long", it was the chat disconnecting, over and
 * over, with nothing in the server log but a transport error carrying a null message, because the
 * frame died before `handleTextMessage` ever ran.
 *
 * So this is a relationship test, not a constant test. Asserting `MAX_MESSAGE_BUFFER == 65536`
 * would pass just as happily on the day somebody raises `MESSAGE_MAX_LENGTH` to 100,000 and
 * silently reopens the same hole. What has to hold is that the buffer is bigger than the biggest
 * frame the rest of the code can legitimately produce.
 */
class WebSocketFrameLimitTest {

    /**
     * The worst case, built rather than estimated: a maximum-length body inside a `SendMessage`
     * with every optional field populated, serialised by the same `wsJson` the handler uses.
     */
    private fun worstCaseFrame(): String {
        val body = "ç".repeat(ValidationRules.MESSAGE_MAX_LENGTH)
        return wsJson.encodeToString(
            WsMessage.serializer(),
            WsMessage.SendMessage(
                requestId = UUID.randomUUID().toString(),
                messageId = UUID.randomUUID().toString(),
                conversationId = UUID.randomUUID().toString(),
                content = body,
                replyToId = UUID.randomUUID().toString(),
                mediaUrl = "https://cdn-muhabbet.116.203.222.213.nip.io/muhabbet-media/" +
                    "${UUID.randomUUID()}.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=" +
                    "${UUID.randomUUID()}&X-Amz-Date=20260817T120000Z&X-Amz-Expires=604800&" +
                    "X-Amz-SignedHeaders=host&X-Amz-Signature=${UUID.randomUUID()}",
                thumbnailUrl = "https://cdn-muhabbet.116.203.222.213.nip.io/muhabbet-media/" +
                    "${UUID.randomUUID()}_thumb.jpg"
            )
        )
    }

    @Test
    fun `the websocket buffer must hold the longest message validation allows`() {
        // Characters, not bytes: Tomcat decodes a text frame into a CharBuffer of this size. That
        // is why a Turkish body, two bytes per character in UTF-8, was cut off at exactly the same
        // character count as an ASCII one when this was measured against production.
        val frameChars = worstCaseFrame().length

        assertTrue(
            frameChars < WebSocketConfig.MAX_MESSAGE_BUFFER,
            "A message of ValidationRules.MESSAGE_MAX_LENGTH (${ValidationRules.MESSAGE_MAX_LENGTH}) " +
                "characters serialises to a $frameChars-character frame, which does not fit in the " +
                "${WebSocketConfig.MAX_MESSAGE_BUFFER}-character WebSocket buffer. Tomcat will close " +
                "the connection with 1009 before the handler runs, so the sender sees the chat " +
                "disconnect rather than an error they can read (#493). Raise MAX_MESSAGE_BUFFER or " +
                "lower MESSAGE_MAX_LENGTH — the two layers have to agree on one number."
        )
    }

    @Test
    fun `the buffer must not be Tomcat's untouched default`() {
        // The default is 8192 and it is the whole of #493. A future edit that removes the
        // ServletServerContainerFactoryBean would leave the first test passing only by accident of
        // arithmetic while the running server was back on 8192 -- so pin that the number is chosen.
        val tomcatDefault = 8192
        assertTrue(
            WebSocketConfig.MAX_MESSAGE_BUFFER > tomcatDefault,
            "MAX_MESSAGE_BUFFER is at or below Tomcat's inherited default of $tomcatDefault."
        )
    }
}
