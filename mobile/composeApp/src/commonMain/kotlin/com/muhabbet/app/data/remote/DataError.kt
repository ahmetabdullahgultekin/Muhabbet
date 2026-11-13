package com.muhabbet.app.data.remote

import com.muhabbet.shared.dto.ApiResponse
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Why a data-layer call failed, as a type rather than a sentence (#824).
 *
 * Repositories used to signal failure with `throw Exception("Upload failed")` — inline English, or
 * the server's own untranslated prose — and every screen caught `Exception`. A failure could not be
 * told from another, so the UI could not pick the right localized message, and tests could not
 * assert what kind of failure they provoked.
 *
 * Every failure that leaves the data layer is now a [DataException] carrying one of these. The
 * HTTP variants keep the backend `ErrorCode` name the server put in the error envelope ([code]),
 * because that — not the status — is what distinguishes, say, `AUTH_OTP_INVALID` from
 * `AUTH_UNAUTHORIZED` when both are 401; and they keep a synthetic `HTTP_<status>` when the body
 * never came from the application (a proxy's HTML page).
 *
 * The UI turns one of these into words with `com.muhabbet.app.ui.components.messageRes`, and only
 * there: nothing in `data/` produces user-visible text.
 */
sealed interface DataError {

    /** The server was never reached: no route, DNS failure, connection reset. */
    data object Network : DataError

    /** The server was reached, or might have been, but did not answer in time. */
    data object Timeout : DataError

    /** The server answered with a non-2xx status. */
    sealed interface Http : DataError {
        val status: Int

        /** The backend `ErrorCode` name, or `HTTP_<status>` when the body was not the envelope. */
        val code: String
    }

    data class Unauthorized(override val code: String) : Http {
        override val status: Int get() = 401
    }

    data class Forbidden(override val code: String) : Http {
        override val status: Int get() = 403
    }

    data class NotFound(override val code: String) : Http {
        override val status: Int get() = 404
    }

    /** 413 — an upload over the server's or the proxy's limit. Worth its own message. */
    data class PayloadTooLarge(override val code: String) : Http {
        override val status: Int get() = 413
    }

    data class RateLimited(override val code: String) : Http {
        override val status: Int get() = 429
    }

    /** Any other 4xx: the server understood the request and refused it for [code]. */
    data class Rejected(override val status: Int, override val code: String) : Http

    /** 5xx: the server, or something in front of it, failed. */
    data class Server(override val status: Int, override val code: String) : Http

    /** A 2xx whose body was not what the contract says. */
    data object Serialization : DataError

    /** A 2xx with no `data` where the endpoint promises one. A contract violation, not a refusal. */
    data object EmptyResponse : DataError

    data object Unknown : DataError

    companion object {
        fun fromHttp(status: Int, code: String): Http = when (status) {
            401 -> Unauthorized(code)
            403 -> Forbidden(code)
            404 -> NotFound(code)
            413 -> PayloadTooLarge(code)
            429 -> RateLimited(code)
            in 500..599 -> Server(status, code)
            else -> Rejected(status, code)
        }
    }
}

/** True for the failures an offline cache may answer: the server said nothing, not "no". */
val DataError.isConnectivity: Boolean
    get() = this == DataError.Network || this == DataError.Timeout

/**
 * The one exception type the data layer throws.
 *
 * Open so that [ApiException] — which screens already match on for its `code` — can be one, and a
 * caller that only cares *that* a call failed and *how* catches a single type.
 */
open class DataException(
    val error: DataError,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: error.toString(), cause)

/**
 * Classifies a throwable from the transport into a [DataError].
 *
 * Order matters: the three Ktor timeouts are themselves [IOException]s, so they are matched first or
 * every timeout would read as "no connection".
 */
fun Throwable.toDataError(): DataError = when (this) {
    is DataException -> error
    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> DataError.Timeout
    is IOException -> DataError.Network
    is SerializationException -> DataError.Serialization
    else -> DataError.Unknown
}

/**
 * Runs [block] and rethrows any failure as a [DataException] — except cancellation, which always
 * propagates untouched.
 *
 * `catch (e: Exception)` swallows `CancellationException` too, so a cancelled screen's request was
 * logged as a failure and, inside a loop, kept running. This is the shape every data-layer catch
 * should take instead.
 */
inline fun <T> dataCall(block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: DataException) {
        throw e
    } catch (e: Exception) {
        throw DataException(e.toDataError(), e.message, e)
    }

/**
 * The `data` of a response the server accepted, or [DataError.EmptyResponse].
 *
 * Replaces `response.data ?: throw Exception(response.error?.message ?: "SOMETHING_FAILED")`. By the
 * time a caller holds an [ApiResponse], [ApiClient] has already thrown for every non-2xx, so
 * `response.error` there is always null and the fallback string was the only thing ever thrown.
 */
fun <T> ApiResponse<T>.requireData(): T = data ?: throw DataException(DataError.EmptyResponse)
