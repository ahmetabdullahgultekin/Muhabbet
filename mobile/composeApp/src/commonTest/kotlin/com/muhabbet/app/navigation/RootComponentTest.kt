package com.muhabbet.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.session.SessionEndReason
import com.muhabbet.app.session.SessionManager
import com.muhabbet.app.session.SessionState
import com.muhabbet.app.session.testSessionManager
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The root follows the session, in both directions, for the life of the component (#813, #819).
 */
class RootComponentTest {

    private val lifecycle = LifecycleRegistry()
    private val storage = FakeTokenStorage()

    private fun root(session: SessionManager): RootComponent =
        RootComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            tokenStorage = storage,
            sessionManager = session,
            mainContext = Dispatchers.Unconfined
        ).also { lifecycle.resume() }

    private val RootComponent.active get() = childStack.value.active.instance
    private val RootComponent.stackConfigs get() = childStack.value.items.map { it.configuration }

    @Test
    fun should_open_the_app_when_the_session_starts_signed_in() {
        storage.saveTokens("t", "r", "a", "d")
        assertIs<RootComponent.Child.Main>(root(testSessionManager(storage)).active)
    }

    @Test
    fun should_return_to_sign_in_with_the_expiry_reason_when_the_refresh_is_rejected() {
        storage.saveTokens("t", "r", "a", "d")
        val session = testSessionManager(storage)
        val root = root(session)

        session.onRefreshRejected()

        assertEquals(listOf<RootComponent.Config>(RootComponent.Config.Auth), root.stackConfigs)
        val auth = assertIs<RootComponent.Child.Auth>(root.active).component
        assertEquals(SessionState.Expired(SessionEndReason.REFRESH_REJECTED), auth.sessionState.value)
    }

    @Test
    fun should_return_to_sign_in_when_the_account_is_deleted() {
        storage.saveTokens("t", "r", "a", "d")
        val session = testSessionManager(storage)
        val root = root(session)

        session.accountDeleted()

        assertEquals(listOf<RootComponent.Config>(RootComponent.Config.Auth), root.stackConfigs)
    }

    @Test
    fun should_open_the_app_when_an_existing_user_signs_in() {
        val session = testSessionManager(storage)
        val root = root(session)
        assertIs<RootComponent.Child.Auth>(root.active)

        session.signIn("t", "r", "a", "d", isNewUser = false)

        assertIs<RootComponent.Child.Main>(root.active)
    }

    @Test
    fun should_resume_at_profile_setup_not_the_app_when_onboarding_never_finished() {
        storage.setProfileSetupPending(true)
        storage.saveTokens("t", "r", "a", "d")

        val root = root(testSessionManager(storage))

        val auth = assertIs<RootComponent.Child.Auth>(root.active).component
        assertEquals(
            listOf<AuthComponent.Config>(AuthComponent.Config.ProfileSetup),
            auth.childStack.value.items.map { it.configuration }
        )
    }

    @Test
    fun should_stay_in_sign_in_until_a_new_user_saves_a_profile_and_then_open_the_app() {
        val session = testSessionManager(storage)
        val root = root(session)
        val auth = assertIs<RootComponent.Child.Auth>(root.active).component

        auth.onPhoneSubmitted("+905000000001")
        session.signIn("t", "r", "a", "d", isNewUser = true)
        auth.onOtpVerified(isNewUser = true)
        assertIs<RootComponent.Child.Auth>(root.active)

        auth.onProfileSetupComplete()

        assertIs<RootComponent.Child.Main>(root.active)
        assertTrue(session.state.value is SessionState.SignedIn)
    }

    @Test
    fun should_stop_following_the_session_once_destroyed() {
        storage.saveTokens("t", "r", "a", "d")
        val session = testSessionManager(storage)
        val root = root(session)
        lifecycle.destroy()

        session.logout()

        // Destroyed components must not navigate: the collector was cancelled with the lifecycle.
        assertIs<RootComponent.Child.Main>(root.active)
    }
}
