package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.model.LinkPreview
import com.muhabbet.messaging.domain.model.LinkPreviewFetchResult
import com.muhabbet.messaging.domain.model.LinkPreviewFetchResult.Busy
import com.muhabbet.messaging.domain.model.LinkPreviewFetchResult.Fetched
import com.muhabbet.messaging.domain.model.LinkPreviewFetchResult.Unavailable
import com.muhabbet.shared.config.LinkPreviewProperties
import com.muhabbet.shared.security.OutboundAddressPolicy
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.Dns
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The SSRF and resource bounds of the link-preview fetcher (#916), against real sockets.
 *
 * Two real HTTP servers stand in for the two sides of the boundary, on the **same port**:
 *  - `127.0.0.1` plays "the public internet" — the test policy allows it;
 *  - `127.0.0.2` plays an internal service (MinIO, Postgres, the metadata endpoint) — blocked.
 *
 * Both are loopback, so the real [com.muhabbet.shared.security.SsrfGuard] would block both; the
 * policy is the one thing substituted. Everything that decides *whether a connection is made* — the
 * resolver hook, the socket guard, the hand-rolled redirect loop, the OkHttp client configuration —
 * is the production code. The fake resolver lets each test decide what a name resolves to, and when.
 *
 * The internal server counts every request it receives. "No connection was made" is asserted as
 * that count staying at zero, not inferred from a null result.
 */
class SafeHttpLinkPreviewFetcherTest {

    private val publicAddress: InetAddress = InetAddress.getByName("127.0.0.1")
    private val internalAddress: InetAddress = InetAddress.getByName("127.0.0.2")
    private val policy = OutboundAddressPolicy { it != publicAddress }

    private lateinit var publicServer: HttpServer
    private lateinit var internalServer: HttpServer
    private val internalHits = AtomicInteger()
    private val publicHits = AtomicInteger()
    private var port = 0
    private val serverThreads = Executors.newCachedThreadPool()

    /** name → successive answers; the last answer repeats. */
    private val dnsAnswers = mutableMapOf<String, ArrayDeque<InetAddress>>()
    private val lookups = AtomicInteger()
    private val fakeDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            lookups.incrementAndGet()
            val answers = dnsAnswers[hostname] ?: throw UnknownHostException(hostname)
            return listOf(if (answers.size > 1) answers.removeFirst() else answers.first())
        }
    }

    @BeforeEach
    fun startServers() {
        publicServer = HttpServer.create(InetSocketAddress(publicAddress, 0), 0)
        port = publicServer.address.port
        internalServer = HttpServer.create(InetSocketAddress(internalAddress, port), 0)
        internalServer.createContext("/") { exchange ->
            internalHits.incrementAndGet()
            respond(exchange, 200, "text/html", "<html><head><title>INTERNAL SECRET</title></head></html>")
        }
        listOf(publicServer, internalServer).forEach {
            it.executor = serverThreads
            it.start()
        }
    }

    @AfterEach
    fun stopServers() {
        publicServer.stop(0)
        internalServer.stop(0)
        serverThreads.shutdownNow()
    }

    private fun fetcher(
        totalTimeout: Duration = Duration.ofSeconds(3),
        maxBodyBytes: Long = 64L * 1024,
        maxRedirects: Int = 5,
        maxConcurrentFetches: Int = 8,
    ) = SafeHttpLinkPreviewFetcher(
        LinkPreviewProperties(
            totalTimeout = totalTimeout,
            connectTimeout = Duration.ofSeconds(1),
            maxBodyBytes = maxBodyBytes,
            maxRedirects = maxRedirects,
            maxConcurrentFetches = maxConcurrentFetches,
        ),
        policy,
        fakeDns,
    )

    private fun publicRoute(path: String, handler: (HttpExchange) -> Unit) {
        publicServer.createContext(path) { exchange ->
            publicHits.incrementAndGet()
            handler(exchange)
        }
    }

    private fun resolve(name: String, vararg answers: InetAddress) {
        dnsAnswers[name] = ArrayDeque(answers.toList())
    }

    @Test
    fun `reads open graph metadata from a public page`() {
        resolve("news.test", publicAddress)
        publicRoute("/article") {
            respond(
                it, 200, "text/html; charset=utf-8",
                """<html><head><title>Fallback</title>
                   <meta property="og:title" content="Çay saati">
                   <meta property="og:description" content="Bir açıklama">
                   <meta property="og:image" content="/img/cover.jpg">
                   <meta property="og:site_name" content="Haber"></head></html>""",
            )
        }

        val preview = fetcher().fetch("http://news.test:$port/article").fetched()

        assertEquals("Çay saati", preview.title)
        assertEquals("Bir açıklama", preview.description)
        assertEquals("http://news.test:$port/img/cover.jpg", preview.imageUrl)
        assertEquals("Haber", preview.siteName)
    }

    @Test
    fun `dns rebinding cannot reach the internal address - the name is resolved once and that address is used`() {
        // A rebinding server: public on the first lookup, internal on every one after. The old code
        // looked the name up twice — once to check, once (inside Jsoup) to connect.
        resolve("rebind.test", publicAddress, internalAddress)
        publicRoute("/") { respond(it, 200, "text/html", "<title>public page</title>") }

        val preview = fetcher().fetch("http://rebind.test:$port/").fetched()

        assertEquals("public page", preview.title)
        assertEquals(1, lookups.get(), "the name must be resolved exactly once per connection")
        assertEquals(0, internalHits.get(), "no request may reach the internal address")
    }

    @Test
    fun `a name that resolves to an internal address is never connected to`() {
        resolve("internal.test", internalAddress)

        assertEquals(Unavailable, fetcher().fetch("http://internal.test:$port/"))
        assertEquals(0, internalHits.get())
    }

    @Test
    fun `a name with one public and one internal address is refused outright`() {
        val mixedDns = object : Dns {
            override fun lookup(hostname: String) = listOf(publicAddress, internalAddress)
        }
        val fetcher = SafeHttpLinkPreviewFetcher(LinkPreviewProperties(), policy, mixedDns)
        publicRoute("/") { respond(it, 200, "text/html", "<title>public</title>") }

        assertEquals(Unavailable, fetcher.fetch("http://mixed.test:$port/"))
        assertEquals(0, publicHits.get())
        assertEquals(0, internalHits.get())
    }

    @Test
    fun `an ip literal pointing inside is refused at the socket - dns is never consulted for literals`() {
        assertEquals(Unavailable, fetcher().fetch("http://127.0.0.2:$port/latest/meta-data/"))
        assertEquals(0, internalHits.get())
    }

    @Test
    fun `a redirect to an internal ip literal is not followed`() {
        resolve("public.test", publicAddress)
        publicRoute("/") { redirect(it, "http://127.0.0.2:$port/secret") }

        assertEquals(Unavailable, fetcher().fetch("http://public.test:$port/"))
        assertEquals(1, publicHits.get())
        assertEquals(0, internalHits.get(), "the redirect target must never be contacted")
    }

    @Test
    fun `a redirect to a name that resolves inside is not followed`() {
        resolve("public.test", publicAddress)
        resolve("shared-minio", internalAddress)
        publicRoute("/") { redirect(it, "http://shared-minio:$port/muhabbet-media/") }

        assertEquals(Unavailable, fetcher().fetch("http://public.test:$port/"))
        assertEquals(0, internalHits.get())
    }

    @Test
    fun `a redirect to a non-http scheme is not followed`() {
        resolve("public.test", publicAddress)
        publicRoute("/") { redirect(it, "file:///etc/passwd") }

        assertEquals(Unavailable, fetcher().fetch("http://public.test:$port/"))
    }

    @Test
    fun `a relative redirect on a public host is followed`() {
        resolve("public.test", publicAddress)
        publicRoute("/old") { redirect(it, "/new") }
        publicRoute("/new") { respond(it, 200, "text/html", "<title>moved here</title>") }

        assertEquals("moved here", fetcher().fetch("http://public.test:$port/old").fetched().title)
    }

    @Test
    fun `the redirect chain is capped`() {
        resolve("loop.test", publicAddress)
        publicRoute("/") { redirect(it, "/") }

        assertEquals(Unavailable, fetcher(maxRedirects = 3).fetch("http://loop.test:$port/"))
        assertEquals(4, publicHits.get(), "the first request plus three redirects, then stop")
    }

    @Test
    fun `only the first maxBodyBytes of the page are read`() {
        resolve("big.test", publicAddress)
        val padding = "x".repeat(200_000)
        publicRoute("/early") {
            respond(it, 200, "text/html", "<head><meta property=\"og:title\" content=\"early\"></head><body>$padding</body>")
        }
        publicRoute("/late") {
            respond(it, 200, "text/html", "<head><!-- $padding --><meta property=\"og:title\" content=\"late\"></head>")
        }
        val fetcher = fetcher(maxBodyBytes = 16L * 1024)

        assertEquals("early", fetcher.fetch("http://big.test:$port/early").fetched().title)
        assertNull(fetcher.fetch("http://big.test:$port/late").fetched().title, "a tag past the cap must not be seen")
    }

    @Test
    fun `a declared non-html response is not parsed`() {
        resolve("files.test", publicAddress)
        publicRoute("/movie.mp4") { respond(it, 200, "video/mp4", "<title>not a page</title>") }

        assertEquals(Unavailable, fetcher().fetch("http://files.test:$port/movie.mp4"))
    }

    @Test
    fun `a server that never answers is abandoned at the deadline`() {
        resolve("slow.test", publicAddress)
        publicRoute("/") {
            Thread.sleep(5_000)
            respond(it, 200, "text/html", "<title>too late</title>")
        }

        val started = System.nanoTime()
        val preview = fetcher(totalTimeout = Duration.ofMillis(300)).fetch("http://slow.test:$port/")
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

        assertEquals(Unavailable, preview)
        assertTrue(elapsedMs < 2_000, "took ${elapsedMs}ms against a 300ms deadline")
    }

    @Test
    fun `the deadline spans the whole redirect chain, not each hop`() {
        // Every hop alone is well inside the deadline; five of them together are not. A per-hop
        // timeout would let this run for 5 x 200ms and return the final page.
        resolve("chain.test", publicAddress)
        publicRoute("/") { exchange ->
            Thread.sleep(200)
            val hop = exchange.requestURI.query?.toIntOrNull() ?: 0
            if (hop < 4) redirect(exchange, "/?${hop + 1}") else respond(exchange, 200, "text/html", "<title>end</title>")
        }

        val started = System.nanoTime()
        val preview = fetcher(totalTimeout = Duration.ofMillis(500)).fetch("http://chain.test:$port/")
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

        assertEquals(Unavailable, preview)
        assertTrue(elapsedMs < 900, "took ${elapsedMs}ms against a 500ms total deadline")
    }

    @Test
    fun `when every fetch slot is busy a request is refused at once instead of queueing`() {
        resolve("slow.test", publicAddress)
        val release = CountDownLatch(1)
        val firstArrived = CountDownLatch(1)
        publicRoute("/") {
            firstArrived.countDown()
            release.await(5, TimeUnit.SECONDS)
            respond(it, 200, "text/html", "<title>slow</title>")
        }
        val fetcher = fetcher(maxConcurrentFetches = 1)
        val first = Executors.newSingleThreadExecutor().submit<Any?> { fetcher.fetch("http://slow.test:$port/") }
        assertTrue(firstArrived.await(2, TimeUnit.SECONDS))

        val started = System.nanoTime()
        val second = fetcher.fetch("http://slow.test:$port/")
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        release.countDown()
        first.get(5, TimeUnit.SECONDS)

        assertEquals(Busy, second, "overload is a fact about this instance, not Unavailable for the URL")
        assertTrue(elapsedMs < 200, "the refused request waited ${elapsedMs}ms")
        assertEquals(1, publicHits.get(), "the refused request must not have been sent")
    }

    @Test
    fun `an unexpected runtime failure while reading the page is Unavailable, not an exception`() {
        // A resolver that misbehaves with something other than an IOException stands in for any
        // library surprise; the caller must get an answer about the URL, not a 500.
        val brokenDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = error("boom")
        }
        val fetcher = SafeHttpLinkPreviewFetcher(LinkPreviewProperties(maxConcurrentFetches = 1), policy, brokenDns)

        assertEquals(Unavailable, fetcher.fetch("http://broken.test:$port/"))
        // The slot was released: a second call is not reported Busy.
        assertEquals(Unavailable, fetcher.fetch("http://broken.test:$port/"))
    }

    private fun LinkPreviewFetchResult.fetched(): LinkPreview = assertInstanceOf(Fetched::class.java, this).preview

    private fun redirect(exchange: HttpExchange, location: String) {
        exchange.responseHeaders.add("Location", location)
        exchange.sendResponseHeaders(302, -1)
        exchange.close()
    }

    private fun respond(exchange: HttpExchange, status: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        // A reader that stops early (the byte cap) closes the connection mid-write; that is the
        // client doing its job, not a server failure worth failing the test over.
        runCatching { exchange.responseBody.use { it.write(bytes) } }
        exchange.close()
    }
}
