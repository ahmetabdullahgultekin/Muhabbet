package com.muhabbet.shared.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class ClientInfoTest {

    @Test
    fun `should round-trip the header form`() {
        val info = ClientInfo(protocolVersion = 1, appVersion = "0.4.2", platform = "android")

        assertEquals("protocol=1;app=0.4.2;platform=android", info.toHeaderValue())
        assertEquals(info, ClientInfo.parseHeader(info.toHeaderValue()))
    }

    @Test
    fun `should treat a missing header as the legacy protocol`() {
        assertEquals(ClientInfo.LEGACY, ClientInfo.parseHeader(null))
        assertEquals(ClientInfo.LEGACY, ClientInfo.parseHeader(""))
    }

    @Test
    fun `should treat an unreadable or negative version as the legacy protocol`() {
        assertEquals(ClientInfo.LEGACY, ClientInfo.parseHeader("protocol=abc;app=1"))
        assertEquals(ClientInfo.LEGACY, ClientInfo.of("-3", "1.0", "ios"))
    }

    @Test
    fun `should strip characters that do not belong in a version or platform before they reach a log`() {
        val info = ClientInfo.of("1", "1.0\n<script>", "and roid")

        assertEquals("1.0script", info.appVersion)
        assertEquals("android", info.platform)
    }

    @Test
    fun `should build from websocket query parameters the same way`() {
        assertEquals(
            ClientInfo(protocolVersion = WireProtocol.VERSION, appVersion = "0.5.0", platform = "ios"),
            ClientInfo.of(WireProtocol.VERSION.toString(), "0.5.0", "ios"),
        )
    }
}
