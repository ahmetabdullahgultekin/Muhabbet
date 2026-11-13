package com.muhabbet.shared.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Unit tests for the SSRF guard (Phase 0 / P0-2). Proves that internal / private-network URLs are
 * rejected before any outbound fetch can happen, while ordinary public hostnames are allowed.
 */
class SsrfGuardTest {

    @Test
    fun `rejects loopback ipv4`() {
        assertThrows(SsrfGuard.BlockedUrlException::class.java) {
            SsrfGuard.assertSafe("http://127.0.0.1/secret")
        }
    }

    @Test
    fun `rejects loopback ipv6`() {
        assertThrows(SsrfGuard.BlockedUrlException::class.java) {
            SsrfGuard.assertSafe("http://[::1]/secret")
        }
    }

    @Test
    fun `rejects cloud metadata endpoint`() {
        assertThrows(SsrfGuard.BlockedUrlException::class.java) {
            SsrfGuard.assertSafe("http://169.254.169.254/latest/meta-data/")
        }
    }

    @Test
    fun `rejects rfc1918 private ranges`() {
        listOf(
            "http://10.0.0.5/",
            "http://172.16.0.1/",
            "http://192.168.1.1/"
        ).forEach { url ->
            assertThrows(SsrfGuard.BlockedUrlException::class.java) {
                SsrfGuard.assertSafe(url)
            }
        }
    }

    @Test
    fun `rejects localhost hostname`() {
        assertThrows(SsrfGuard.BlockedUrlException::class.java) {
            SsrfGuard.assertSafe("http://localhost:9000/")
        }
    }

    @Test
    fun `rejects non-http schemes`() {
        listOf(
            "file:///etc/passwd",
            "ftp://example.com/x",
            "gopher://example.com/",
            "redis://127.0.0.1:6379"
        ).forEach { url ->
            assertThrows(SsrfGuard.BlockedUrlException::class.java) {
                SsrfGuard.assertSafe(url)
            }
        }
    }

    @Test
    fun `rejects wildcard address`() {
        assertThrows(SsrfGuard.BlockedUrlException::class.java) {
            SsrfGuard.assertSafe("http://0.0.0.0/")
        }
    }

    @Test
    fun `allows ordinary public hostname`() {
        // 8.8.8.8 is a stable public address (Google DNS) — passes the IP-range checks.
        val uri = SsrfGuard.assertSafe("https://8.8.8.8/")
        assertEquals("8.8.8.8", uri.host)
    }

    @Test
    fun `should reject when the address is in a shared or reserved range`() {
        listOf(
            "http://100.64.0.1/",        // CGNAT / overlay networks
            "http://100.127.255.254/",
            "http://0.1.2.3/",           // 0.0.0.0/8
            "http://198.18.0.1/",        // benchmarking
            "http://240.0.0.1/",         // reserved
            "http://[64:ff9b::7f00:1]/", // NAT64 to 127.0.0.1
            "http://[2002:7f00:1::]/"    // 6to4 wrapping 127.0.0.1
        ).forEach { url ->
            assertThrows(SsrfGuard.BlockedUrlException::class.java, { SsrfGuard.assertSafe(url) }, url)
        }
    }

    @Test
    fun `should reject when the url names a port other than the web defaults`() {
        assertThrows(SsrfGuard.BlockedUrlException::class.java) {
            SsrfGuard.assertSafe("http://93.184.215.14:6379/")
        }
    }

    @Test
    fun `should accept when the url names the default port explicitly`() {
        SsrfGuard.assertSafe("https://93.184.215.14:443/")
        SsrfGuard.assertSafe("http://93.184.215.14:80/")
    }
}
