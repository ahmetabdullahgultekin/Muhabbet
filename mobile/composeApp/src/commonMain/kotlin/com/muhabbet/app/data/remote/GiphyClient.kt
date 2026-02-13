package com.muhabbet.app.data.remote

import com.muhabbet.app.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GiphyResponse(val data: List<GiphyGif> = emptyList())

@Serializable
data class GiphyGif(
    val id: String,
    val title: String = "",
    val images: GiphyImages
)

@Serializable
data class GiphyImages(
    val fixed_height: GiphyImageVariant? = null,
    val fixed_width: GiphyImageVariant? = null,
    val original: GiphyImageVariant? = null,
    val preview_gif: GiphyImageVariant? = null
)

@Serializable
data class GiphyImageVariant(
    val url: String = "",
    val width: String = "0",
    val height: String = "0"
)

class GiphyClient(private val apiKey: String = GIPHY_PUBLIC_BETA_KEY) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun searchGifs(query: String, limit: Int = 25, offset: Int = 0): List<GiphyGif> {
        return try {
            val response = client.get("https://api.giphy.com/v1/gifs/search") {
                parameter("api_key", apiKey)
                parameter("q", query)
                parameter("limit", limit)
                parameter("offset", offset)
                parameter("rating", "g")
                parameter("lang", "tr")
            }
            val body = response.bodyAsText()
            json.decodeFromString<GiphyResponse>(body).data
        } catch (e: Exception) {
            Log.e(TAG, "GIF search failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getTrending(limit: Int = 25): List<GiphyGif> {
        return try {
            val response = client.get("https://api.giphy.com/v1/gifs/trending") {
                parameter("api_key", apiKey)
                parameter("limit", limit)
                parameter("rating", "g")
            }
            val body = response.bodyAsText()
            json.decodeFromString<GiphyResponse>(body).data
        } catch (e: Exception) {
            Log.e(TAG, "GIF trending failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun searchStickers(query: String, limit: Int = 25): List<GiphyGif> {
        return try {
            val response = client.get("https://api.giphy.com/v1/stickers/search") {
                parameter("api_key", apiKey)
                parameter("q", query)
                parameter("limit", limit)
                parameter("rating", "g")
                parameter("lang", "tr")
            }
            val body = response.bodyAsText()
            json.decodeFromString<GiphyResponse>(body).data
        } catch (e: Exception) {
            Log.e(TAG, "Sticker search failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getTrendingStickers(limit: Int = 25): List<GiphyGif> {
        return try {
            val response = client.get("https://api.giphy.com/v1/stickers/trending") {
                parameter("api_key", apiKey)
                parameter("limit", limit)
                parameter("rating", "g")
            }
            val body = response.bodyAsText()
            json.decodeFromString<GiphyResponse>(body).data
        } catch (e: Exception) {
            Log.e(TAG, "Sticker trending failed: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "GiphyClient"
        // GIPHY public beta key — for production, use your own key via env/config
        const val GIPHY_PUBLIC_BETA_KEY = "dc6zaTOxFJmzC"
    }
}
