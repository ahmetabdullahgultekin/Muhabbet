package com.muhabbet.app.data.remote

import com.muhabbet.app.BuildInfo
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.shared.dto.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.content.PartData
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import com.muhabbet.app.util.Log
import kotlinx.io.Buffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * The app's REST client.
 *
 * Every verb **throws [ApiException] on a non-2xx status**. It used to log the status and decode the
 * body regardless, and because the error envelope is a valid `ApiResponse` with `data = null`, a
 * rejection was indistinguishable from an empty answer — see [ApiException]. Callers must therefore
 * treat a returned [ApiResponse] as a request the server accepted, and handle the exception.
 *
 * @param engine supplied only by tests, so they can drive the repositories through a mock engine
 * instead of reaching the network. Production callers pass nothing and get the platform engine.
 */
class ApiClient(
    private val tokenStorage: TokenStorage,
    engine: HttpClientEngine? = null,
    @PublishedApi internal val transportPolicy: HttpTransportPolicy = HttpTransportPolicy.Default,
) {

    companion object {
        // Must match the Traefik Host() rule in the repo-root docker-compose.prod.yml (NOT
        // infra/docker-compose.prod.yml — that is the legacy nginx stack and carries no Traefik
        // labels). No certificate is issued for any other name, so a mismatch fails the TLS
        // handshake instead of 404-ing.
        const val BASE_URL = "https://muhabbet-api.rollingcatsoftware.com"
        @PublishedApi internal const val TAG = "ApiClient"

        /** Multipart field name the media endpoints read the file from. */
        private const val UPLOAD_FILE_FIELD = "file"

        private val API_BASE = Url(BASE_URL)

        private val PRINTABLE_ASCII = 0x20..0x7E

        /**
         * The only endpoints that run before there is a signed-in user, and so the only ones the
         * bearer token is withheld from.
         *
         * Listed exactly, not by prefix. `/api/v1/auth/` is not a synonym for "unauthenticated" —
         * `two-step` and `login-approvals` live under it and identify the caller from the token
         * alone. Matching a prefix is what broke two-step verification (#544), and the failure
         * direction of an exact list is the safe one: a token sent to an endpoint that ignores it
         * costs nothing, while a token withheld from one that needs it is a 401.
         */
        internal val PRE_LOGIN_PATHS = setOf(
            "/api/v1/auth/otp/request",
            "/api/v1/auth/otp/verify",
            "/api/v1/auth/firebase-verify",
            "/api/v1/auth/token/refresh",
        )
    }

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val clientConfig: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            json(json)
        }

        install(Logging) {
            // SECURITY: LogLevel.HEADERS leaks the Authorization bearer token to logs. Never log
            // headers. Debug builds log method+URL+status only (LogLevel.INFO); release logs nothing.
            level = if (BuildInfo.DEBUG) LogLevel.INFO else LogLevel.NONE
        }

        install(WebSockets)

        // Timeouts and idempotent-only retry (#822). Installed before Auth so that a retried
        // request goes back through the bearer interceptor exactly like the first attempt.
        installTransportPolicy(transportPolicy)

        install(Auth) {
            bearer {
                loadTokens {
                    val access = tokenStorage.getAccessToken()
                    val refresh = tokenStorage.getRefreshToken()
                    if (access != null && refresh != null) {
                        BearerTokens(access, refresh)
                    } else null
                }
                refreshTokens {
                    val refresh = tokenStorage.getRefreshToken() ?: return@refreshTokens null
                    try {
                        val response = client.post("$BASE_URL/api/v1/auth/token/refresh") {
                            contentType(ContentType.Application.Json)
                            setBody(mapOf("refreshToken" to refresh))
                        }
                        // Only clear tokens if server explicitly rejects the refresh token
                        if (response.status.value in listOf(401, 403)) {
                            Log.w(TAG, "Refresh token rejected (${response.status})")
                            tokenStorage.clear()
                            return@refreshTokens null
                        }
                        if (!response.status.isSuccess()) {
                            Log.w(TAG, "Token refresh server error (${response.status})")
                            return@refreshTokens null
                        }
                        val text = response.bodyAsText()
                        val body = json.decodeFromString(
                            ApiResponse.serializer(serializer<Map<String, String>>()),
                            text
                        )
                        val data = body.data ?: run {
                            tokenStorage.clear()
                            return@refreshTokens null
                        }
                        val newAccess = data["accessToken"] ?: return@refreshTokens null
                        val newRefresh = data["refreshToken"] ?: return@refreshTokens null
                        tokenStorage.saveTokens(
                            accessToken = newAccess,
                            refreshToken = newRefresh,
                            userId = tokenStorage.getUserId() ?: "",
                            deviceId = tokenStorage.getDeviceId() ?: ""
                        )
                        BearerTokens(newAccess, newRefresh)
                    } catch (e: Exception) {
                        // Network error — don't clear tokens, user stays logged in
                        Log.e(TAG, "Token refresh failed (network): ${e.message}")
                        null
                    }
                }
                // Attach the bearer token up front unless the endpoint is one of the four that runs
                // before anyone is signed in. It used to skip **any** path with an `auth` segment,
                // which swept in `/api/v1/auth/two-step/**` — endpoints that identify the caller
                // purely from the token — so the two-step screen was the one caller in the app
                // whose requests went out anonymously (#544). `/api/v1/auth/**` is `permitAll` on
                // the server, so nothing stopped them at the filter chain either: they reached the
                // controller with an empty SecurityContext and came back 401 AUTH_UNAUTHORIZED.
                //
                // And only to the API's own host (#822): the predicate used to look at the path
                // alone, so an absolute URL to any other host fetched through this client carried
                // the user's JWT to whoever served it.
                sendWithoutRequest { request ->
                    val url = request.url.build()
                    (isApiHost(url, API_BASE) || isApiSocket(url, API_BASE)) &&
                        url.encodedPath !in PRE_LOGIN_PATHS
                }
            }

            // A 401 from a pre-login endpoint is the server rejecting a **code**, not challenging
            // for a **token**, and Ktor cannot tell the two apart on its own.
            //
            // The Auth plugin retries every 401. Where the request carried a bearer token it
            // refreshes first; where it did not — which is exactly the paths above, by
            // `sendWithoutRequest` — it has no token version recorded against the request, so
            // `refreshTokenIfNeeded` returns true without refreshing anything and the request is
            // replayed unchanged. No `/auth/token/refresh` is sent, which is why nothing in a log
            // hints at a retry: there are simply two identical POSTs.
            //
            // `AUTH_OTP_INVALID` is 401 and `AuthService.verifyOtp` claims an attempt *before* it
            // compares the code, so both copies counted. One entered code cost two of the five
            // configured attempts and the third typo locked the user out (#400).
            //
            // This narrows the predicate rather than removing the retry: every other 401 still
            // re-authorizes exactly as before, or no session would survive its first expiry.
            reAuthorizeOnResponse { response ->
                // A 401 from another host must not be answered with our token either (#822).
                response.status == HttpStatusCode.Unauthorized &&
                    isApiHost(response.request.url, API_BASE) &&
                    response.request.url.encodedPath !in PRE_LOGIN_PATHS
            }
        }

        defaultRequest {
            url(BASE_URL)
            contentType(ContentType.Application.Json)
        }
    }

    val httpClient =
        if (engine == null) HttpClient(clientConfig) else HttpClient(engine, clientConfig)

    suspend inline fun <reified T> get(path: String): ApiResponse<T> {
        Log.d(TAG, "GET $path")
        return dataCall { decodeEnvelope(bodyOfSuccessful("GET", path, httpClient.get(path))) }
    }

    suspend inline fun <reified T> post(path: String, body: Any): ApiResponse<T> {
        Log.d(TAG, "POST $path")
        return dataCall { decodeEnvelope(bodyOfSuccessful("POST", path, httpClient.post(path) { setBody(body) })) }
    }

    suspend inline fun <reified T> put(path: String, body: Any): ApiResponse<T> {
        Log.d(TAG, "PUT $path")
        return dataCall { decodeEnvelope(bodyOfSuccessful("PUT", path, httpClient.put(path) { setBody(body) })) }
    }

    suspend inline fun <reified T> patch(path: String, body: Any): ApiResponse<T> {
        Log.d(TAG, "PATCH $path")
        return dataCall { decodeEnvelope(bodyOfSuccessful("PATCH", path, httpClient.patch(path) { setBody(body) })) }
    }

    suspend inline fun <reified T> delete(path: String): ApiResponse<T> {
        Log.d(TAG, "DELETE $path")
        return dataCall { decodeEnvelope(bodyOfSuccessful("DELETE", path, httpClient.delete(path))) }
    }

    /**
     * Uploads one file as `multipart/form-data` and decodes the envelope, under the same status
     * contract as the verbs above (#798).
     *
     * Media used to be posted through the raw [httpClient] in three copy-pasted blocks that decoded
     * the body whatever the status: a 413 from the proxy became a `SerializationException`, and an
     * error envelope became `Exception(serverMessage)`. This is the one upload path:
     *
     * - **Status-checked** through [bodyOfSuccessful], so a rejection is an [ApiException] carrying
     *   the backend code (or `HTTP_413` for a proxy's HTML page) and nothing else.
     * - **One `Content-Disposition`**, built with [ContentDisposition.withParameter] rather than
     *   string interpolation, so a filename with a quote is escaped and one with Turkish letters is
     *   sent as RFC 6266 `filename*`, instead of either breaking the part header.
     * - **Observable**: [onProgress] receives bytes sent and the total (known, because every part
     *   declares its length).
     * - **Cancellable** like any suspend call: cancelling the caller aborts the request.
     * - **Long-running** by policy: the upload request timeout replaces the ordinary one, while the
     *   socket timeout still catches a transfer that has actually stalled. Never retried — it is a
     *   POST.
     *
     * [fields] are extra plain form fields (the audio endpoint's `durationSeconds`).
     */
    suspend inline fun <reified T> upload(
        path: String,
        part: UploadPart,
        fields: Map<String, String> = emptyMap(),
        noinline onProgress: ((sent: Long, total: Long?) -> Unit)? = null,
    ): ApiResponse<T> {
        Log.d(TAG, "UPLOAD $path (${part.bytes.size} bytes)")
        return dataCall {
            val response = httpClient.submitFormWithBinaryData(path, multipartParts(part, fields)) {
                timeout {
                    requestTimeoutMillis = transportPolicy.uploadRequestTimeoutMs
                    socketTimeoutMillis = transportPolicy.uploadSocketTimeoutMs
                }
                if (onProgress != null) onUpload { sent, total -> onProgress(sent, total) }
            }
            decodeEnvelope(bodyOfSuccessful("UPLOAD", path, response))
        }
    }

    /**
     * The parts of an upload: the file, then each of [fields].
     *
     * Built by hand rather than with `formData { append(key, bytes, headers) }`, because that
     * builder writes its own `form-data; name=...` disposition and the caller's `filename` used to
     * arrive as a second, separately unescaped header value beside it.
     */
    @PublishedApi
    internal fun multipartParts(part: UploadPart, fields: Map<String, String>): List<PartData> {
        val bytes = part.bytes
        val file = PartData.BinaryItem(
            provider = { Buffer().apply { write(bytes) } },
            dispose = {},
            partHeaders = Headers.build {
                append(HttpHeaders.ContentDisposition, fileDisposition(part.fileName))
                append(HttpHeaders.ContentType, part.mimeType)
                append(HttpHeaders.ContentLength, bytes.size.toString())
            },
        )
        val formFields = fields.map { (name, value) ->
            PartData.FormItem(
                value = value,
                dispose = {},
                partHeaders = Headers.build {
                    append(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition("form-data").withParameter(ContentDisposition.Parameters.Name, name).toString(),
                    )
                },
            )
        }
        return listOf(file) + formFields
    }

    /**
     * `form-data; name=file; filename="..."`, plus an RFC 6266 `filename*` when the name is not
     * printable ASCII.
     *
     * [ContentDisposition.withParameter] quotes and backslash-escapes, which handles a quote in the
     * name, but it does not encode non-ASCII: even with `encodeValue = true` "çay.jpg" stays raw
     * UTF-8 inside a quoted string, which a header may not carry. So `filename` holds an ASCII
     * fallback and `filename*=UTF-8''...` the real name percent-encoded — RFC 6266 §4.3, and the
     * form Spring's `ContentDisposition.parse` prefers when both are present.
     */
    private fun fileDisposition(fileName: String): String {
        val isAscii = fileName.all { it.code in PRINTABLE_ASCII }
        val fallback = if (isAscii) fileName else fileName.map { if (it.code in PRINTABLE_ASCII) it else '_' }.joinToString("")
        val base = ContentDisposition("form-data")
            .withParameter(ContentDisposition.Parameters.Name, UPLOAD_FILE_FIELD)
            .withParameter(ContentDisposition.Parameters.FileName, fallback)
            .toString()
        return if (isAscii) base else "$base; filename*=UTF-8''${fileName.encodeURLParameter()}"
    }

    /**
     * Logs the outcome and returns the body — or throws [ApiException] if the status is not 2xx.
     *
     * `internal` rather than private because the five verbs above are `inline` with a `reified` type
     * parameter and so are compiled into their callers.
     *
     * [response] is the FINAL response: the `Auth` plugin installed above intercepts a 401, runs
     * `refreshTokens`, and replays the request before the call returns, so a 401 that refresh
     * recovers from never reaches here. Only a 401 the server stands by does.
     */
    @PublishedApi
    internal suspend fun bodyOfSuccessful(
        method: String,
        path: String,
        response: HttpResponse,
    ): String {
        Log.d(TAG, "$method $path -> ${response.status}")
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw ApiException.from(json, response.status, text)
        }
        return text
    }

    /**
     * Decodes a 2xx body into the envelope, treating an empty one as "no data".
     *
     * A 204, or a 200 that writes nothing, gives `decodeFromString` an empty string and it throws.
     * The several `delete<Unit>` callers would then see a successful delete as a failure.
     */
    @PublishedApi
    internal inline fun <reified T> decodeEnvelope(body: String): ApiResponse<T> =
        if (body.isBlank()) {
            ApiResponse(data = null, error = null, timestamp = "")
        } else {
            json.decodeFromString(ApiResponse.serializer(serializer<T>()), body)
        }
}

/** One file to [ApiClient.upload]. */
class UploadPart(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
)
