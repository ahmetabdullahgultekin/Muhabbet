package com.muhabbet.app.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** The sign-in flow's transition function and what it persists (#819, ADR 0012). */
class AuthFlowTest {

    private val codeSent = AuthFlowState.CodeSent(
        phoneNumber = "+905000000001",
        firebaseVerificationId = null,
        sentAtEpochMs = 1_000L,
        devCode = "123456"
    )

    @Test
    fun should_move_to_code_sent_when_a_code_is_requested() {
        val t = AuthFlowState.EnterPhone.on(
            AuthFlowEvent.CodeRequested("+905000000001", "123456", null, nowEpochMs = 1_000L)
        )
        assertEquals(codeSent, t.state)
        assertNull(t.effect)
    }

    @Test
    fun should_require_a_profile_when_a_new_user_verifies() {
        assertEquals(AuthFlowState.NeedsProfile, codeSent.on(AuthFlowEvent.Verified(isNewUser = true)).state)
    }

    @Test
    fun should_complete_without_onboarding_when_an_existing_user_verifies() {
        val t = codeSent.on(AuthFlowEvent.Verified(isNewUser = false))
        assertEquals(AuthFlowState.Complete, t.state)
        // The session is already signed in by the repository; the flow asks for nothing more.
        assertNull(t.effect)
    }

    @Test
    fun should_accept_a_firebase_auto_verification_straight_from_the_phone_step() {
        assertEquals(
            AuthFlowState.NeedsProfile,
            AuthFlowState.EnterPhone.on(AuthFlowEvent.Verified(isNewUser = true)).state
        )
    }

    @Test
    fun should_return_to_the_phone_step_on_back_from_the_code() {
        assertEquals(AuthFlowState.EnterPhone, codeSent.on(AuthFlowEvent.Back).state)
    }

    @Test
    fun should_never_leave_the_profile_step_on_back() {
        val t = AuthFlowState.NeedsProfile.on(AuthFlowEvent.Back)
        assertEquals(AuthFlowState.NeedsProfile, t.state)
        assertNull(t.effect)
    }

    @Test
    fun should_finish_onboarding_when_the_profile_is_saved() {
        val t = AuthFlowState.NeedsProfile.on(AuthFlowEvent.ProfileSaved)
        assertEquals(AuthFlowState.Complete, t.state)
        assertEquals(AuthFlowEffect.FinishOnboarding, t.effect)
    }

    @Test
    fun should_restart_the_code_clock_when_the_code_is_resent() {
        val t = codeSent.on(AuthFlowEvent.CodeResent(devCode = "654321", nowEpochMs = 90_000L))
        assertEquals(codeSent.copy(sentAtEpochMs = 90_000L, devCode = "654321"), t.state)
    }

    @Test
    fun should_ignore_every_event_that_does_not_belong_to_the_state() {
        val ignored = listOf(
            AuthFlowState.EnterPhone to AuthFlowEvent.Back,
            AuthFlowState.EnterPhone to AuthFlowEvent.ProfileSaved,
            AuthFlowState.EnterPhone to AuthFlowEvent.CodeResent(null, 0L),
            codeSent to AuthFlowEvent.CodeRequested("+905000000002", null, null, 0L),
            codeSent to AuthFlowEvent.ProfileSaved,
            AuthFlowState.NeedsProfile to AuthFlowEvent.Verified(isNewUser = false),
            AuthFlowState.NeedsProfile to AuthFlowEvent.CodeRequested("+905000000002", null, null, 0L),
            AuthFlowState.Complete to AuthFlowEvent.Back,
            AuthFlowState.Complete to AuthFlowEvent.ProfileSaved,
            AuthFlowState.Complete to AuthFlowEvent.Verified(isNewUser = true)
        )
        ignored.forEach { (state, event) ->
            val t = state.on(event)
            assertEquals(state, t.state, "$event must not move $state")
            assertNull(t.effect)
        }
    }

    @Test
    fun should_count_the_code_down_from_when_it_was_sent_not_from_when_the_screen_opened() {
        assertEquals(300, codeSent.secondsRemaining(nowEpochMs = 1_000L))
        assertEquals(240, codeSent.secondsRemaining(nowEpochMs = 61_000L))
        assertEquals(0, codeSent.secondsRemaining(nowEpochMs = 1_000_000L))
        val firebase = codeSent.copy(firebaseVerificationId = "vid")
        assertEquals(60, firebase.secondsRemaining(nowEpochMs = 1_000L))
        // A clock that went backwards must not give the code more than its lifetime.
        assertEquals(300, codeSent.secondsRemaining(nowEpochMs = 0L))
    }

    @Test
    fun should_never_write_the_one_time_code_into_saved_state() {
        val saved = Json.encodeToString(AuthFlowState.serializer(), codeSent)

        assertFalse("123456" in saved, "saved state must not contain the code: $saved")
        val restored = Json.decodeFromString(AuthFlowState.serializer(), saved)
        assertEquals(codeSent.copy(devCode = null), restored)
    }

    @Test
    fun should_never_put_the_one_time_code_in_a_navigation_config() {
        AuthComponent.stackFor(codeSent).forEach { config ->
            val saved = Json.encodeToString(AuthComponent.Config.serializer(), config)
            assertFalse("123456" in saved, "config must not contain the code: $saved")
        }
    }

    @Test
    fun should_show_the_profile_step_alone_so_back_has_nothing_to_return_to() {
        assertEquals(listOf(AuthComponent.Config.ProfileSetup), AuthComponent.stackFor(AuthFlowState.NeedsProfile))
        assertEquals(
            listOf(
                AuthComponent.Config.PhoneInput,
                AuthComponent.Config.OtpVerify("+905000000001", null)
            ),
            AuthComponent.stackFor(codeSent)
        )
    }
}
