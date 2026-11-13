package com.muhabbet.shared.security

import okhttp3.Dns
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.SocketFactory

/** Decides whether an outbound connection to an address is forbidden. [SsrfGuard] is the real one. */
fun interface OutboundAddressPolicy {
    fun isBlocked(address: InetAddress): Boolean
}

/** A host whose resolved addresses include one the policy forbids. Never connected to. */
class BlockedHostException(host: String) : UnknownHostException("URL_TARGETS_INTERNAL_ADDRESS: $host")

/** A socket destination the policy forbids. Thrown before the TCP handshake starts. */
class BlockedAddressException(address: InetAddress) :
    SocketException("URL_TARGETS_INTERNAL_ADDRESS: ${address.hostAddress}")

/**
 * The resolve-once half of the #916 fix.
 *
 * OkHttp calls `Dns.lookup` once per new connection and then connects to exactly the addresses it
 * returned, using the *hostname* only for the Host header, SNI and certificate verification. So the
 * addresses vetted here are the addresses connected to: there is no second lookup for a rebinding
 * server to answer differently, which is the hole `SsrfGuard.assertSafe` + `Jsoup.connect(name)`
 * had.
 *
 * If **any** resolved address is blocked the whole host is refused rather than filtered down to its
 * public addresses. A name that resolves to both a public and a private address is either a
 * misconfiguration or an attack, and quietly connecting to the public half would hide the second.
 */
class SsrfSafeDns(
    private val policy: OutboundAddressPolicy,
    private val delegate: Dns = Dns.SYSTEM,
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (addresses.isEmpty()) throw UnknownHostException(hostname)
        if (addresses.any(policy::isBlocked)) throw BlockedHostException(hostname)
        return addresses
    }
}

/**
 * The backstop: whatever path produced the address, a socket to a blocked destination is never
 * opened.
 *
 * [SsrfSafeDns] alone is not enough, because OkHttp skips `Dns` entirely for IP-literal hosts — a
 * redirect to `http://169.254.169.254/` would otherwise go straight through. Checking at
 * `Socket.connect` covers literals, every redirect hop, and any future code path in the client,
 * because nothing reaches the network without a socket. For HTTPS, OkHttp layers TLS over the
 * socket this factory created, so the check covers both schemes.
 */
class SsrfSafeSocketFactory(private val policy: OutboundAddressPolicy) : SocketFactory() {

    override fun createSocket(): Socket = GuardedSocket(policy)

    override fun createSocket(host: String, port: Int): Socket =
        createSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        createSocket().apply {
            bind(InetSocketAddress(localHost, localPort))
            connect(InetSocketAddress(host, port))
        }

    override fun createSocket(host: InetAddress, port: Int): Socket =
        createSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        createSocket().apply {
            bind(InetSocketAddress(localAddress, localPort))
            connect(InetSocketAddress(address, port))
        }

    /** `Socket.connect(SocketAddress)` delegates to this overload, so this is the only door. */
    private class GuardedSocket(private val policy: OutboundAddressPolicy) : Socket() {
        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            val address = (endpoint as? InetSocketAddress)?.address
                ?: throw SocketException("URL_HOST_UNRESOLVED")
            if (policy.isBlocked(address)) throw BlockedAddressException(address)
            super.connect(endpoint, timeout)
        }
    }
}
