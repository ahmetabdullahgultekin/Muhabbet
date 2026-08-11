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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import com.muhabbet.app.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
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
                sendWithoutRequest { request ->
                    request.url.pathSegments.let { path ->
                        !path.containsAll(listOf("auth"))
                    }
                }
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
        val response = httpClient.get(path)
        Log.d(TAG, "GET $path -> ${response.status}")
        val text = response.bodyAsText()
        return json.decodeFromString(ApiResponse.serializer(serializer<T>()), text)
    }

    suspend inline fun <reified T> post(path: String, body: Any): ApiResponse<T> {
        Log.d(TAG, "POST $path")
        val response = httpClient.post(path) { setBody(body) }
        Log.d(TAG, "POST $path -> ${response.status}")
        val text = response.bodyAsText()
        return json.decodeFromString(ApiResponse.serializer(serializer<T>()), text)
    }

    suspend inline fun <reified T> put(path: String, body: Any): ApiResponse<T> {
        Log.d(TAG, "PUT $path")
        val response = httpClient.put(path) { setBody(body) }
        Log.d(TAG, "PUT $path -> ${response.status}")
        val text = response.bodyAsText()
        return json.decodeFromString(ApiResponse.serializer(serializer<T>()), text)
    }

    suspend inline fun <reified T> patch(path: String, body: Any): ApiResponse<T> {
        Log.d(TAG, "PATCH $path")
        val response = httpClient.patch(path) { setBody(body) }
        Log.d(TAG, "PATCH $path -> ${response.status}")
        val text = response.bodyAsText()
        return json.decodeFromString(ApiResponse.serializer(serializer<T>()), text)
    }

    suspend inline fun <reified T> delete(path: String): ApiResponse<T> {
        Log.d(TAG, "DELETE $path")
        val response = httpClient.delete(path)
        Log.d(TAG, "DELETE $path -> ${response.status}")
        val text = response.bodyAsText()
        return json.decodeFromString(ApiResponse.serializer(serializer<T>()), text)
    }
}
