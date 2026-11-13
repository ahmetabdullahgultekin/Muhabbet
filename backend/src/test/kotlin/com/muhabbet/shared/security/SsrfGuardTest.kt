package com.muhabbet.shared.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.InetAddress

/**
 * The address policy behind every server-side outbound fetch (#916).
 *
 * Every address here is a literal, so `InetAddress.getByName` parses it without touching DNS and
 * these tests need no network.
 */
class SsrfGuardTest {

    private fun blocked(literal: String) = SsrfGuard.isBlocked(InetAddress.getByName(literal))

    @ParameterizedTest(name = "blocks {0}")
    @ValueSource(
        strings = [
            // Ranges the guard already covered before #916
            "127.0.0.1", "127.255.255.254", "10.0.0.5", "172.16.0.1", "172.31.255.255", "192.168.1.1",
            "169.254.169.254", "0.0.0.0", "224.0.0.1",
            // Added by #916 — one per range, at an address the old JDK-flag checks let through
            "0.1.2.3",           // 0.0.0.0/8 beyond the single wildcard address
            "100.64.0.1",        // CGNAT 100.64.0.0/10, low edge
            "100.127.255.254",   // CGNAT, high edge
            "192.0.0.8",         // IETF protocol assignments 192.0.0.0/24
            "192.0.2.10",        // TEST-NET-1
            "192.88.99.1",       // deprecated 6to4 relay anycast
            "198.18.0.1",        // benchmarking 198.18.0.0/15, low half
            "198.19.255.254",    // benchmarking, high half
            "198.51.100.7",      // TEST-NET-2
            "203.0.113.9",       // TEST-NET-3
            "240.0.0.1",         // reserved 240.0.0.0/4
            "255.255.255.255",   // limited broadcast
        ]
    )
    fun `blocks non-public ipv4`(address: String) {
        assertTrue(blocked(address), "$address must be blocked")
    }

    @ParameterizedTest(name = "blocks {0}")
    @ValueSource(
        strings = [
            "::1",                     // loopback
            "::",                      // unspecified
            "fe80::1",                 // link-local
            "fc00::1", "fd12:3456::1", // unique-local fc00::/7
            "ff02::1",                 // multicast
            "64:ff9b::7f00:1",         // NAT64 64:ff9b::/96 wrapping 127.0.0.1
            "64:ff9b:1::a00:1",        // local-use NAT64 64:ff9b:1::/48
            "::7f00:1",                // IPv4-compatible ::127.0.0.1
            "100::1",                  // discard-only 100::/64
            "2001::1",                 // Teredo 2001::/32
            "2001:2::1",               // benchmarking
            "2001:db8::1",             // documentation
            "2002:7f00:1::1",          // 6to4 wrapping 127.0.0.1
            "3fff::1",                 // documentation 3fff::/20
        ]
    )
    fun `blocks non-public ipv6`(address: String) {
        assertTrue(blocked(address), "$address must be blocked")
    }

    @Test
    fun `blocks ipv4-mapped ipv6 by the address it maps to`() {
        assertTrue(blocked("::ffff:127.0.0.1"))
        assertTrue(blocked("::ffff:169.254.169.254"))
        assertTrue(blocked("::ffff:100.64.0.1"))
    }

    @ParameterizedTest(name = "allows {0}")
    @ValueSource(
        strings = [
            "8.8.8.8", "1.1.1.1", "93.184.215.14",
            "100.63.255.255",  // just below CGNAT
            "100.128.0.0",     // just above CGNAT
            "198.17.255.255",  // just below benchmarking
            "198.20.0.0",      // just above benchmarking
            "223.255.255.254", // just below multicast
            "2606:4700:4700::1111", "2a00:1450:4001:80b::200e",
        ]
    )
    fun `allows public addresses`(address: String) {
        assertFalse(blocked(address), "$address is public and must be allowed")
    }

    @ParameterizedTest(name = "refuses {0}")
    @ValueSource(
        strings = [
            "file:///etc/passwd", "ftp://example.com/x", "gopher://example.com/", "redis://127.0.0.1:6379",
            "javascript:alert(1)", "http:///nohost", "not a url", "https://user:pw@example.com/",
        ]
    )
    fun `refuses urls that are not plain http or https to a host`(url: String) {
        assertThrows(SsrfGuard.BlockedUrlException::class.java) { SsrfGuard.parseFetchable(url) }
    }

    @Test
    fun `parses an ordinary url without resolving it`() {
        // .invalid is guaranteed never to resolve (RFC 2606) — parsing must not need it to.
        val uri = SsrfGuard.parseFetchable("  https://preview.invalid/a?b=c  ")
        assertEquals("preview.invalid", uri.host)
    }
}
