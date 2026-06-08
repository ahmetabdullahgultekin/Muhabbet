package com.muhabbet.auth.adapter.out.external

import com.muhabbet.shared.config.SmsProperties
import com.muhabbet.shared.config.TwilioProperties
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.util.Base64

/**
 * Unit tests for the real Twilio SMS adapter. The HTTP transport (Spring RestClient) is fully
 * mocked — no live Twilio account or network call. We assert the request targets Twilio's
 * Messages REST endpoint with Basic auth + form body, and that a non-2xx maps to OTP_SEND_FAILED.
 */
class TwilioOtpSenderTest {

    private val props = SmsProperties(
        provider = "twilio",
        twilio = TwilioProperties(
            accountSid = "AC123",
            authToken = "tok456",
            fromNumber = "+15005550006",
            apiBaseUrl = "https://api.twilio.com/2010-04-01"
        )
    )

    private class Mocks(
        val builder: RestClient.Builder,
        val uriSlot: io.mockk.CapturingSlot<String>,
        val authHeaderName: io.mockk.CapturingSlot<String>,
        val authHeaderValue: io.mockk.CapturingSlot<String>,
        val contentType: io.mockk.CapturingSlot<MediaType>,
        @Suppress("UNCHECKED_CAST")
        val body: io.mockk.CapturingSlot<MultiValueMap<String, String>>,
        val responseSpec: RestClient.ResponseSpec
    )

    @Suppress("UNCHECKED_CAST")
    private fun wireMocks(): Mocks {
        val restClient = mockk<RestClient>()
        val builder = mockk<RestClient.Builder>()
        val postSpec = mockk<RestClient.RequestBodyUriSpec>()
        val responseSpec = mockk<RestClient.ResponseSpec>()

        val uriSlot = slot<String>()
        val headerName = slot<String>()
        val headerValue = slot<String>()
        val contentType = slot<MediaType>()
        val body = slot<MultiValueMap<String, String>>()

        every { builder.build() } returns restClient
        every { restClient.post() } returns postSpec
        every { postSpec.uri(capture(uriSlot)) } returns postSpec
        every { postSpec.header(capture(headerName), capture(headerValue)) } returns postSpec
        every { postSpec.contentType(capture(contentType)) } returns postSpec
        every { postSpec.body(capture(body) as Any) } returns postSpec
        every { postSpec.retrieve() } returns responseSpec

        return Mocks(builder, uriSlot, headerName, headerValue, contentType, body, responseSpec)
    }

    @Test
    fun `should build correct Twilio Messages request when sending otp`() = runBlocking {
        val m = wireMocks()
        every { m.responseSpec.toBodilessEntity() } returns mockk(relaxed = true)

        TwilioOtpSender(props, m.builder).send("+905001112233", "742980")

        assertEquals("https://api.twilio.com/2010-04-01/Accounts/AC123/Messages.json", m.uriSlot.captured)
        assertEquals("Authorization", m.authHeaderName.captured)
        val expectedAuth = "Basic " + Base64.getEncoder().encodeToString("AC123:tok456".toByteArray())
        assertEquals(expectedAuth, m.authHeaderValue.captured)
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, m.contentType.captured)

        val form = m.body.captured
        assertEquals("+905001112233", form.getFirst("To"))
        assertEquals("+15005550006", form.getFirst("From"))
        assertTrue(form.getFirst("Body")?.contains("742980") == true) { "OTP missing from body" }
    }

    @Test
    fun `should succeed when Twilio returns 2xx`() = runBlocking {
        val m = wireMocks()
        every { m.responseSpec.toBodilessEntity() } returns mockk(relaxed = true)

        // No exception == success
        TwilioOtpSender(props, m.builder).send("+905001112233", "111222")
    }

    @Test
    fun `should throw OTP_SEND_FAILED when Twilio returns non-2xx`() {
        val m = wireMocks()
        every { m.responseSpec.toBodilessEntity() } throws
            RestClientResponseException("unauthorized", HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null)

        val ex = assertThrows(BusinessException::class.java) {
            runBlocking { TwilioOtpSender(props, m.builder).send("+905001112233", "111222") }
        }
        assertEquals(ErrorCode.OTP_SEND_FAILED, ex.errorCode)
    }
}
