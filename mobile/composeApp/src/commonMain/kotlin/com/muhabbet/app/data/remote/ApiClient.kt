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
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import com.muhabbet.app.util.Log
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
) {

    companion object {
        // Must match the Traefik Host() rule in the repo-root docker-compose.prod.yml (NOT
        // infra/docker-compose.prod.yml — that is the legacy nginx stack and carries no Traefik
        // labels). No certificate is issued for any other name, so a mismatch fails the TLS
        // handshake instead of 404-ing.
        const val BASE_URL = "https://muhabbet-api.rollingcatsoftware.com"
        @PublishedApi internal const val TAG = "ApiClient"

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
                sendWithoutRequest { request -> request.url.build().encodedPath !in PRE_LOGIN_PATHS }
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
        return decodeEnvelope(bodyOfSuccessful("GET", path, httpClient.get(path)))
    }

    suspend inline fun <reified T> post(path: String, body: Any): ApiResponse<T> {
        Log.d(TAG, "POST $path")
        return decodeEnvelope(bodyOfSuccessful("POST", path, httpClient.post(path) { setBody(body) }))
    }

    suspend inline fun <reified T> put(path: String, body: Any): ApiResponse<T> {
        Log.d(TAG, "PUT $path")
        return decodeEnvelope(bodyOfSuccessful("PUT", path, httpClient.put(path) { setBody(body) }))
    }

    suspend inline fun <reified T> patch(path: String, body: Any): ApiResponse<T> {
        Log.d(TAG, "PATCH $path")
        return decodeEnvelope(bodyOfSuccessful("PATCH", path, httpClient.patch(path) { setBody(body) }))
    }

    suspend inline fun <reified T> delete(path: String): ApiResponse<T> {
        Log.d(TAG, "DELETE $path")
        return decodeEnvelope(bodyOfSuccessful("DELETE", path, httpClient.delete(path)))
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
