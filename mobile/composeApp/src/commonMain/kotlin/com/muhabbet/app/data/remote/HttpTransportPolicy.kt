package com.muhabbet.app.data.remote

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isWebsocket
import kotlinx.coroutines.delay
import kotlinx.io.IOException

/**
 * Every transport decision the REST client makes, in one place (#822).
 *
 * The client used to install no timeout and no retry at all: a request on a stalled network hung
 * until the platform engine gave up (minutes on OkHttp's read timeout, never on some), and a GET
 * that met a momentary 503 failed outright although repeating it is harmless by definition.
 *
 * **Timeouts.** [connectTimeoutMs] bounds the handshake and [socketTimeoutMs] the silence between
 * two packets — the "stalled" detector. [requestTimeoutMs] bounds an ordinary call end to end.
 * Uploads take [uploadRequestTimeoutMs] instead (see [ApiClient.upload]): a 40 MB video on 3G is a
 * legitimate several-minute request, and the socket timeout still catches it if it actually stalls.
 * Ktor's `HttpTimeout` does not apply to WebSocket or other upgrade requests, so the long-lived chat
 * socket that shares this client is untouched.
 *
 * **Retry.** Only for [idempotent][isIdempotent] methods, only on a 5xx or a transport failure, and
 * never for a WebSocket handshake (`WsClient` owns its own reconnect backoff — stacking two would
 * multiply them). A POST is never retried: the server may have acted on the first one, and a send,
 * a vote or an OTP request repeated by the transport is a duplicate the user did not ask for. The
 * delay is exponential with jitter so a fleet of clients recovering from one outage does not hit
 * the server in lock-step.
 *
 * A request that exhausted its whole [requestTimeoutMs] is not retried either: the user has
 * already waited that long, and doing it again silently would double the wait before they see
 * anything.
 *
 * Production uses [Default]; tests pass a policy with millisecond delays and a recording [sleep].
 */
data class HttpTransportPolicy(
    val connectTimeoutMs: Long = 10_000,
    val socketTimeoutMs: Long = 30_000,
    val requestTimeoutMs: Long = 30_000,
    val uploadRequestTimeoutMs: Long = 10 * 60_000,
    val uploadSocketTimeoutMs: Long = 60_000,
    val maxRetries: Int = 3,
    val retryBaseDelayMs: Long = 500,
    val retryMaxDelayMs: Long = 8_000,
    val retryJitterMs: Long = 250,
    val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    companion object {
        val Default = HttpTransportPolicy()

        /** RFC 9110 §9.2.2. POST and PATCH are not idempotent; everything else this app sends is. */
        fun isIdempotent(method: HttpMethod): Boolean = method in IDEMPOTENT_METHODS

        private val IDEMPOTENT_METHODS = setOf(
            HttpMethod.Get, HttpMethod.Head, HttpMethod.Options, HttpMethod.Put, HttpMethod.Delete,
        )

        /** A transport failure worth repeating: connection refused/reset, DNS, a stalled socket. */
        fun isRetryableFailure(cause: Throwable): Boolean =
            cause is IOException && cause !is HttpRequestTimeoutException

        /** Base delay before retry number [retry] (1-based), before jitter: base·2^(retry-1), capped. */
        fun backoffMs(policy: HttpTransportPolicy, retry: Int): Long {
            val exponent = (retry - 1).coerceIn(0, MAX_EXPONENT)
            return (policy.retryBaseDelayMs shl exponent).coerceAtMost(policy.retryMaxDelayMs)
        }

        private const val MAX_EXPONENT = 20
    }
}

/** Installs [policy]'s timeouts and retry rules on a client under construction. */
internal fun HttpClientConfig<*>.installTransportPolicy(policy: HttpTransportPolicy) {
    install(HttpTimeout) {
        connectTimeoutMillis = policy.connectTimeoutMs
        socketTimeoutMillis = policy.socketTimeoutMs
        requestTimeoutMillis = policy.requestTimeoutMs
    }
    install(HttpRequestRetry) {
        maxRetries = policy.maxRetries
        retryIf { request, response ->
            !request.url.protocol.isWebsocket() &&
                HttpTransportPolicy.isIdempotent(request.method) &&
                response.status.value in SERVER_ERROR_RANGE
        }
        retryOnExceptionIf { request, cause ->
            !request.url.protocol.isWebsocket() &&
                HttpTransportPolicy.isIdempotent(request.method) &&
                HttpTransportPolicy.isRetryableFailure(cause)
        }
        delayMillis { retry ->
            HttpTransportPolicy.backoffMs(policy, retry) +
                if (policy.retryJitterMs > 0) kotlin.random.Random.nextLong(policy.retryJitterMs) else 0
        }
        delay { policy.sleep(it) }
    }
}

private val SERVER_ERROR_RANGE = 500..599

/**
 * True when [url] is on the API's own host, the only host the session token may be sent to.
 *
 * The bearer predicate used to look at the path alone, so any absolute URL fetched through the
 * shared client — a media download from the CDN host, a link preview — carried the user's JWT to
 * whoever served it. Scheme is checked too: the token must never travel over cleartext to a host
 * that merely shares the API's name.
 */
internal fun isApiHost(url: Url, apiBase: Url): Boolean =
    url.host.equals(apiBase.host, ignoreCase = true) &&
        url.protocol.name == apiBase.protocol.name &&
        url.port == apiBase.port

/** [isApiHost] for the WebSocket handshake, which runs on `wss` against the same host. */
internal fun isApiSocket(url: Url, apiBase: Url): Boolean =
    url.host.equals(apiBase.host, ignoreCase = true) &&
        (url.protocol == URLProtocol.WSS && apiBase.protocol == URLProtocol.HTTPS ||
            url.protocol == URLProtocol.WS && apiBase.protocol == URLProtocol.HTTP)
