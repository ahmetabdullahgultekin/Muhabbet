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
 * The data behind the first rung of the name resolution (#549), and the two properties that make it
 * safe to have: it is keyed so the lookup can actually hit, and it does not outlive the permission.
 *
 * The resolution *order* is not tested here — that is `ChatTargetTest`, against the one definition
 * in `ParticipantResponse.resolveName`. This class only decides which numbers get a name at all.
 */
class ContactNameDirectoryTest {

    @Test
    fun should_key_names_by_e164_whatever_the_address_book_wrote() {
        // The server sends `phoneNumber` as E.164, so a key in any other shape is a key that can
        // never be looked up — the map would look consulted and resolve nothing.
        val names = ContactNameDirectory.contactNameMapOf(
            listOf(
                DeviceContact(name = "Anne", phoneNumber = "0532 111 22 33"),
                DeviceContact(name = "Baba", phoneNumber = "+90 (533) 111-2233"),
                DeviceContact(name = "Kardeş", phoneNumber = "5341112233")
            )
        )

        assertEquals(
            mapOf(
                "+905321112233" to "Anne",
                "+905331112233" to "Baba",
                "+905341112233" to "Kardeş"
            ),
            names
        )
    }

    @Test
    fun should_drop_a_contact_whose_number_cannot_be_normalized() {
        val names = ContactNameDirectory.contactNameMapOf(
            listOf(
                DeviceContact(name = "Pizza", phoneNumber = "4141"),
                DeviceContact(name = "Anne", phoneNumber = "05321112233")
            )
        )

        assertEquals(setOf("+905321112233"), names.keys)
    }

    @Test
    fun should_drop_a_contact_saved_without_a_name() {
        // A blank name would win the first rung and render an empty title — #543's shape, arriving
        // from the address book rather than from the server.
        val names = ContactNameDirectory.contactNameMapOf(
            listOf(DeviceContact(name = "   ", phoneNumber = "05321112233"))
        )

        assertTrue(names.isEmpty())
    }

    @Test
    fun should_publish_the_names_once_access_is_granted() = runTest(UnconfinedTestDispatcher()) {
        val provider = FakeContactsProvider(granted = true, contacts = listOf(anne))
        val directory = ContactNameDirectory(provider, accessControllerFor(provider))

        val seen = mutableListOf<Map<String, String>>()
        directory.names.onEach { seen += it }.launchIn(backgroundScope)
        runCurrent()

        assertEquals(emptyMap<String, String>(), seen.first(), "nothing is known before the first read")

        directory.refresh()
        runCurrent()

        assertEquals(mapOf("+905321112233" to "Anne"), directory.names.value)
    }

    @Test
    fun should_not_read_the_address_book_without_permission() = runTest {
        // Reading it anyway is the one thing that would turn a naming improvement into a privacy
        // regression, so the guard is asserted rather than assumed: FakeContactsProvider throws.
        val provider = FakeContactsProvider(granted = false, contacts = listOf(anne))
        val directory = ContactNameDirectory(provider, accessControllerFor(provider))

        directory.refresh()

        assertTrue(directory.names.value.isEmpty())
    }

    @Test
    fun should_forget_the_names_when_access_is_revoked() = runTest {
        val provider = FakeContactsProvider(granted = true, contacts = listOf(anne))
        val access = accessControllerFor(provider)
        val directory = ContactNameDirectory(provider, access)
        directory.refresh()
        assertEquals(1, directory.names.value.size)

        // Revoked in system settings and the app brought back to the foreground. Keeping the last
        // read would leave names on screen that the app is no longer allowed to have read.
        provider.granted = false
        access.refresh()
        directory.refresh()

        assertTrue(directory.names.value.isEmpty())
    }

    @Test
    fun should_keep_the_names_it_has_when_a_read_fails() = runTest {
        val provider = FakeContactsProvider(granted = true, contacts = listOf(anne))
        val directory = ContactNameDirectory(provider, accessControllerFor(provider))
        directory.refresh()

        // A failed read is not evidence the names are wrong. Blanking here would rewrite every
        // open list back to phone numbers on a transient provider error.
        provider.failing = true
        directory.refresh()

        assertEquals(mapOf("+905321112233" to "Anne"), directory.names.value)
    }

    private val anne = DeviceContact(name = "Anne", phoneNumber = "05321112233")

    private fun accessControllerFor(provider: FakeContactsProvider) =
        ContactsAccessController(provider, FakeTokenStorage())

    private class FakeContactsProvider(
        var granted: Boolean,
        private val contacts: List<DeviceContact>
    ) : ContactsProvider {
        var failing = false

        override fun hasPermission(): Boolean = granted

        override fun readContacts(): List<DeviceContact> {
            if (!granted) throw AssertionError("the address book must not be read without permission")
            if (failing) throw RuntimeException("provider unavailable")
            return contacts
        }

        override fun openSystemSettings() =
            throw AssertionError("the directory must never navigate anywhere")
    }
}
