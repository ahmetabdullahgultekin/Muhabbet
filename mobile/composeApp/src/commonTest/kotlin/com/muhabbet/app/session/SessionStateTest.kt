package com.muhabbet.app.session

import com.muhabbet.app.session.SessionEffect.ClaimAccountData
import com.muhabbet.app.session.SessionEffect.ClearCredentials
import com.muhabbet.app.session.SessionEffect.ClearProfilePending
import com.muhabbet.app.session.SessionEffect.CloseAccountScope
import com.muhabbet.app.session.SessionEffect.OpenAccountScope
import com.muhabbet.app.session.SessionEffect.WipeAccountData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The session's transition function (#813, ADR 0012): every legal transition, the effects it asks
 * for, and every pair that must leave the state alone.
 */
class SessionStateTest {

    private val signedIn = SessionState.SignedIn("a")
    private val refreshing = SessionState.Refreshing("a")
    private val needsProfile = SessionState.NeedsProfile("a")
    private val expired = SessionState.Expired(SessionEndReason.REFRESH_REJECTED)

    private val allStates = listOf(SessionState.SignedOut, needsProfile, signedIn, refreshing, expired)

    private fun authenticated(userId: String = "a", needsProfile: Boolean = false, owner: String? = "a") =
        SessionEvent.Authenticated(userId, needsProfile, owner)

    // --- initial state ---

    @Test
    fun should_start_signed_out_when_no_access_token_is_stored() {
        assertEquals(SessionState.SignedOut, initialSessionState(null, "a", profileSetupPending = false))
        assertEquals(SessionState.SignedOut, initialSessionState("t", null, profileSetupPending = false))
    }

    @Test
    fun should_start_at_the_profile_step_when_tokens_exist_but_onboarding_never_finished() {
        assertEquals(needsProfile, initialSessionState("t", "a", profileSetupPending = true))
    }

    @Test
    fun should_start_signed_in_when_tokens_exist_and_onboarding_finished() {
        assertEquals(signedIn, initialSessionState("t", "a", profileSetupPending = false))
    }

    // --- Authenticated ---

    @Test
    fun should_sign_in_and_open_the_account_scope_when_an_existing_user_verifies() {
        val t = SessionState.SignedOut.on(authenticated())
        assertEquals(signedIn, t.state)
        assertEquals(listOf(OpenAccountScope("a")), t.effects)
    }

    @Test
    fun should_require_a_profile_and_open_no_scope_when_a_new_user_verifies() {
        val t = SessionState.SignedOut.on(authenticated(needsProfile = true))
        assertEquals(needsProfile, t.state)
        assertEquals(emptyList(), t.effects)
    }

    @Test
    fun should_wipe_and_claim_the_device_data_when_a_different_account_signs_in() {
        val t = SessionState.SignedOut.on(authenticated(userId = "b", owner = "a"))
        assertEquals(SessionState.SignedIn("b"), t.state)
        assertEquals(listOf(WipeAccountData, ClaimAccountData("b"), OpenAccountScope("b")), t.effects)
    }

    @Test
    fun should_wipe_unowned_device_data_before_anyone_can_see_it() {
        val t = SessionState.SignedOut.on(authenticated(owner = null))
        assertEquals(listOf(WipeAccountData, ClaimAccountData("a"), OpenAccountScope("a")), t.effects)
    }

    @Test
    fun should_keep_the_data_when_the_same_account_signs_back_in_after_expiry() {
        val t = expired.on(authenticated(owner = "a"))
        assertEquals(signedIn, t.state)
        assertEquals(listOf(OpenAccountScope("a")), t.effects)
    }

    @Test
    fun should_ignore_a_verification_while_a_session_is_already_authenticated() {
        listOf(needsProfile, signedIn, refreshing).forEach { state ->
            assertUnchanged(state, authenticated(userId = "b", owner = "a"))
        }
    }

    // --- ProfileCompleted ---

