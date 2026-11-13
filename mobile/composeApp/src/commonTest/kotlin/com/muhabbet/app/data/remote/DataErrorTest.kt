package com.muhabbet.app.data.remote

import com.muhabbet.shared.dto.ApiResponse
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The typed error model the data layer throws instead of `Exception("...")` (#824). */
class DataErrorTest {

    @Test
    fun eachHttpStatus_mapsToItsKind_keepingTheBackendCode() {
        assertEquals(DataError.Unauthorized("AUTH_OTP_INVALID"), DataError.fromHttp(401, "AUTH_OTP_INVALID"))
        assertEquals(DataError.Forbidden("GROUP_NOT_MEMBER"), DataError.fromHttp(403, "GROUP_NOT_MEMBER"))
        assertEquals(DataError.NotFound("CONV_NOT_FOUND"), DataError.fromHttp(404, "CONV_NOT_FOUND"))
        assertEquals(DataError.PayloadTooLarge("HTTP_413"), DataError.fromHttp(413, "HTTP_413"))
        assertEquals(DataError.RateLimited("AUTH_OTP_COOLDOWN"), DataError.fromHttp(429, "AUTH_OTP_COOLDOWN"))
        assertEquals(DataError.Rejected(409, "CONV_ALREADY_EXISTS"), DataError.fromHttp(409, "CONV_ALREADY_EXISTS"))
        assertEquals(DataError.Server(502, "HTTP_502"), DataError.fromHttp(502, "HTTP_502"))
    }

    @Test
    fun apiException_isADataException_withItsStatusClassified() {
        val e: DataException = ApiException(403, "GROUP_PERMISSION_DENIED", "x")
        assertEquals(DataError.Forbidden("GROUP_PERMISSION_DENIED"), e.error)
    }

    @Test
    fun transportFailures_areClassified_timeoutBeforeNetwork() {
        // HttpRequestTimeoutException IS an IOException; matching IOException first would call
        // every timeout "no connection".
        assertEquals(DataError.Timeout, HttpRequestTimeoutException("/x", 1L).toDataError())
        assertEquals(DataError.Network, IOException("reset").toDataError())
        assertEquals(DataError.Serialization, SerializationException("bad").toDataError())
        assertEquals(DataError.Unknown, IllegalStateException("?").toDataError())
    }

    @Test
    fun connectivity_isOnlyNetworkAndTimeout() {
        assertTrue(DataError.Network.isConnectivity)
        assertTrue(DataError.Timeout.isConnectivity)
        assertFalse(DataError.Forbidden("X").isConnectivity)
        assertFalse(DataError.Server(500, "X").isConnectivity)
    }

    @Test
    fun dataCall_rethrowsCancellationUntouched() {
        val cancellation = CancellationException("screen left")
        val thrown = assertFailsWith<CancellationException> { dataCall { throw cancellation } }
        assertSame(cancellation, thrown)
    }

    @Test
    fun dataCall_wrapsAnythingElse_keepingTheCause() {
        val cause = IOException("reset")
        val thrown = assertFailsWith<DataException> { dataCall { throw cause } }
        assertEquals(DataError.Network, thrown.error)
        assertSame(cause, thrown.cause)
    }

    @Test
    fun dataCall_passesADataExceptionThroughAsIs() {
        val original = ApiException(404, "CONV_NOT_FOUND", "x")
        assertSame(original, assertFailsWith<ApiException> { dataCall { throw original } })
    }

    @Test
    fun requireData_onAnEmptySuccess_isEmptyResponse_notAnEnglishSentence() {
        val thrown = assertFailsWith<DataException> {
            ApiResponse<String>(data = null, error = null, timestamp = "").requireData()
        }
        assertEquals(DataError.EmptyResponse, thrown.error)
    }
}
