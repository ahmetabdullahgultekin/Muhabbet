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
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import com.muhabbet.app.util.Log
import com.muhabbet.app.session.RefreshOutcomeListener
import kotlinx.coroutines.CancellationException
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
 * @param refreshOutcomes where a token refresh's result is reported — the `SessionManager` in the
 * app. What a rejected refresh *means* is the session's decision, not this client's (#813).
 */
class ApiClient(
    private val tokenStorage: TokenStorage,
    engine: HttpClientEngine? = null,
    private val refreshOutcomes: RefreshOutcomeListener = RefreshOutcomeListener.None,
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
                // Read the token store on every request instead of letting Ktor cache the first
                // answer (#813). The cache lives in this client, which is a process-wide singleton,
                // and nothing ever invalidated it: after logging out and signing in as someone else,
                // requests went on carrying the previous account's access token until a 401 forced a
                // refresh. With the store as the only copy, a sign-in, a logout and a refresh are
                // all seen by the very next request. A preferences read per request is free next to
                // the request itself.
                cacheTokens = false
                loadTokens {
                    val access = tokenStorage.getAccessToken()
                    val refresh = tokenStorage.getRefreshToken()
                    if (access != null && refresh != null) {
                        BearerTokens(access, refresh)
                    } else null
                }
                refreshTokens {
                    val refresh = tokenStorage.getRefreshToken() ?: return@refreshTokens null
                    refreshOutcomes.onRefreshStarted()
                    when (val outcome = requestRefresh(client, refresh)) {
                        is RefreshOutcome.Renewed -> {
                            tokenStorage.saveTokens(
                                accessToken = outcome.accessToken,
                                refreshToken = outcome.refreshToken,
                                userId = tokenStorage.getUserId() ?: "",
                                deviceId = tokenStorage.getDeviceId() ?: ""
                            )
                            refreshOutcomes.onRefreshSucceeded()
                            BearerTokens(outcome.accessToken, outcome.refreshToken)
                        }
                        // The session is over. Reported, not acted on: this used to call
                        // tokenStorage.clear() itself and tell nobody, which left the signed-in UI
                        // on screen with every request failing and the socket polling an empty
                        // token store every two seconds for as long as the app stayed open (#813).
                        RefreshOutcome.Rejected -> {
                            refreshOutcomes.onRefreshRejected()
                            null
                        }
                        // No network, or the server failed: nothing is known about the session, so
                        // it stays as it is and the next request tries again.
                        RefreshOutcome.Failed -> {
                            refreshOutcomes.onRefreshFailedTransiently()
                            null
                        }
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
                response.status == HttpStatusCode.Unauthorized &&
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

    /**
     * Asks the server to renew the session and says what it answered — and nothing else. Storing the
     * result and deciding what a rejection means both happen in the caller.
     */
    private suspend fun requestRefresh(client: HttpClient, refreshToken: String): RefreshOutcome {
        val response = try {
            client.post("$BASE_URL/api/v1/auth/token/refresh") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("refreshToken" to refreshToken))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed (network): ${e.message}")
            return RefreshOutcome.Failed
        }
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
            Log.w(TAG, "Refresh token rejected (${response.status})")
            return RefreshOutcome.Rejected
        }
        if (!response.status.isSuccess()) {
            Log.w(TAG, "Token refresh server error (${response.status})")
            return RefreshOutcome.Failed
        }
        val data = try {
            json.decodeFromString(
                ApiResponse.serializer(serializer<Map<String, String>>()),
                response.bodyAsText()
            ).data
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh returned an unreadable body: ${e.message}")
            return RefreshOutcome.Failed
        }
        // An accepted call that issues no session is a refusal in all but status code — the
        // previous code cleared the store here too.
        data ?: return RefreshOutcome.Rejected
        val access = data["accessToken"] ?: return RefreshOutcome.Failed
        val refresh = data["refreshToken"] ?: return RefreshOutcome.Failed
        return RefreshOutcome.Renewed(access, refresh)
    }

    private sealed interface RefreshOutcome {
        data class Renewed(val accessToken: String, val refreshToken: String) : RefreshOutcome
        data object Rejected : RefreshOutcome
        data object Failed : RefreshOutcome
    }

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
