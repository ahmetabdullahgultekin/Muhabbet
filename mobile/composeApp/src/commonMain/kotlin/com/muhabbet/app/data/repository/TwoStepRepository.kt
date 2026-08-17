package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.DisableTwoStepRequest
import com.muhabbet.shared.dto.SetupTwoStepRequest
import com.muhabbet.shared.dto.TwoStepStatusResponse

/**
 * The two-step verification endpoints, in one place.
 *
 * They were three string literals inside `TwoStepSetupScreen`, and two of the three did not match
 * what the backend serves (#544):
 *
 *  - setup was posted to `/api/v1/auth/two-step`, which was mapped for `DELETE` only, so Spring
 *    answered **405** and the screen showed "bir hata oluştu" — the reported bug;
 *  - disable was a `DELETE` with no body, where the controller requires the current PIN, so it would
 *    have answered **400** had anyone ever got far enough to try it. It is a `POST` now, for the
 *    reason given on the controller.
 *
 * A repository does not make a typo impossible, but it makes there be exactly one of it and gives a
 * test something to drive — `TwoStepRepositoryTest` asserts the method, path and body of all three
 * calls, which is the only kind of check that could have caught this without a device.
 */
class TwoStepRepository(
    private val apiClient: ApiClient
) {

    companion object {
        /** Must match `TWO_STEP_BASE_PATH` next to the backend's `TwoStepVerificationController`. */
        internal const val BASE_PATH = "/api/v1/auth/two-step"
        internal const val STATUS_PATH = "$BASE_PATH/status"
        internal const val SETUP_PATH = "$BASE_PATH/setup"
        internal const val DISABLE_PATH = "$BASE_PATH/disable"
    }

    /**
     * Whether two-step is on for the signed-in user.
     *
     * Deliberately not defaulted to `false` on failure: the screen renders the whole enable form
     * from this answer, and guessing "off" for an account that has it on would invite the user to
     * set a second PIN and then report the `AUTH_2FA_ALREADY_ENABLED` that follows as a bug.
     */
    suspend fun status(): TwoStepStatusResponse =
        apiClient.get<TwoStepStatusResponse>(STATUS_PATH).data
            ?: TwoStepStatusResponse(enabled = false, hasEmail = false)

    /** Turns two-step on. [recoveryEmail] is optional and is what a later reset would be sent to. */
    suspend fun enable(pin: String, recoveryEmail: String?) {
        apiClient.post<Unit>(SETUP_PATH, SetupTwoStepRequest(pin = pin, email = recoveryEmail))
    }

    /**
     * Turns two-step off. The server re-checks [currentPin] against the stored hash, so this is a
     * request the caller can be refused — an `ApiException` carrying `AUTH_2FA_PIN_INVALID`.
     */
    suspend fun disable(currentPin: String) {
        apiClient.post<Unit>(DISABLE_PATH, DisableTwoStepRequest(currentPin = currentPin))
    }
}
