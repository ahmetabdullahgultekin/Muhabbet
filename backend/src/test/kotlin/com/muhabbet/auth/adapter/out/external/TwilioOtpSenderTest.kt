package com.muhabbet.auth.adapter.out.external

import com.muhabbet.shared.config.SmsProperties
import com.muhabbet.shared.config.TwilioProperties
import com.twilio.rest.api.v2010.account.Message
import com.twilio.rest.api.v2010.account.MessageCreator
import com.twilio.type.PhoneNumber
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for the Twilio Messages SMS adapter (`muhabbet.sms.provider=twilio`).
 *
 * This is not the provider production runs — that is `twilio-verify`, which uses
 * [TwilioVerifyOtpVerifier] and never touches this class. The Messages path is still selectable by
 * configuration, so it is still worth pinning that it addresses the right recipient and carries the
 * code.
 *
 * The adapter talks to the Twilio SDK through statics (`Twilio.init` in its constructor,
 * `Message.creator(...)` on send), so the SDK's static entry point is mocked out. No account, no
 * network. What is *not* covered: anything the real Twilio API would validate about the numbers.
 */
class TwilioOtpSenderTest {

    private val props = SmsProperties(
        provider = "twilio",
        twilio = TwilioProperties(
            accountSid = "AC00000000000000000000000000000000",
            authToken = "tok456",
            fromNumber = "+15005550006"
        )
    )

    private lateinit var creator: MessageCreator

    @BeforeEach
    fun mockTwilioSdk() {
        mockkStatic(Message::class)
        creator = mockk()
    }

    @AfterEach
    fun unmockTwilioSdk() {
        unmockkStatic(Message::class)
    }

    @Test
    fun `should address the recipient from the configured number and carry the otp`() {
        val to = slot<PhoneNumber>()
        val from = slot<PhoneNumber>()
        val body = slot<String>()
        every { Message.creator(capture(to), capture(from), capture(body)) } returns creator
        every { creator.create() } returns mockk<Message>(relaxed = true)

        TwilioOtpSender(props).send("+905001112233", "742980")

        assertEquals("+905001112233", to.captured.endpoint)
        assertEquals("+15005550006", from.captured.endpoint)
        assertTrue(body.captured.contains("742980")) { "OTP missing from body: ${body.captured}" }
    }

    @Test
    fun `should not swallow a failed send`() {
        every { Message.creator(any<PhoneNumber>(), any<PhoneNumber>(), any<String>()) } returns creator
        every { creator.create() } throws RuntimeException("twilio unavailable")

        // A swallowed failure would report "code sent" to a user who never receives one.
        assertThrows(RuntimeException::class.java) {
            TwilioOtpSender(props).send("+905001112233", "111222")
        }
    }
}
