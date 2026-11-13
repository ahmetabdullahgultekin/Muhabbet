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
    private val ALLOWED_PORTS = setOf(80, 443)

    /**
     * IPv4 ranges the JDK predicates below do not cover, as (network, prefix length): "this
     * network", shared address space (CGNAT, and overlay networks such as Tailscale that a home
     * server may sit on), the IETF protocol block, benchmarking, and the reserved class E block
     * (which includes broadcast).
     */
    private val EXTRA_BLOCKED_V4 = listOf(
        "0.0.0.0" to 8,
        "100.64.0.0" to 10,
        "192.0.0.0" to 24,
        "198.18.0.0" to 15,
        "240.0.0.0" to 4
    ).map { (net, bits) -> InetAddress.getByName(net).address to bits }

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

        // Only the web's own ports. Anything else turns a preview into a port scanner for
        // whatever the server can reach, public or not.
        if (uri.port != -1 && uri.port !in ALLOWED_PORTS) {
            throw BlockedUrlException("URL_PORT_NOT_ALLOWED")
        }

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
            isUniqueLocalIpv6(addr) ||      // fc00::/7
            inExtraBlockedV4(addr.address) ||
            embeddedV4IsBlocked(addr)       // NAT64 64:ff9b::/96 and 6to4 2002::/16

    private fun inExtraBlockedV4(bytes: ByteArray): Boolean =
        bytes.size == 4 && EXTRA_BLOCKED_V4.any { (net, bits) -> prefixMatches(bytes, net, bits) }

    private fun prefixMatches(bytes: ByteArray, net: ByteArray, bits: Int): Boolean {
        val value = bytes.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        val network = net.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        val mask = (0xFFFFFFFFL shl (32 - bits)) and 0xFFFFFFFFL
        return (value and mask) == (network and mask)
    }

    /**
     * An IPv6 address that carries an IPv4 one — NAT64 (`64:ff9b::/96`, the last four bytes) or
     * 6to4 (`2002::/16`, bytes 2–5) — is judged by the IPv4 address it reaches.
     */
    private fun embeddedV4IsBlocked(addr: InetAddress): Boolean {
        val b = addr.address
        if (b.size != 16) return false
        val embedded = when {
            b[0] == 0x00.toByte() && b[1] == 0x64.toByte() && b[2] == 0xFF.toByte() && b[3] == 0x9B.toByte() &&
                (4..11).all { b[it] == 0.toByte() } -> b.copyOfRange(12, 16)
            b[0] == 0x20.toByte() && b[1] == 0x02.toByte() -> b.copyOfRange(2, 6)
            else -> return false
        }
        return isBlockedAddress(InetAddress.getByAddress(embedded))
    }

    private fun isUniqueLocalIpv6(addr: InetAddress): Boolean {
        val bytes = addr.address
        // IPv6 unique-local: high 7 bits == 1111110 (fc00::/7)
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    }
}
