package com.muhabbet.app.ui.auth

import com.muhabbet.shared.model.UserProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When the signed-in app asks for a date of birth (#751). */
class AgeVerificationGateTest {

    private fun profile(required: Boolean) = UserProfile(
        id = "u1",
        displayName = null,
        avatarUrl = null,
        ageVerificationRequired = required
    )

    @Test
    fun should_ask_for_a_date_of_birth_when_the_server_flags_the_account() = runTest {
        assertTrue(ageDeclarationDue { profile(required = true) })
    }

    @Test
    fun should_not_ask_when_the_account_already_has_an_assured_age() = runTest {
        assertFalse(ageDeclarationDue { profile(required = false) })
    }

    @Test
    fun should_not_block_the_app_when_the_flag_cannot_be_loaded() = runTest {
        assertFalse(ageDeclarationDue { throw IllegalStateException("offline") })
    }
}
