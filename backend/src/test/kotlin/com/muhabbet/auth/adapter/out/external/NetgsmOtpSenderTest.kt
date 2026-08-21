package com.muhabbet.auth.adapter.out.external

import com.muhabbet.shared.config.NetgsmProperties
import com.muhabbet.shared.config.SmsProperties
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * Unit tests for the Netgsm SMS adapter.
 *
 * The adapter builds its own `RestClient` in a private field, so there is no seam to mock and the
 * transport is the only way in. Rather than change production code to make it injectable, these
 * tests point `muhabbet.sms.netgsm.api-url` at a loopback [HttpServer] on an ephemeral port: no
 * Netgsm account, no outbound network, and the assertions are on the bytes actually sent.
 *
 * What is pinned: the XML envelope Netgsm documents (credentials, message header, recipient with
 * the leading `+` stripped, the code inside the CDATA body), and the response-code contract —
 * only a body starting with `00` counts as sent, everything else raises.
 */
class NetgsmOtpSenderTest {

    private lateinit var server: HttpServer
    private val lastBody = AtomicReference<String>()
    private val lastContentType = AtomicReference<String>()

    /** Response the fake Netgsm answers with; `null` means "200 with no body at all". */
    @Volatile
    private var responseBody: String? = "00 123456789"

    @Volatile
    private var responseStatus: Int = 200

    @BeforeEach
    fun startFakeNetgsm() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/sms/send/xml") { exchange ->
            lastBody.set(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
            lastContentType.set(exchange.requestHeaders.getFirst("Content-Type"))
            val payload = responseBody
            if (payload == null) {
                exchange.sendResponseHeaders(responseStatus, -1) // -1 = no body
            } else {
                val bytes = payload.toByteArray()
                exchange.sendResponseHeaders(responseStatus, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
    }

    @AfterEach
    fun stopFakeNetgsm() {
        server.stop(0)
    }

    private fun sender() = NetgsmOtpSender(
        SmsProperties(
            provider = "netgsm",
            netgsm = NetgsmProperties(
                usercode = "8501234567",
                password = "s3cr3t",
                msgheader = "MUHABBET",
                apiUrl = "http://127.0.0.1:${server.address.port}/sms/send/xml"
            )
        )
    )

    @Test
    fun `should post the documented Netgsm XML envelope when sending otp`() {
        sender().send("+905001112233", "428190")

        val body = lastBody.get() ?: fail("fake Netgsm was never called")
        assertTrue(lastContentType.get().orEmpty().startsWith("application/xml")) {
            "unexpected content type: ${lastContentType.get()}"
        }
        assertTrue(body.contains("<usercode>8501234567</usercode>")) { body }
        assertTrue(body.contains("<password>s3cr3t</password>")) { body }
        assertTrue(body.contains("<msgheader>MUHABBET</msgheader>")) { body }
        // Netgsm expects 905XXXXXXXXX — the leading '+' must be stripped.
        assertTrue(body.contains("<no>905001112233</no>")) { body }
        assertFalse(body.contains("+905001112233")) { "the leading '+' reached Netgsm: $body" }
        assertTrue(body.contains("428190")) { "OTP code missing from message: $body" }
    }

    @Test
    fun `should succeed when Netgsm returns the 00 success code`() {
        responseBody = "00 987654321"

        // No exception == sent.
        sender().send("+905001112233", "111222")
    }

    @Test
    fun `should fail when Netgsm returns an error code`() {
        responseBody = "30" // 30 = invalid usercode/password

        val ex = assertThrows(RuntimeException::class.java) {
            sender().send("+905001112233", "111222")
        }
        assertTrue(ex.message.orEmpty().contains("Netgsm SMS failed")) { ex.message.orEmpty() }
    }

    @Test
    fun `should fail when Netgsm answers 200 with no body`() {
        responseBody = null

        assertThrows(RuntimeException::class.java) {
            sender().send("+905001112233", "111222")
        }
    }

    @Test
    fun `should not swallow a transport error`() {
        responseStatus = 502
        responseBody = "bad gateway"

        // The adapter logs and rethrows: a failed OTP send must not look like a sent one.
        assertThrows(RuntimeException::class.java) {
            sender().send("+905001112233", "111222")
        }
    }
}
