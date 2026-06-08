package com.muhabbet.auth.adapter.out.external

import com.muhabbet.shared.config.NetgsmProperties
import com.muhabbet.shared.config.SmsProperties
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.http.HttpStatus

/**
 * Unit tests for the real Netgsm SMS adapter. The HTTP transport (Spring RestClient) is fully
 * mocked — no live Netgsm account or network call. We assert the request is built per Netgsm's
 * documented GET API and that the plaintext status code maps correctly to the domain contract.
 */
class NetgsmOtpSenderTest {

    private val props = SmsProperties(
        provider = "netgsm",
        netgsm = NetgsmProperties(
            usercode = "8501234567",
            password = "s3cr3t",
            msgheader = "MUHABBET",
            apiUrl = "https://api.netgsm.com.tr/sms/send/get"
        )
    )

    /** Mocks the `restClient.get().uri(uri).retrieve().body(String::class.java)` chain. */
    private fun senderReturning(body: String?, uriSlot: io.mockk.CapturingSlot<String>): NetgsmOtpSender {
        val restClient = mockk<RestClient>()
        val builder = mockk<RestClient.Builder>()
        val getSpec = mockk<RestClient.RequestHeadersUriSpec<*>>()
        val headersSpec = mockk<RestClient.RequestHeadersSpec<*>>()
        val responseSpec = mockk<RestClient.ResponseSpec>()

        every { builder.build() } returns restClient
        every { restClient.get() } returns getSpec
        every { getSpec.uri(capture(uriSlot)) } returns headersSpec
        every { headersSpec.retrieve() } returns responseSpec
        every { responseSpec.body(String::class.java) } returns body

        return NetgsmOtpSender(props, builder)
    }

    @Test
    fun `should build correct Netgsm GET request when sending otp`() = runBlocking {
        val uriSlot = slot<String>()
        val sender = senderReturning("00 123456789", uriSlot)

        sender.send("+905001112233", "428190")

        val uri = uriSlot.captured
        assertTrue(uri.startsWith("https://api.netgsm.com.tr/sms/send/get")) { uri }
        assertTrue(uri.contains("usercode=8501234567")) { uri }
        assertTrue(uri.contains("password=s3cr3t")) { uri }
        // '+' is stripped — Netgsm expects 905XXXXXXXXX
        assertTrue(uri.contains("gsmno=905001112233")) { uri }
        assertTrue(uri.contains("msgheader=MUHABBET")) { uri }
        assertTrue(uri.contains("428190")) { "OTP code missing from message: $uri" }
    }

    @Test
    fun `should succeed when Netgsm returns 00 success code`() = runBlocking {
        val uriSlot = slot<String>()
        val sender = senderReturning("00 987654321", uriSlot)

        // No exception == success
        sender.send("+905001112233", "111222")
    }

    @Test
    fun `should throw OTP_SEND_FAILED when Netgsm returns error code`() {
        val uriSlot = slot<String>()
        val sender = senderReturning("30", uriSlot) // 30 = invalid usercode/password

        val ex = assertThrows(BusinessException::class.java) {
            runBlocking { sender.send("+905001112233", "111222") }
        }
        assertEquals(ErrorCode.OTP_SEND_FAILED, ex.errorCode)
    }

    @Test
    fun `should throw OTP_SEND_FAILED when Netgsm returns null body`() {
        val uriSlot = slot<String>()
        val sender = senderReturning(null, uriSlot)

        val ex = assertThrows(BusinessException::class.java) {
            runBlocking { sender.send("+905001112233", "111222") }
        }
        assertEquals(ErrorCode.OTP_SEND_FAILED, ex.errorCode)
    }

    @Test
    fun `should throw OTP_SEND_FAILED when transport raises an exception`() {
        val restClient = mockk<RestClient>()
        val builder = mockk<RestClient.Builder>()
        val getSpec = mockk<RestClient.RequestHeadersUriSpec<*>>()
        val headersSpec = mockk<RestClient.RequestHeadersSpec<*>>()
        val responseSpec = mockk<RestClient.ResponseSpec>()

        every { builder.build() } returns restClient
        every { restClient.get() } returns getSpec
        every { getSpec.uri(any<String>()) } returns headersSpec
        every { headersSpec.retrieve() } returns responseSpec
        every { responseSpec.body(String::class.java) } throws
            RestClientResponseException("boom", HttpStatus.BAD_GATEWAY, "Bad Gateway", null, null, null)

        val sender = NetgsmOtpSender(props, builder)
        val ex = assertThrows(BusinessException::class.java) {
            runBlocking { sender.send("+905001112233", "111222") }
        }
        assertEquals(ErrorCode.OTP_SEND_FAILED, ex.errorCode)
    }
}
