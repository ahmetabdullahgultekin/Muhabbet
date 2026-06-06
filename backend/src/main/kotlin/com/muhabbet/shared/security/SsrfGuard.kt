package com.muhabbet.shared.security

import java.net.InetAddress
import java.net.URI

/**
 * SSRF protection for server-side outbound fetches (e.g. link-preview).
 *
 * Blocks requests that would let an authenticated user point the backend at internal
 * infrastructure: RFC-1918 private ranges, loopback (127.0.0.0/8, ::1), link-local
 * (169.254.0.0/16 incl. the 169.254.169.254 cloud metadata endpoint, fe80::/10),
 * unique-local IPv6 (fc00::/7), wildcard/multicast, and any non-http(s) scheme.
 *
 * Every hostname is DNS-resolved and EVERY resolved address is checked, so a name that
 * resolves to a private IP is rejected. Callers MUST re-run [assertSafe] on each redirect
 * hop (resolve-then-fetch still leaves a TOCTOU window, but re-validating redirects closes
 * the common "public URL 302s to 169.254.169.254" bypass).
 */
object SsrfGuard {

    private val ALLOWED_SCHEMES = setOf("http", "https")

    /** Thrown when a URL is not safe to fetch from the server. */
    class BlockedUrlException(message: String) : RuntimeException(message)

    /**
     * Validates [rawUrl] and returns the parsed [URI]. Throws [BlockedUrlException] if the scheme
     * is not http/https, the host is missing, or any resolved address is private/loopback/link-local.
     */
    fun assertSafe(rawUrl: String): URI {
        val uri = try {
            URI(rawUrl.trim())
        } catch (e: Exception) {
            throw BlockedUrlException("URL_MALFORMED")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme == null || scheme !in ALLOWED_SCHEMES) {
            throw BlockedUrlException("URL_SCHEME_NOT_ALLOWED")
        }

        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: throw BlockedUrlException("URL_HOST_MISSING")

        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) {
            throw BlockedUrlException("URL_HOST_UNRESOLVABLE")
        }

        if (addresses.isEmpty() || addresses.any { isBlockedAddress(it) }) {
            throw BlockedUrlException("URL_TARGETS_INTERNAL_ADDRESS")
        }

        return uri
    }

    private fun isBlockedAddress(addr: InetAddress): Boolean =
        addr.isLoopbackAddress ||           // 127.0.0.0/8, ::1
            addr.isLinkLocalAddress ||      // 169.254.0.0/16 (incl. 169.254.169.254), fe80::/10
            addr.isSiteLocalAddress ||      // 10/8, 172.16/12, 192.168/16
            addr.isAnyLocalAddress ||       // 0.0.0.0, ::
            addr.isMulticastAddress ||
            isUniqueLocalIpv6(addr)         // fc00::/7

    private fun isUniqueLocalIpv6(addr: InetAddress): Boolean {
        val bytes = addr.address
        // IPv6 unique-local: high 7 bits == 1111110 (fc00::/7)
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    }
}
