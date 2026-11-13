package com.muhabbet.app.session

import com.muhabbet.app.data.local.FakeTokenStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The session owner executing what its transitions ask for, against fakes (#813, #819, #829). */
class SessionManagerTest {

    private val storage = FakeTokenStorage()
    private val data = RecordingAccountData()
    private val scope = RecordingAccountScope()

    private fun manager() = SessionManager(storage, data, scope)

    @Test
    fun should_resume_at_the_profile_step_after_a_restart_during_onboarding() {
        val first = manager()
        first.signIn("t", "r", "a", "d", isNewUser = true)

        // A new process reads the same storage — this is the cold start of #819.
        val restarted = manager()

        assertEquals(SessionState.NeedsProfile("a"), restarted.state.value)
        assertNull(scope.openFor, "nothing per-account may start before onboarding finishes")
    }

    @Test
    fun should_open_the_app_on_the_next_start_once_the_profile_is_saved() {
        val session = manager()
        session.signIn("t", "r", "a", "d", isNewUser = true)
        session.completeProfile()

        assertEquals(SessionState.SignedIn("a"), session.state.value)
        assertFalse(storage.isProfileSetupPending())
        assertEquals(SessionState.SignedIn("a"), manager().state.value)
    }

    @Test
    fun should_expire_and_clear_the_tokens_when_the_refresh_is_rejected() {
        storage.saveTokens("t", "r", "a", "d")
        val session = manager()

        session.onRefreshStarted()
        session.onRefreshRejected()

        assertEquals(SessionState.Expired(SessionEndReason.REFRESH_REJECTED), session.state.value)
        assertNull(storage.getAccessToken())
        assertNull(scope.openFor)
        assertEquals(0, data.wipes, "expiry keeps the account's data for the same person to sign back in")
    }

    @Test
    fun should_wipe_the_previous_accounts_data_when_another_account_signs_in_after_expiry() {
        storage.saveTokens("t", "r", "a", "d")
        val session = manager()
        session.onRefreshRejected()

        session.signIn("t2", "r2", "b", "d2", isNewUser = false)

        assertEquals(1, data.wipes)
        assertEquals("b", storage.getLocalDataOwner())
        assertEquals(SessionState.SignedIn("b"), session.state.value)
    }

    @Test
    fun should_keep_the_data_when_the_same_account_signs_back_in_after_expiry() {
        storage.saveTokens("t", "r", "a", "d")
        val session = manager()
        session.onRefreshRejected()

        session.signIn("t2", "r2", "a", "d", isNewUser = false)

        assertEquals(0, data.wipes)
        assertEquals(SessionState.SignedIn("a"), session.state.value)
    }

    @Test
    fun should_clear_wipe_and_close_on_logout_and_then_give_the_next_account_a_fresh_scope() {
        storage.saveTokens("t", "r", "a", "d")
        val session = manager()

        session.logout()

        assertEquals(SessionState.SignedOut, session.state.value)
        assertNull(storage.getAccessToken())
        assertEquals(1, data.wipes)
        assertNull(storage.getLocalDataOwner())

        session.signIn("t2", "r2", "b", "d2", isNewUser = false)
        assertEquals(listOf("open:a", "close:a", "open:b"), scope.history)
    }

    @Test
    fun should_sign_out_when_the_account_is_deleted() {
        storage.saveTokens("t", "r", "a", "d")
        val session = manager()

        session.accountDeleted()

        assertEquals(SessionState.SignedOut, session.state.value)
        assertNull(storage.getAccessToken())
        assertEquals(1, data.wipes)
    }

    @Test
    fun should_claim_rather_than_wipe_the_data_of_an_install_upgraded_while_signed_in() {
        storage.saveTokens("t", "r", "a", "d")

        manager()

        assertEquals("a", storage.getLocalDataOwner())
        assertEquals(0, data.wipes)
        assertEquals("a", scope.openFor)
    }

    @Test
    fun should_record_the_profile_step_before_the_tokens_so_a_kill_between_them_is_safe() {
        val writes = mutableListOf<String>()
        val ordered = object : com.muhabbet.app.data.local.TokenStorage by storage {
            override fun setProfileSetupPending(pending: Boolean) {
                writes += "pending=$pending"
                storage.setProfileSetupPending(pending)
            }

            override fun saveTokens(accessToken: String, refreshToken: String, userId: String, deviceId: String) {
                writes += "tokens"
                storage.saveTokens(accessToken, refreshToken, userId, deviceId)
            }
        }

        SessionManager(ordered, data, scope).signIn("t", "r", "a", "d", isNewUser = true)

        assertEquals(listOf("pending=true", "tokens"), writes)
        assertTrue(storage.isProfileSetupPending())
    }

    @Test
    fun should_go_back_to_signed_in_after_a_refresh_that_could_not_reach_the_server() {
        storage.saveTokens("t", "r", "a", "d")
        val session = manager()

        session.onRefreshStarted()
        assertEquals(SessionState.Refreshing("a"), session.state.value)
        session.onRefreshFailedTransiently()

        assertEquals(SessionState.SignedIn("a"), session.state.value)
        assertEquals("t", storage.getAccessToken())
    }
}
