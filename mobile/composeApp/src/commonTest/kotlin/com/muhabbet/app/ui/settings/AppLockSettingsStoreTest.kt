package com.muhabbet.app.ui.settings

import com.muhabbet.app.config.AppLockTimeout
import com.muhabbet.app.data.local.AppLockController
import com.muhabbet.app.data.local.FakeTokenStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * App Lock settings as a Store (ADR 0010). The rule under test is #378's: the lock is armed only
 * after the platform proves the credential works. It used to be enforced by the order of statements
 * inside a composable, where no test could reach it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppLockSettingsStoreTest {

    private val storage = FakeTokenStorage()
    private val controller = AppLockController(storage)

    private fun TestScope.store() = AppLockSettingsStore(controller, UnconfinedTestDispatcher(testScheduler))

    @Test
    fun should_askForAuthenticationWithoutArming_when_toggledOn() = runTest {
        val store = store()

        store.onEvent(AppLockSettingsEvent.ToggleRequested(on = true))

        assertEquals(AppLockSettingsEffect.Authenticate, store.effects.first())
        assertFalse(storage.getAppLockEnabled(), "a lock is not armed before its credential is proven")
        assertFalse(store.state.value.enabled)
    }

    @Test
    fun should_armTheLock_when_authenticationSucceeds() = runTest {
        val store = store()
        store.onEvent(AppLockSettingsEvent.ToggleRequested(on = true))

        store.onEvent(AppLockSettingsEvent.AuthenticationFinished(success = true))

        assertTrue(storage.getAppLockEnabled())
        assertTrue(store.state.value.enabled)
        assertFalse(store.state.value.authFailed)
    }

    @Test
    fun should_stayOffAndSaySo_when_authenticationFails() = runTest {
        val store = store()
        store.onEvent(AppLockSettingsEvent.ToggleRequested(on = true))

        store.onEvent(AppLockSettingsEvent.AuthenticationFinished(success = false))

        assertFalse(storage.getAppLockEnabled())
        assertTrue(store.state.value.authFailed)
    }

    @Test
    fun should_clearThePreviousFailure_when_toggledOnAgain() = runTest {
        val store = store()
        store.onEvent(AppLockSettingsEvent.AuthenticationFinished(success = false))

        store.onEvent(AppLockSettingsEvent.ToggleRequested(on = true))

        assertFalse(store.state.value.authFailed)
    }

    @Test
    fun should_disarmWithoutAuthentication_when_toggledOff() = runTest {
        controller.setEnabled(true)
        val store = store()

        store.onEvent(AppLockSettingsEvent.ToggleRequested(on = false))

        assertFalse(storage.getAppLockEnabled())
        assertFalse(store.state.value.enabled)
    }

    @Test
    fun should_persistTheChoice_when_timeoutSelected() = runTest {
        controller.setEnabled(true)
        val store = store()

        store.onEvent(AppLockSettingsEvent.TimeoutSelected(AppLockTimeout.THIRTY_MINUTES))

        assertEquals(AppLockTimeout.THIRTY_MINUTES, storage.getAppLockTimeout())
        assertEquals(AppLockTimeout.THIRTY_MINUTES, store.state.value.timeout)
    }

    @Test
    fun should_followTheController_when_anotherWriterChangesIt() = runTest {
        val store = store()

        controller.setEnabled(true)

        assertTrue(store.state.value.enabled, "the controller is the source of truth; the Store observes it")
    }
}
