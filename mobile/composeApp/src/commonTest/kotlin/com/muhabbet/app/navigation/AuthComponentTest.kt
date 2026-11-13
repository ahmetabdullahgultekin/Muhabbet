package com.muhabbet.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.muhabbet.app.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The sign-in flow's navigation, driven by its state machine (#819). */
class AuthComponentTest {

    private var now = 1_000L
    private var onboardingFinished = 0

    private fun component(
        startAtProfile: Boolean = false,
        savedState: SerializableContainer? = null,
        stateKeeper: StateKeeperDispatcher = StateKeeperDispatcher(savedState)
    ): AuthComponent = AuthComponent(
        componentContext = DefaultComponentContext(
            lifecycle = LifecycleRegistry().apply { resume() },
            stateKeeper = stateKeeper
        ),
        startAtProfile = startAtProfile,
        sessionState = MutableStateFlow(SessionState.SignedOut),
        onFinishOnboarding = { onboardingFinished++ },
        clock = { now }
    )

    private val AuthComponent.screens get() = childStack.value.items.map { it.configuration }

    @Test
    fun should_show_the_code_on_top_of_the_phone_number_and_go_back_to_it() {
        val auth = component()

        auth.onPhoneSubmitted("+905000000001", devCode = "123456")
        assertEquals(
            listOf(AuthComponent.Config.PhoneInput, AuthComponent.Config.OtpVerify("+905000000001")),
            auth.screens
        )

        auth.goBack()
        assertEquals(listOf<AuthComponent.Config>(AuthComponent.Config.PhoneInput), auth.screens)
    }

    @Test
    fun should_replace_the_spent_code_with_the_profile_step_so_back_cannot_reach_it() {
        val auth = component()
        auth.onPhoneSubmitted("+905000000001")
        auth.onOtpVerified(isNewUser = true)

        assertEquals(listOf<AuthComponent.Config>(AuthComponent.Config.ProfileSetup), auth.screens)

        auth.goBack()
        assertEquals(listOf<AuthComponent.Config>(AuthComponent.Config.ProfileSetup), auth.screens)
        assertEquals(AuthFlowState.NeedsProfile, auth.state.value)
    }

    @Test
    fun should_report_the_finished_profile_to_the_session_exactly_once() {
        val auth = component(startAtProfile = true)
        assertEquals(listOf<AuthComponent.Config>(AuthComponent.Config.ProfileSetup), auth.screens)

        auth.onProfileSetupComplete()
        auth.onProfileSetupComplete()

        assertEquals(1, onboardingFinished)
    }

    @Test
    fun should_restore_the_code_step_after_process_death_without_the_code_itself() {
        val keeper = StateKeeperDispatcher()
        val before = component(stateKeeper = keeper)
        now = 5_000L
        before.onPhoneSubmitted("+905000000001", devCode = "123456")
        // Through real serialisation, as a process kill would: the container otherwise holds the
        // live object and would hand back fields that were never written.
        val saved = Json.decodeFromString(
            SerializableContainer.serializer(),
            Json.encodeToString(SerializableContainer.serializer(), keeper.save())
        )

        now = 65_000L
        val after = component(savedState = saved)

        assertEquals(
            listOf(AuthComponent.Config.PhoneInput, AuthComponent.Config.OtpVerify("+905000000001")),
            after.screens
        )
        val code = after.state.value as AuthFlowState.CodeSent
        assertNull(code.devCode, "the one-time code must not survive in saved state")
        // The countdown resumes from when the code was sent, not from a fresh five minutes.
        assertEquals(240, after.codeSecondsRemaining(code))
    }

    @Test
    fun should_restart_the_countdown_when_the_code_is_resent() {
        val auth = component()
        auth.onPhoneSubmitted("+905000000001")
        now += 200_000L
        auth.onCodeResent(devCode = null)

        val code = auth.state.value as AuthFlowState.CodeSent
        assertTrue(auth.codeSecondsRemaining(code) == BACKEND_CODE_LIFETIME_SECONDS)
    }
}
