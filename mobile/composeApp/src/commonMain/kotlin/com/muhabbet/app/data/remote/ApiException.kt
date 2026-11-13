package com.muhabbet.app.data.remote

import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.exception.ErrorCodeSerializer
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A non-2xx response from the API.
 *
 * `ApiResponse` declares `data` and `error` both nullable, so the standard error envelope
 * deserialises **cleanly** with `data = null`. That made a rejection indistinguishable from an empty
 * answer: `response.data ?: emptyList()` turned a 500 into "no communities yet", and the many
 * callers that ignore the return value reported success after a 403. Non-2xx now produces this
 * exception instead of a well-formed lie.
 *
 * [code] is the shared [ErrorCode] the server put in the envelope (#883) — a typed value, so a
 * screen matches `ErrorCode.AUTH_OTP_INVALID` and a renamed code is a compile error instead of a
 * silently generic message. A code from a newer server than this build is [ErrorCode.UNKNOWN].
 * [code] is **null** when the body was not the envelope at all: a Traefik 502 HTML page never
 * reached the application, so there is no application code to report, only [status]. That used to
 * be a synthetic `"HTTP_502"` string sharing the server's namespace, which is exactly the kind of
 * client-invented code #883 removes.
 *
 * [message] carries the server's own text or, failing that, the start of the raw body. No
 * user-facing text is invented here: it is a diagnostic for logs, and screens map [code] rather than
 * showing [message] raw, because it arrives untranslated and can be an HTML fragment.
 */
class ApiException(
    val status: Int,
    val code: ErrorCode?,
    override val message: String,
) : Exception(message) {

    /**
     * Whether the server refused this client as too old (#873): 426 with
     * [ErrorCode.CLIENT_UPGRADE_REQUIRED]. Either half is enough — a proxy that rewrites the body
     * still passes the status through.
     */
    val isUpgradeRequired: Boolean
        get() = status == HttpStatusCode.UpgradeRequired.value || code == ErrorCode.CLIENT_UPGRADE_REQUIRED

    override fun toString(): String = "ApiException(status=$status, code=$code, message=$message)"

    companion object {
        /** Longest raw body kept in [message]. A gateway error page is unbounded. */
        private const val MAX_RAW_BODY = 200

        /**
         * Builds the exception for [status], reading `error.code`/`error.message` out of [body] when
         * [body] is the standard envelope.
         *
         * [json] is the client's own configured instance, so a body this parser accepts is exactly a
         * body the response decoder would have accepted.
         */
        fun from(json: Json, status: HttpStatusCode, body: String): ApiException {
            val error = errorObject(json, body)
            return ApiException(
                status = status.value,
                code = error?.stringField("code")?.let(ErrorCodeSerializer::fromWire),
                message = error?.stringField("message")
                    ?: body.trim().take(MAX_RAW_BODY).ifBlank { status.toString() },
            )
        }

        /**
         * The envelope's `error` object, or null when [body] is not the envelope at all.
         *
         * Not a swallowed failure: a body that does not parse is the documented second case — an
         * HTML error page from the proxy, or an empty body — and the caller answers it with a null
         * code and the raw text.
         */
        private fun errorObject(json: Json, body: String): JsonObject? =
            try {
                (json.parseToJsonElement(body) as? JsonObject)?.get("error") as? JsonObject
            } catch (_: SerializationException) {
                null
            }

        private fun JsonObject.stringField(name: String): String? =
            (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}
