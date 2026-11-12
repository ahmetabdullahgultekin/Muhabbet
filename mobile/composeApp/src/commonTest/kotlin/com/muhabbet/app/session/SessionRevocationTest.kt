package com.muhabbet.app.session

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A server-ended session leaves for the login screen — once, and only from a signed-in screen.
 *
 * The flag is sticky, and logout itself can raise it: an expired access token on the logout call
 * asks for a refresh, the refresh is refused, and the signal fires after the user is already on the
 * login screen. Acting on it there would reset an OTP entry already in progress.
 */
class SessionRevocationTest {

    @Test
    fun should_leave_for_login_when_a_session_is_on_screen() {
        assertTrue(shouldLeaveForLogin(revoked = true, sessionActive = true))
    }

    @Test
    fun should_not_navigate_when_the_signal_arrives_while_already_logged_out() {
        assertFalse(shouldLeaveForLogin(revoked = true, sessionActive = false))
    }

    @Test
    fun should_not_navigate_when_nothing_was_revoked() {
        assertFalse(shouldLeaveForLogin(revoked = false, sessionActive = true))
    }

    @Test
    fun should_lower_the_flag_when_acknowledged_after_a_signal_while_logged_out() {
        val revocation = SessionRevocation()
        revocation.signal()

        // What App.kt does when the signal arrives on the login screen: acknowledge, do not navigate.
        if (!shouldLeaveForLogin(revocation.revoked.value, sessionActive = false)) revocation.acknowledge()

        assertFalse(revocation.revoked.value, "a sticky flag left raised would fire on the next login")
    }
}
