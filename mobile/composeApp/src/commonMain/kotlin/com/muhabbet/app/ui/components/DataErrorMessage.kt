package com.muhabbet.app.ui.components

import com.muhabbet.app.data.remote.DataError
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.data_error_forbidden
import com.muhabbet.composeapp.generated.resources.data_error_not_found
import com.muhabbet.composeapp.generated.resources.data_error_rate_limited
import com.muhabbet.composeapp.generated.resources.data_error_rejected
import com.muhabbet.composeapp.generated.resources.data_error_server
import com.muhabbet.composeapp.generated.resources.data_error_timeout
import com.muhabbet.composeapp.generated.resources.data_error_too_large
import com.muhabbet.composeapp.generated.resources.data_error_unauthorized
import com.muhabbet.composeapp.generated.resources.error_generic
import com.muhabbet.composeapp.generated.resources.error_network
import org.jetbrains.compose.resources.StringResource

/**
 * The localized sentence for a data-layer failure (#824).
 *
 * The one place a [DataError] becomes words. The data layer produces no user-visible text, so a
 * screen that used to show `e.message` — the server's untranslated prose, or an English fallback —
 * resolves this instead. A screen that knows a specific backend code better (an OTP attempt, a
 * taken community name) still matches that code first and falls back to this.
 *
 * Exhaustive over the sealed hierarchy, so adding a kind of failure without a message does not
 * compile.
 */
fun DataError.messageRes(): StringResource = when (this) {
    DataError.Network -> Res.string.error_network
    DataError.Timeout -> Res.string.data_error_timeout
    is DataError.Unauthorized -> Res.string.data_error_unauthorized
    is DataError.Forbidden -> Res.string.data_error_forbidden
    is DataError.NotFound -> Res.string.data_error_not_found
    is DataError.PayloadTooLarge -> Res.string.data_error_too_large
    is DataError.RateLimited -> Res.string.data_error_rate_limited
    is DataError.Rejected -> Res.string.data_error_rejected
    is DataError.Server -> Res.string.data_error_server
    DataError.Serialization, DataError.EmptyResponse, DataError.Unknown -> Res.string.error_generic
}
