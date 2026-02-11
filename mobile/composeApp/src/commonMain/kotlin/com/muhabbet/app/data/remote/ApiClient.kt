package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.shared.dto.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class ApiClient(private val tokenStorage: TokenStorage) {

    companion object {
        const val BASE_URL = "https://muhabbet.rollingcatsoftware.com"
    }

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }

        install(Logging) {
            level = LogLevel.HEADERS
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
                            println("MUHABBET: Refresh token rejected (${response.status})")
                            tokenStorage.clear()
                            return@refreshTokens null
                        }
                        if (!response.status.isSuccess()) {
                            println("MUHABBET: Token refresh server error (${response.status})")
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
                        println("MUHABBET: Token refresh failed (network): ${e.message}")
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

    suspend inline fun <reified T> get(path: String): ApiResponse<T> {
        println("MUHABBET: GET $path")
        val response = httpClient.get(path)
        println("MUHABBET: GET $path -> status=${response.status}")
        val text = response.bodyAsText()
        return json.decodeFromString(ApiResponse.serializer(serializer<T>()), text)
    }

    suspend inline fun <reified T> post(path: String, body: Any): ApiResponse<T> {
        println("MUHABBET: POST $path body=$body")
        val response = httpClient.post(path) { setBody(body) }
        println("MUHABBET: POST $path -> status=${response.status}")
        val text = response.bodyAsText()
        return json.decodeFromString(ApiResponse.serializer(serializer<T>()), text)
    }

    suspend inline fun <reified T> put(path: String, body: Any): ApiResponse<T> {
        println("MUHABBET: PUT $path")
        val response = httpClient.put(path) { setBody(body) }
        println("MUHABBET: PUT $path -> status=${response.status}")
        val text = response.bodyAsText()
        return json.decodeFromString(ApiResponse.serializer(serializer<T>()), text)
    }

    suspend inline fun <reified T> patch(path: String, body: Any): ApiResponse<T> {
        println("MUHABBET: PATCH $path body=$body")
        val response = httpClient.patch(path) { setBody(body) }
        println("MUHABBET: PATCH $path -> status=${response.status}")
        val text = response.bodyAsText()
        return json.decodeFromString(ApiResponse.serializer(serializer<T>()), text)
    }
}
