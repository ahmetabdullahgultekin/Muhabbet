package com.muhabbet.shared.web

import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.ClientInfo
import com.muhabbet.shared.protocol.MuhabbetJson
import com.muhabbet.shared.protocol.WireProtocol
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/** #873: a client below the minimum protocol is told to upgrade, with a code, before anything else. */
class ClientProtocolFilterTest {

    private fun call(minSupported: Int, path: String = "/api/v1/conversations", header: String? = null): Pair<MockHttpServletResponse, MockFilterChain> {
        val request = MockHttpServletRequest("GET", path).apply {
            requestURI = path
            header?.let { addHeader(WireProtocol.CLIENT_HEADER, it) }
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        ClientProtocolFilter(minSupported).doFilter(request, response, chain)
        return response to chain
    }

    @Test
    fun `should answer 426 with CLIENT_UPGRADE_REQUIRED when the client is below the minimum`() {
        val (response, chain) = call(minSupported = 2, header = ClientInfo(protocolVersion = 1).toHeaderValue())

        assertEquals(426, response.status)
        assertNull(chain.request, "the request must not reach the application")
        val body = MuhabbetJson.decodeFromString(ApiResponse.serializer(Unit.serializer()), response.contentAsString)
        assertEquals(ErrorCode.CLIENT_UPGRADE_REQUIRED, body.error?.code)
    }

    @Test
    fun `should treat a request without the header as the legacy protocol`() {
        val (response, _) = call(minSupported = 1)

        assertEquals(426, response.status)
    }

    @Test
    fun `should pass a client at the minimum straight through`() {
        val (response, chain) = call(minSupported = 1, header = ClientInfo(protocolVersion = 1).toHeaderValue())

        assertEquals(200, response.status)
        assertNotNull(chain.request)
    }

    @Test
    fun `should refuse nobody at the default minimum, including builds that predate versioning`() {
        val (_, chain) = call(minSupported = WireProtocol.LEGACY_VERSION)

        assertNotNull(chain.request)
    }

    @Test
    fun `should never gate the health check the container probes without a header`() {
        val (_, chain) = call(minSupported = 5, path = "/actuator/health")

        assertNotNull(chain.request)
    }
}
