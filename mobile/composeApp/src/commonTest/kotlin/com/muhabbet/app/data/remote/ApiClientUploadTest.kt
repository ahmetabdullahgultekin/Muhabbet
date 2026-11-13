package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.repository.GroupRepository
import com.muhabbet.app.data.repository.MediaRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Media uploads and message edits through `ApiClient`'s checked path (#798).
 *
 * They used to post on the raw `httpClient` and decode whatever came back: a 413 from the proxy
 * became a `SerializationException`, an error envelope became `Exception(serverMessage)`, the
 * filename was interpolated unescaped into the part header, and there was no progress at all.
 */
class ApiClientUploadTest {

    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val uploaded =
        """{"data":{"mediaId":"m1","url":"https://cdn/x.jpg","thumbnailUrl":null,"contentType":"image/jpeg","sizeBytes":3},"timestamp":""}"""

    private fun client(engine: MockEngine) = ApiClient(
        FakeTokenStorage(),
        engine,
        HttpTransportPolicy(retryJitterMs = 0, sleep = {}),
    )

    @Test
    fun payloadTooLarge_withAnHtmlBody_isATyped413_notASerializationCrash() = runTest {
        val repo = MediaRepository(client(MockEngine {
            respond("<html><body>413 Request Entity Too Large</body></html>", HttpStatusCode.PayloadTooLarge)
        }))

        val failure = assertFailsWith<ApiException> {
            repo.uploadImage(byteArrayOf(1, 2, 3), "image/jpeg", "a.jpg")
        }

        assertEquals(413, failure.status)
        assertEquals("HTTP_413", failure.code)
        assertIs<DataError.PayloadTooLarge>(failure.error)
    }

    @Test
    fun anErrorEnvelope_becomesItsBackendCode() = runTest {
        val repo = MediaRepository(client(MockEngine {
            respond(
                """{"error":{"code":"MEDIA_UNSUPPORTED_TYPE","message":"Desteklenmeyen dosya türü"},"timestamp":""}""",
                HttpStatusCode.BadRequest,
                json,
            )
        }))

        val failure = assertFailsWith<ApiException> {
            repo.uploadDocument(byteArrayOf(1), "application/x-msdownload", "a.exe")
        }

        assertEquals("MEDIA_UNSUPPORTED_TYPE", failure.code)
        assertEquals(DataError.Rejected(400, "MEDIA_UNSUPPORTED_TYPE"), failure.error)
    }

    @Test
    fun anUpload_isNeverRetried() = runTest {
        var attempts = 0
        val repo = MediaRepository(client(MockEngine {
            attempts++
            respond("", HttpStatusCode.ServiceUnavailable)
        }))

        assertFailsWith<ApiException> { repo.uploadImage(byteArrayOf(1), "image/jpeg", "a.jpg") }

        assertEquals(1, attempts)
    }

    @Test
    fun progress_reachesTheWholeBody() = runTest {
        val repo = MediaRepository(client(MockEngine { request ->
            request.body.toByteArray() // consume the body, as a real engine would
            respond(uploaded, HttpStatusCode.OK, json)
        }))
        val progress = mutableListOf<Pair<Long, Long?>>()

        val result = repo.uploadImage(ByteArray(64 * 1024) { 7 }, "image/jpeg", "a.jpg") { sent, total ->
            progress += sent to total
        }

        assertEquals("m1", result.mediaId)
        val (sent, total) = progress.last()
        assertNotNull(total, "every part declares its length, so the total is known")
        assertEquals(total, sent, "progress must reach 100%")
        assertTrue(total > 64 * 1024, "the total is the whole multipart body, file included")
    }

    @Test
    fun cancellingTheCaller_abortsTheUpload() = runTest {
        val reachedServer = CompletableDeferred<Unit>()
        val serverSawCancellation = CompletableDeferred<Boolean>()
        val repo = MediaRepository(client(MockEngine {
            reachedServer.complete(Unit)
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                serverSawCancellation.complete(true)
                throw e
            }
        }))

        val upload = async(Dispatchers.Default, start = CoroutineStart.DEFAULT) {
            repo.uploadImage(byteArrayOf(1, 2, 3), "image/jpeg", "a.jpg")
        }
        withContext(Dispatchers.Default) { reachedServer.await() }
        upload.cancel()

        assertTrue(withContext(Dispatchers.Default) { serverSawCancellation.await() })
        assertTrue(upload.isCancelled)
    }

    @Test
    fun aFilenameWithAQuote_isEscapedInsideOneDisposition() {
        val parts = client(MockEngine { respond("") })
            .multipartParts(UploadPart(byteArrayOf(1), "image/jpeg", "say \"cheese\".jpg"), emptyMap())

        val dispositions = parts.first().headers.getAll(HttpHeaders.ContentDisposition).orEmpty()
        assertEquals(1, dispositions.size, "one disposition, not the builder's plus ours")
        val parsed = ContentDisposition.parse(dispositions.single())
        assertEquals("form-data", parsed.disposition)
        assertEquals("file", parsed.parameter(ContentDisposition.Parameters.Name))
        assertEquals("say \"cheese\".jpg", parsed.parameter(ContentDisposition.Parameters.FileName))
    }

    @Test
    fun aTurkishFilename_isSentAsAnRfc6266ExtendedValue() {
        val parts = client(MockEngine { respond("") })
            .multipartParts(UploadPart(byteArrayOf(1), "image/jpeg", "çay bardağı.jpg"), emptyMap())

        val disposition = parts.first().headers[HttpHeaders.ContentDisposition].orEmpty()

        assertTrue(
            disposition.contains("filename*=utf-8''%C3%A7ay%20barda%C4%9F%C4%B1.jpg", ignoreCase = true),
            "non-ASCII must be percent-encoded UTF-8, not raw bytes in a quoted string: $disposition",
        )
    }

    @Test
    fun audio_sendsItsDurationAsAFormField() = runTest {
        var body = ""
        val repo = MediaRepository(client(MockEngine { request ->
            body = request.body.toByteArray().decodeToString()
            respond(uploaded, HttpStatusCode.OK, json)
        }))

        repo.uploadAudio(byteArrayOf(1), "audio/ogg", "v.ogg", durationSeconds = 12)

        assertTrue(Regex("""name="?durationSeconds"?\r\n(?:[^\r\n]+\r\n)*\r\n12\r\n""").containsMatchIn(body), body)
        assertTrue(body.contains("audio/ogg"))
    }

    @Test
    fun editMessage_rejection_carriesTheServerCode() = runTest {
        val repo = GroupRepository(client(MockEngine {
            respond(
                """{"error":{"code":"MSG_EDIT_WINDOW_EXPIRED","message":"x"},"timestamp":""}""",
                HttpStatusCode.BadRequest,
                json,
            )
        }))

        val failure = assertFailsWith<ApiException> { repo.editMessage("m1", "hello") }

        assertEquals("MSG_EDIT_WINDOW_EXPIRED", failure.code)
    }

    @Test
    fun editMessage_acceptsTheServersMessageBody() = runTest {
        val repo = GroupRepository(client(MockEngine {
            respond("""{"data":{"id":"m1","content":"hello","anything":[1,2]},"timestamp":""}""", HttpStatusCode.OK, json)
        }))

        repo.editMessage("m1", "hello")
    }
}
