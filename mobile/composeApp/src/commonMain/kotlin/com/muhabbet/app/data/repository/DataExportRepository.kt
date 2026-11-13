package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.api.ApiRoutes
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The KVKK m.11 data export, fetched to the end and assembled into one document (#780).
 *
 * The old client called a path nothing served, and even had it worked it decoded the response into
 * `Unit` — the user pressed "Export" and nothing was ever saved. The server's export is
 * cursor-paginated on its two unbounded categories (`messages` and `mediaFiles`), so a single call
 * is one page, not the export. This walks both cursors until each reports `hasMore = false` and
 * merges the pages into a single JSON document with every item, which the screen then hands to the
 * platform's "save as" (see `rememberDocumentSaver`).
 *
 * The page is read as a [JsonObject], not a typed DTO, on purpose: the export is the user's data in
 * the server's shape and is written back out verbatim. A typed copy on the client would silently
 * drop any category the server adds before the app learns about it — the "quietly incomplete"
 * failure the server-side export (#341) was built to avoid.
 */
class DataExportRepository(private val apiClient: ApiClient) {

    /**
     * Fetches every page and returns the merged document. [onPage] is told each page number as its
     * request starts, so the screen can show progress on a large account.
     *
     * Fails (throws) rather than returning a partial document: an export that stopped at page 7
     * without saying so would be a false statement to a data subject about what we hold.
     */
    suspend fun exportAll(onPage: (Int) -> Unit = {}): JsonObject {
        val walker = ExportPageWalker()
        var page = 0
        while (!walker.isComplete) {
            page++
            if (page > MAX_PAGES) throw IllegalStateException("DATA_EXPORT_TOO_MANY_PAGES")
            onPage(page)
            walker.accept(fetchPage(walker.messagesCursor, walker.mediaCursor))
        }
        return walker.document()
    }

    /** Pretty-printed, because this file is meant to be opened and read by a person. */
    fun encode(document: JsonObject): String = PrettyJson.encodeToString(JsonObject.serializer(), document)

    private suspend fun fetchPage(messagesCursor: String?, mediaCursor: String?): JsonObject {
        val query = buildList {
            add("${ApiRoutes.UserData.PARAM_PAGE_SIZE}=$PAGE_SIZE")
            messagesCursor?.let { add("${ApiRoutes.UserData.PARAM_MESSAGES_CURSOR}=${it.encodeURLParameter()}") }
            mediaCursor?.let { add("${ApiRoutes.UserData.PARAM_MEDIA_CURSOR}=${it.encodeURLParameter()}") }
        }.joinToString("&")
        val response = apiClient.get<JsonObject>("${ApiRoutes.UserData.DATA_EXPORT}?$query")
        return response.data ?: throw IllegalStateException(response.error?.code ?: "DATA_EXPORT_FAILED")
    }

    companion object {
        /** The server's `MAX_EXPORT_PAGE_SIZE`: the fewest round trips it will allow. */
        const val PAGE_SIZE = 500

        /** 5M messages at 500 a page. A loop guard against a server that never says "done". */
        const val MAX_PAGES = 10_000

        private val PrettyJson = Json { prettyPrint = true }
    }
}

/**
 * The cursor bookkeeping for [DataExportRepository.exportAll], kept apart so it is testable without
 * HTTP.
 *
 * Each category is followed independently. Once one is exhausted its cursor stays at its last value
 * and the server keeps returning that category's final page alongside the other's progress; those
 * repeats are ignored here, so nothing is duplicated in the document. A page that says `hasMore`
 * but hands back no cursor, or the same cursor again, is treated as the end rather than looped on.
 */
internal class ExportPageWalker {
    private var first: JsonObject? = null
    private val messages = mutableListOf<JsonElement>()
    private val media = mutableListOf<JsonElement>()
    private var messagesDone = false
    private var mediaDone = false

    var messagesCursor: String? = null
        private set
    var mediaCursor: String? = null
        private set

    val isComplete: Boolean get() = first != null && messagesDone && mediaDone

    fun accept(page: JsonObject) {
        if (first == null) first = page
        if (!messagesDone) {
            val (next, done) = follow(page, MESSAGES, messages, messagesCursor)
            messagesCursor = next
            messagesDone = done
        }
        if (!mediaDone) {
            val (next, done) = follow(page, MEDIA, media, mediaCursor)
            mediaCursor = next
            mediaDone = done
        }
    }

    /** The first page with both categories replaced by every item collected, and marked complete. */
    fun document(): JsonObject {
        val base = first ?: throw IllegalStateException("DATA_EXPORT_EMPTY")
        return JsonObject(
            base + mapOf(
                MESSAGES to completedPage(base, MESSAGES, messages),
                MEDIA to completedPage(base, MEDIA, media)
            )
        )
    }

    private fun follow(
        page: JsonObject,
        key: String,
        into: MutableList<JsonElement>,
        previousCursor: String?
    ): Pair<String?, Boolean> {
        val category = page[key] as? JsonObject ?: return previousCursor to true
        val hasMore = category["hasMore"]?.jsonPrimitive?.booleanOrNull ?: false
        val next = category["nextCursor"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        // The same cursor back means the same page back: its items are already collected.
        val repeated = hasMore && next != null && next == previousCursor
        if (!repeated) (category["items"] as? JsonArray)?.let { into.addAll(it) }
        val done = !hasMore || next == null || repeated
        return (if (done) previousCursor else next) to done
    }

    private fun completedPage(base: JsonObject, key: String, items: List<JsonElement>): JsonObject =
        buildJsonObject {
            put("items", JsonArray(items))
            put("hasMore", JsonPrimitive(false))
            put("nextCursor", JsonPrimitive(null as String?))
            (base[key] as? JsonObject)?.get("totalCount")?.let { put("totalCount", it) }
        }

    private companion object {
        const val MESSAGES = "messages"
        const val MEDIA = "mediaFiles"
    }
}
