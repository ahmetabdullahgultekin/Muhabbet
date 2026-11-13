package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.model.LinkPreview
import com.muhabbet.messaging.domain.port.out.LinkPreviewFetchPort
import com.muhabbet.shared.config.LinkPreviewProperties
import com.muhabbet.shared.security.BlockedAddressException
import com.muhabbet.shared.security.BlockedHostException
import com.muhabbet.shared.security.OutboundAddressPolicy
import com.muhabbet.shared.security.SsrfGuard
import com.muhabbet.shared.security.SsrfSafeDns
import com.muhabbet.shared.security.SsrfSafeSocketFactory
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.Proxy
import java.net.URI
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Fetches link previews without letting a caller reach the internal network (#916).
 *
 * The previous implementation checked a hostname with `InetAddress.getAllByName` and then handed the
 * *name* to Jsoup, which resolved it again — so a rebinding DNS server could pass the check with a
 * public address and be connected to on `127.0.0.1` or a container on the Docker network. Here the
 * HTTP client never resolves anything itself:
 *
 *  - **Resolve once, connect to what was vetted.** [SsrfSafeDns] is OkHttp's resolver; OkHttp
 *    connects to exactly the addresses it returns and uses the hostname only for Host, SNI and
 *    certificate checks, so TLS still verifies against the real name.
 *  - **Every socket is checked.** [SsrfSafeSocketFactory] refuses a blocked destination at
 *    `connect`, which covers IP-literal URLs and redirects to them (OkHttp skips `Dns` for literals).
 *  - **Redirects are followed by hand**, one hop at a time, each through the same client and so the
 *    same two checks, with a hop cap.
 *  - **No proxy.** A proxy would do the resolving on our behalf and none of the above would apply.
 *
 * And it is bounded, because a fetch on behalf of a caller should not be able to hold the server:
 * one wall-clock deadline across all hops, a byte cap on the one body that is read, a refusal to
 * read anything that is not HTML, and a bulkhead on concurrent fetches — when it is full the caller
 * gets an empty preview immediately rather than a Tomcat thread queued behind slow sites.
 *
 * What it cannot bound: a JDK DNS lookup is not interruptible, so a pathologically slow resolver can
 * outlast the deadline by the resolver's own timeout. The bulkhead still caps how many such threads
 * exist.
 */
@Component
class SafeHttpLinkPreviewFetcher internal constructor(
    private val properties: LinkPreviewProperties,
    policy: OutboundAddressPolicy,
    dns: Dns,
) : LinkPreviewFetchPort {

    @Autowired
    constructor(properties: LinkPreviewProperties) : this(properties, SsrfGuard, Dns.SYSTEM)

    private val log = LoggerFactory.getLogger(javaClass)

    private val inFlight = Semaphore(properties.maxConcurrentFetches)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .dns(SsrfSafeDns(policy, dns))
        .socketFactory(SsrfSafeSocketFactory(policy))
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(properties.connectTimeout)
        .readTimeout(properties.totalTimeout)
        .writeTimeout(properties.totalTimeout)
        .callTimeout(properties.totalTimeout)
        .build()

    override fun fetch(url: String): LinkPreview? {
        if (!inFlight.tryAcquire()) {
            log.warn("Link preview skipped: {} fetches already in flight", properties.maxConcurrentFetches)
            return null
        }
        return try {
            fetchFollowingRedirects(url, deadlineNanos = System.nanoTime() + properties.totalTimeout.toNanos())
        } catch (e: BlockedHostException) {
            log.warn("Link preview refused, host resolves to an internal address: {}", e.message)
            null
        } catch (e: BlockedAddressException) {
            log.warn("Link preview refused, connection to an internal address: {}", e.message)
            null
        } catch (e: SsrfGuard.BlockedUrlException) {
            log.info("Link preview refused: {}", e.message)
            null
        } catch (e: IOException) {
            log.debug("Link preview fetch failed: {}", e.toString())
            null
        } finally {
            inFlight.release()
        }
    }

    private fun fetchFollowingRedirects(initialUrl: String, deadlineNanos: Long): LinkPreview? {
        var current: URI = SsrfGuard.parseFetchable(initialUrl)
        repeat(properties.maxRedirects + 1) {
            val remaining = deadlineNanos - System.nanoTime()
            if (remaining <= 0) throw IOException("LINK_PREVIEW_DEADLINE_EXCEEDED")

            val httpUrl = current.toString().toHttpUrlOrNull()
                ?: throw SsrfGuard.BlockedUrlException("URL_MALFORMED")
            val request = Request.Builder()
                .url(httpUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", ACCEPT)
                .build()
            val call = client.newCall(request)
            // Whatever is left of the one deadline, not a fresh budget per hop.
            call.timeout().timeout(remaining, TimeUnit.NANOSECONDS)

            call.execute().use { response ->
                if (response.isRedirect) {
                    val location = response.header("Location") ?: return null
                    // A Location URI cannot parse is a broken redirect, not a reason to throw.
                    val next = runCatching { current.resolve(location.trim()) }.getOrNull() ?: return null
                    current = SsrfGuard.parseFetchable(next.toString())
                    return@repeat
                }
                if (!response.isSuccessful || !isHtml(response.body.contentType())) return null
                return parse(initialUrl, current, response.body)
            }
        }
        log.info("Link preview refused: more than {} redirects", properties.maxRedirects)
        return null
    }

    private fun parse(requestedUrl: String, finalUrl: URI, body: ResponseBody): LinkPreview {
        val source = body.source()
        val buffer = Buffer()
        while (buffer.size < properties.maxBodyBytes) {
            if (source.read(buffer, properties.maxBodyBytes - buffer.size) == -1L) break
        }
        val document = Jsoup.parse(
            buffer.inputStream(),
            body.contentType()?.charset()?.name(),
            finalUrl.toString(),
        )
        return OpenGraphParser.parse(requestedUrl, document)
    }

    /** A declared non-HTML type (an image, a video, a zip) is never read. Undeclared is given a chance. */
    private fun isHtml(type: MediaType?): Boolean =
        type == null ||
            (type.type == "text" && type.subtype == "html") ||
            (type.type == "application" && type.subtype == "xhtml+xml")

    private companion object {
        const val USER_AGENT = "MuhabbetBot/1.0 (+link preview)"
        const val ACCEPT = "text/html,application/xhtml+xml;q=0.9"
    }
}