    @Test
    fun should_finish_onboarding_and_open_the_scope_when_the_profile_is_saved() {
        val t = needsProfile.on(SessionEvent.ProfileCompleted)
        assertEquals(signedIn, t.state)
        assertEquals(listOf(ClearProfilePending, OpenAccountScope("a")), t.effects)
    }

    @Test
    fun should_ignore_a_profile_completion_outside_onboarding() {
        (allStates - needsProfile).forEach { assertUnchanged(it, SessionEvent.ProfileCompleted) }
    }

    // --- refresh ---

    @Test
    fun should_move_to_refreshing_when_a_refresh_starts_while_signed_in() {
        assertEquals(refreshing, signedIn.on(SessionEvent.RefreshStarted).state)
    }

    @Test
    fun should_return_to_signed_in_when_a_refresh_succeeds_or_fails_transiently() {
        assertEquals(signedIn, refreshing.on(SessionEvent.RefreshSucceeded).state)
        assertEquals(signedIn, refreshing.on(SessionEvent.RefreshFailedTransiently).state)
    }

    @Test
    fun should_expire_and_clear_credentials_when_the_refresh_token_is_rejected() {
        listOf(signedIn, refreshing, needsProfile).forEach { state ->
            val t = state.on(SessionEvent.RefreshRejected)
            assertEquals(expired, t.state, "from $state")
            // The device data is kept: the same person signing back in keeps their unsent messages.
            assertEquals(listOf(ClearCredentials, CloseAccountScope), t.effects, "from $state")
        }
    }

    @Test
    fun should_ignore_refresh_outcomes_with_no_live_session() {
        val events = listOf(
            SessionEvent.RefreshStarted,
            SessionEvent.RefreshSucceeded,
            SessionEvent.RefreshFailedTransiently,
            SessionEvent.RefreshRejected
        )
        listOf(SessionState.SignedOut, expired).forEach { state ->
            events.forEach { assertUnchanged(state, it) }
        }
        assertUnchanged(signedIn, SessionEvent.RefreshSucceeded)
        assertUnchanged(refreshing, SessionEvent.RefreshStarted)
        assertUnchanged(needsProfile, SessionEvent.RefreshStarted)
    }

    // --- logout and deletion ---

    @Test
    fun should_sign_out_clear_close_and_wipe_when_an_authenticated_user_logs_out_or_deletes() {
        listOf(SessionEvent.LoggedOut, SessionEvent.AccountDeleted).forEach { event ->
            listOf(signedIn, refreshing, needsProfile).forEach { state ->
                val t = state.on(event)
                assertEquals(SessionState.SignedOut, t.state, "$event from $state")
                assertEquals(listOf(ClearCredentials, CloseAccountScope, WipeAccountData), t.effects)
            }
        }
    }

    @Test
    fun should_wipe_the_kept_data_when_an_expired_session_is_abandoned() {
        val t = expired.on(SessionEvent.LoggedOut)
        assertEquals(SessionState.SignedOut, t.state)
        assertEquals(listOf(WipeAccountData), t.effects)
    }

    @Test
    fun should_do_nothing_when_logging_out_while_signed_out() {
        assertUnchanged(SessionState.SignedOut, SessionEvent.LoggedOut)
        assertUnchanged(SessionState.SignedOut, SessionEvent.AccountDeleted)
    }

    // --- derived ---

    @Test
    fun should_count_refreshing_as_an_active_session_and_onboarding_as_not() {
        assertEquals("a", signedIn.activeUserId)
        assertEquals("a", refreshing.activeUserId)
        assertNull(needsProfile.activeUserId)
        assertNull(expired.activeUserId)
        assertNull(SessionState.SignedOut.activeUserId)
        assertEquals("a", needsProfile.authenticatedUserId)
    }

    private fun assertUnchanged(state: SessionState, event: SessionEvent) {
        val t = state.on(event)
        assertEquals(state, t.state, "$event must not move $state")
        assertEquals(emptyList(), t.effects, "$event from $state must ask for nothing")
    }
}
