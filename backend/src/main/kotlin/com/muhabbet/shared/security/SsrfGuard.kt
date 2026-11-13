package com.muhabbet.shared.security

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * Decides which addresses a server-side outbound fetch (link previews) may reach.
 *
 * This object only *decides*. It does not resolve a name and then hand the name to an HTTP client
 * — that is the shape it had until #916, and it is a time-of-check/time-of-use hole: the client
 * resolves the name a second time, and a rebinding DNS server answers the second lookup with
 * `127.0.0.1`, `169.254.169.254` or a container on the Docker network. Enforcement lives where the
 * connection is made — [SsrfSafeDns] resolves once and hands the HTTP client only vetted addresses,
 * and [SsrfSafeSocketFactory] refuses any socket whose destination is blocked, which also covers IP
 * literals (OkHttp does not consult `Dns` for those) and every redirect hop.
 *
 * ## What is blocked
 * - **IPv4:** every block in the IANA IPv4 Special-Purpose Address Registry that is not globally
 *   reachable, plus multicast and the reserved 240.0.0.0/4 — see [BLOCKED_IPV4].
 * - **IPv6:** an *allowlist*, not a blocklist. Only global unicast (2000::/3) is reachable, minus the
 *   special-purpose blocks inside it ([BLOCKED_IPV6_GLOBAL]). Everything else — loopback, ULA
 *   fc00::/7, link-local fe80::/10, multicast, NAT64 64:ff9b::/96 and 64:ff9b:1::/48, the
 *   IPv4-mapped and IPv4-compatible forms — sits outside 2000::/3 and is refused without having to
 *   be enumerated, so an address family IANA adds later is closed by default rather than open.
 *
 * IPv4-mapped IPv6 (`::ffff:a.b.c.d`) is normally collapsed to an [Inet4Address] by the JDK and
 * then judged by the IPv4 table; should one arrive as a raw [Inet6Address] it is outside 2000::/3
 * and refused anyway.
 *
 * Registry reviewed 2026-11-13: https://www.iana.org/assignments/iana-ipv4-special-registry and
 * https://www.iana.org/assignments/iana-ipv6-special-registry. A few small sub-blocks the registry
 * marks globally reachable (192.0.0.9/32, 192.0.0.10/32, the AMT and ORCHIDv2 blocks inside
 * 2001::/23) are blocked with their parent — no web page is served from them, and a list that is
 * slightly too strict costs one missing preview, while one that is too loose costs the network.
 */
object SsrfGuard : OutboundAddressPolicy {

    private val ALLOWED_SCHEMES = setOf("http", "https")

    /** Thrown when a URL is not one the server may fetch. The message is an error code, not prose. */
    class BlockedUrlException(code: String) : RuntimeException(code)

    /**
     * Parses [rawUrl] and checks everything that can be checked without the network: the scheme is
     * http or https, and there is a host. Deliberately does **not** resolve the host — resolution
     * happens exactly once, at connection time, in [SsrfSafeDns].
     */
    fun parseFetchable(rawUrl: String): URI {
        val uri = try {
            URI(rawUrl.trim())
        } catch (e: Exception) {
            throw BlockedUrlException("URL_MALFORMED")
        }
        rejectionOf(uri)?.let { throw BlockedUrlException(it) }
        return uri
    }

    /** The error code for a parsed URL that may not be fetched, or null when it may. */
    private fun rejectionOf(uri: URI): String? = when {
        uri.scheme?.lowercase() !in ALLOWED_SCHEMES -> "URL_SCHEME_NOT_ALLOWED"
        uri.host.isNullOrBlank() -> "URL_HOST_MISSING"
        // `http://user@host/` is never a link a person meant to preview, and credentials in an
        // outbound request are a leak waiting for a log line.
        uri.rawUserInfo != null -> "URL_USERINFO_NOT_ALLOWED"
        else -> null
    }

    override fun isBlocked(address: InetAddress): Boolean = when (address) {
        is Inet4Address -> BLOCKED_IPV4.any { it.contains(address) }
        is Inet6Address -> !GLOBAL_UNICAST_IPV6.contains(address) ||
            BLOCKED_IPV6_GLOBAL.any { it.contains(address) }
        else -> true
    }

    /**
     * IANA IPv4 special-purpose blocks that are not globally reachable, plus multicast and the
     * reserved range. Each line names the registry entry so a reviewer can check it against the list.
     */
    private val BLOCKED_IPV4: List<Cidr> = listOf(
        "0.0.0.0/8",          // "This network" (RFC 791) — not only 0.0.0.0
        "10.0.0.0/8",         // Private-use (RFC 1918)
        "100.64.0.0/10",      // Shared address space / CGNAT (RFC 6598)
        "127.0.0.0/8",        // Loopback
        "169.254.0.0/16",     // Link-local, incl. the 169.254.169.254 metadata endpoint
        "172.16.0.0/12",      // Private-use (RFC 1918)
        "192.0.0.0/24",       // IETF protocol assignments (RFC 6890)
        "192.0.2.0/24",       // Documentation TEST-NET-1
        "192.88.99.0/24",     // Deprecated 6to4 relay anycast (RFC 7526)
        "192.168.0.0/16",     // Private-use (RFC 1918)
        "198.18.0.0/15",      // Benchmarking (RFC 2544)
        "198.51.100.0/24",    // Documentation TEST-NET-2
        "203.0.113.0/24",     // Documentation TEST-NET-3
        "224.0.0.0/4",        // Multicast
        "240.0.0.0/4",        // Reserved, incl. limited broadcast 255.255.255.255
    ).map(Cidr::parse)

    /** The only IPv6 space a public web server can live in. */
    private val GLOBAL_UNICAST_IPV6: Cidr = Cidr.parse("2000::/3")

    /** Special-purpose blocks that fall inside 2000::/3 and so need naming explicitly. */
    private val BLOCKED_IPV6_GLOBAL: List<Cidr> = listOf(
        "2001::/23",          // IETF protocol assignments: Teredo 2001::/32, benchmarking, ORCHID
        "2001:db8::/32",      // Documentation
        "2002::/16",          // 6to4 — embeds an arbitrary IPv4 address, incl. private ones
        "3fff::/20",          // Documentation (RFC 9637)
    ).map(Cidr::parse)

    /** An address prefix. Compares raw bytes so no address family conversion can confuse it. */
    internal class Cidr private constructor(private val network: ByteArray, private val prefixLength: Int) {

        fun contains(address: InetAddress): Boolean {
            val bytes = address.address
            if (bytes.size != network.size) return false
            val fullBytes = prefixLength / BITS_PER_BYTE
            for (i in 0 until fullBytes) {
                if (bytes[i] != network[i]) return false
            }
            val remainingBits = prefixLength % BITS_PER_BYTE
            if (remainingBits == 0) return true
            val mask = (BYTE_MASK shl (BITS_PER_BYTE - remainingBits)) and BYTE_MASK
            return (bytes[fullBytes].toInt() and mask) == (network[fullBytes].toInt() and mask)
        }

        companion object {
            private const val BITS_PER_BYTE = 8
            private const val BYTE_MASK = 0xFF

            /** Parses a literal CIDR. Only ever called on the constants above, never on input. */
            fun parse(cidr: String): Cidr {
                val (address, length) = cidr.split('/')
                return Cidr(InetAddress.getByName(address).address, length.toInt())
            }
        }
    }
}
