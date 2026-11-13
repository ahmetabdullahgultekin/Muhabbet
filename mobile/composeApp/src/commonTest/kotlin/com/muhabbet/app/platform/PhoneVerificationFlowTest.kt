package com.muhabbet.app.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * Pins the exhaustive outcomes of phone verification (#860). Each of these paths used to hang the
 * login screen, return an empty token, or discard the resend token.
 */
class PhoneVerificationFlowTest {

    /** A scripted SDK: each request answers with [answer], or never if it returns null. */
    private class FakeDriver(
        var answer: () -> PhoneVerificationEvent<String, String>? = { null },
        var signInResult: () -> String? = { "id-token" },
    ) : PhoneVerificationDriver<String, String> {
        val resendTokensSeen = mutableListOf<String?>()

        override fun requestCode(
            phoneNumber: String,
            resendToken: String?,
            onEvent: (PhoneVerificationEvent<String, String>) -> Unit
        ) {
            resendTokensSeen += resendToken
            answer()?.let(onEvent)
        }

        override suspend fun signIn(credential: String): String? = signInResult()

        override suspend fun signInWithCode(verificationId: String, code: String): String? = signInResult()
    }

    private val phone = "+905000000001"

    @Test
    fun should_fail_with_sign_in_failed_when_auto_verified_sign_in_throws() = runTest {
        val driver = FakeDriver(
            answer = { PhoneVerificationEvent.Completed("credential") },
            signInResult = { throw IllegalStateException("sign-in task failed") },
        )

        val result = PhoneVerificationFlow(driver).start(phone)

        assertEquals(PhoneVerificationResult.Error(PhoneAuthErrorCode.SIGN_IN_FAILED), result)
    }

    @Test
    fun should_fail_rather_than_return_an_empty_token_when_auto_verified_token_is_null() = runTest {
        val driver = FakeDriver(answer = { PhoneVerificationEvent.Completed("credential") }, signInResult = { null })

        val result = PhoneVerificationFlow(driver).start(phone)

        assertEquals(PhoneVerificationResult.Error(PhoneAuthErrorCode.SIGN_IN_FAILED), result)
    }

    @Test
    fun should_fail_when_auto_verified_token_is_blank() = runTest {
        val driver = FakeDriver(answer = { PhoneVerificationEvent.Completed("credential") }, signInResult = { "" })

        assertEquals(
            PhoneVerificationResult.Error(PhoneAuthErrorCode.SIGN_IN_FAILED),
            PhoneVerificationFlow(driver).start(phone)
        )
    }

    @Test
    fun should_return_auto_verified_with_the_token_when_sign_in_succeeds() = runTest {
        val driver = FakeDriver(answer = { PhoneVerificationEvent.Completed("credential") })

        assertEquals(PhoneVerificationResult.AutoVerified("id-token"), PhoneVerificationFlow(driver).start(phone))
    }

    @Test
    fun should_keep_the_driver_classification_when_sign_in_throws_a_typed_error() = runTest {
        val driver = FakeDriver(
            answer = { PhoneVerificationEvent.Completed("credential") },
            signInResult = { throw PhoneAuthException(PhoneAuthErrorCode.RATE_LIMITED) },
        )

        assertEquals(
            PhoneVerificationResult.Error(PhoneAuthErrorCode.RATE_LIMITED),
            PhoneVerificationFlow(driver).start(phone)
        )
    }

    @Test
    fun should_time_out_with_a_typed_error_when_the_sdk_never_answers() = runTest {
        val driver = FakeDriver(answer = { null })

        val result = PhoneVerificationFlow(driver, firstEventTimeout = 5.seconds).start(phone)

        assertEquals(PhoneVerificationResult.Error(PhoneAuthErrorCode.TIMEOUT), result)
    }

    @Test
    fun should_report_configuration_when_the_sdk_refuses_to_start() = runTest {
        val driver = FakeDriver(answer = { throw IllegalStateException("no FirebaseApp") })

        assertEquals(
            PhoneVerificationResult.Error(PhoneAuthErrorCode.CONFIGURATION),
            PhoneVerificationFlow(driver).start(phone)
        )
    }

    @Test
    fun should_pass_through_the_classified_failure_when_verification_fails() = runTest {
        val driver = FakeDriver(answer = { PhoneVerificationEvent.Failed(PhoneAuthErrorCode.INVALID_PHONE) })

        assertEquals(
            PhoneVerificationResult.Error(PhoneAuthErrorCode.INVALID_PHONE),
            PhoneVerificationFlow(driver).start(phone)
        )
    }

    @Test
    fun should_reuse_the_resend_token_when_the_same_number_asks_again() = runTest {
        val driver = FakeDriver(answer = { PhoneVerificationEvent.CodeSent("vid-1", "resend-1") })
        val flow = PhoneVerificationFlow(driver)

        assertEquals(PhoneVerificationResult.CodeSent("vid-1"), flow.start(phone))
        flow.start(phone)

        assertEquals(listOf(null, "resend-1"), driver.resendTokensSeen)
    }

    @Test
    fun should_not_reuse_a_resend_token_when_a_different_number_asks() = runTest {
        val driver = FakeDriver(answer = { PhoneVerificationEvent.CodeSent("vid-1", "resend-1") })
        val flow = PhoneVerificationFlow(driver)

        flow.start(phone)
        flow.start("+905000000002")

        assertNull(driver.resendTokensSeen.last())
    }

    @Test
    fun should_throw_a_typed_exception_when_code_sign_in_yields_no_token() = runTest {
        val driver = FakeDriver(signInResult = { null })

        val e = assertFailsWith<PhoneAuthException> { PhoneVerificationFlow(driver).verifyCode("vid", "123456") }

        assertEquals(PhoneAuthErrorCode.SIGN_IN_FAILED, e.code)
    }

    @Test
    fun should_return_the_token_when_code_sign_in_succeeds() = runTest {
        assertEquals("id-token", PhoneVerificationFlow(FakeDriver()).verifyCode("vid", "123456"))
    }
}
