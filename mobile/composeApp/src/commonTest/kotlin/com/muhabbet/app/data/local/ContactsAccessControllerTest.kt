package com.muhabbet.app.data.local

import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.DeviceContact
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The contract #691 is about: **one** answer to "does this app have contacts access", and a reader
 * that was already looking when the answer changed finds out.
 *
 * The bug these cover is not a wrong value — every one of the five `remember { mutableStateOf(...) }`
 * copies read the OS correctly at the moment it ran. It is that none of them could ever be told
 * about a change, so a user who granted the permission from system settings (which is what
 * `contacts_permission_denied` instructs) came back to screens still insisting they had not.
 *
 * [should_tell_readers_that_were_already_open_when_access_is_granted_elsewhere] is the one that
 * fails if this ever regresses to a per-screen snapshot: a `remember` would also read `Granted`, but
 * only when it is composed afresh, and re-composition is exactly what returning to the foreground
 * does not cause.
 */
class ContactsAccessControllerTest {

    @Test
    fun should_report_not_asked_before_the_dialog_has_ever_been_shown() {
        val controller = controllerWith(hasPermission = false)

        assertEquals(ContactsAccess.NotAsked, controller.access.value)
    }

    @Test
    fun should_tell_readers_that_were_already_open_when_access_is_granted_elsewhere() =
        runTest(UnconfinedTestDispatcher()) {
            val provider = FakeContactsProvider(granted = false)
            val controller = ContactsAccessController(provider, FakeTokenStorage())

            // Two readers, subscribed BEFORE anything changes — the conversation list and an open
            // "Yeni Sohbet" screen, in the app.
            val listSaw = mutableListOf<ContactsAccess>()
            val pickerSaw = mutableListOf<ContactsAccess>()
            controller.access.onEach { listSaw += it }.launchIn(backgroundScope)
            controller.access.onEach { pickerSaw += it }.launchIn(backgroundScope)
            runCurrent()

            assertEquals(listOf(ContactsAccess.NotAsked), listSaw)

            // The user leaves for system settings, grants it there, and comes back. No composition
            // was torn down, so nothing re-read anything: the only thing that can move the app off
            // its stale answer is the foreground refresh.
            provider.granted = true
            controller.refresh()
            runCurrent()

            assertEquals(
                listOf(ContactsAccess.NotAsked, ContactsAccess.Granted),
                listSaw,
                "A reader that was already open must learn that access was granted elsewhere. " +
                    "This is the whole of #691: five per-screen copies, none of which could be told."
            )
            assertEquals(listSaw, pickerSaw, "Two readers of one flow cannot disagree.")
        }

    @Test
    fun should_not_re_emit_when_the_answer_has_not_actually_changed() =
        runTest(UnconfinedTestDispatcher()) {
            val provider = FakeContactsProvider(granted = true)
            val controller = ContactsAccessController(provider, FakeTokenStorage())

            val seen = mutableListOf<ContactsAccess>()
            controller.access.onEach { seen += it }.launchIn(backgroundScope)
            runCurrent()

            // Every return to the foreground calls refresh(). Most of them change nothing, and a
            // flow that re-emitted each time would re-run every effect keyed on it — including the
            // conversation list's address-book read.
            repeat(5) { controller.refresh() }
            runCurrent()

            assertEquals(listOf(ContactsAccess.Granted), seen)
        }

    @Test
    fun should_remember_that_the_dialog_has_already_been_shown() {
        val storage = FakeTokenStorage()
        val controller = ContactsAccessController(FakeContactsProvider(granted = false), storage)

        controller.onPermissionRequested()

        assertEquals(
            ContactsAccess.Denied,
            controller.access.value,
            "Recorded before the dialog is shown, so a user who answers by swiping the app away " +
                "still counts as asked — otherwise the app keeps offering a dialog Android has " +
                "already stopped showing."
        )
        assertTrue(storage.getContactsPermissionAsked(), "and it must survive the process")
    }

    @Test
    fun should_report_granted_even_after_an_earlier_refusal() {
        val storage = FakeTokenStorage().apply { setContactsPermissionAsked() }
        val controller = ContactsAccessController(FakeContactsProvider(granted = true), storage)

        assertEquals(
            ContactsAccess.Granted,
            controller.access.value,
            "Granting from system settings after refusing the dialog is the case the whole issue " +
                "is about. The permission wins over the asked flag."
        )
    }

    @Test
    fun should_share_one_consent_answer_between_readers() = runTest(UnconfinedTestDispatcher()) {
        val storage = FakeTokenStorage()
        val controller = ContactsAccessController(FakeContactsProvider(granted = true), storage)

        val seen = mutableListOf<Boolean>()
        controller.syncConsented.onEach { seen += it }.launchIn(backgroundScope)
        runCurrent()

        // Accepted on one screen; the other screen holding a second copy is how a consent with
        // legal weight (#425) was able to read differently in two places at once.
        controller.grantSyncConsent()
        runCurrent()

        assertEquals(listOf(false, true), seen)
        assertTrue(
            storage.getContactSyncConsentAt() != null,
            "and it is persisted, so a restart does not re-ask"
        )
    }

    @Test
    fun should_read_a_recorded_consent_back_on_the_next_launch() {
        val storage = FakeTokenStorage()
        ContactsAccessController(FakeContactsProvider(granted = true), storage).grantSyncConsent()

        val afterRestart = ContactsAccessController(FakeContactsProvider(granted = true), storage)

        assertTrue(afterRestart.syncConsented.value)
    }

    @Test
    fun should_derive_access_from_the_permission_and_whether_we_have_asked() {
        val cases = listOf(
            Triple(false, false, ContactsAccess.NotAsked),
            Triple(false, true, ContactsAccess.Denied),
            Triple(true, false, ContactsAccess.Granted),
            Triple(true, true, ContactsAccess.Granted)
        )

        cases.forEach { (hasPermission, hasBeenAsked, expected) ->
            assertEquals(
                expected,
                ContactsAccessController.contactsAccessOf(hasPermission, hasBeenAsked),
                "hasPermission=$hasPermission hasBeenAsked=$hasBeenAsked"
            )
        }
    }

    private fun controllerWith(hasPermission: Boolean) =
        ContactsAccessController(FakeContactsProvider(hasPermission), FakeTokenStorage())

    /**
     * Mutable on purpose: the OS answer changing under a running app is the situation being tested,
     * and a fixed stub could not express it.
     */
    private class FakeContactsProvider(var granted: Boolean) : ContactsProvider {
        override fun hasPermission(): Boolean = granted

        override fun readContacts(): List<DeviceContact> =
            throw AssertionError("the access controller must never read the address book itself")

        override fun openSystemSettings() =
            throw AssertionError("the access controller must never navigate anywhere")
    }
}
