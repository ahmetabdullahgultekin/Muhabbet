package com.muhabbet.app.data.local

import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.DeviceContact
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.normalizeToE164
import com.muhabbet.app.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val TAG = "ContactNames"

/**
 * The address book, reduced to the one thing the app shows: a name for a number.
 *
 * A singleton, for the fourth time and the fourth identical reason —
 * [PrivacySettingsController], [AppLockController], [ContactsAccessController]. Until now this map
 * lived in a `remember { mutableStateMapOf() }` inside `ConversationListScreen`, filled by a
 * `LaunchedEffect` that only that screen ran. It worked, on that screen. Every other surface that
 * names a person — the home search, the archived list, starred messages, the forward picker, a chat
 * opened from a notification — called
 * [com.muhabbet.app.ui.conversations.toChatTarget] with no map at all and therefore skipped the
 * first rung of the resolution entirely, showing a bare phone number for someone the user has saved
 * as "Anne" (#549).
 *
 * The alternative was to thread the list screen's map down through the ten call sites that name a
 * person, two of which (`MainComponent`, the forward dialog) are nowhere near it in the tree. That
 * is how the same `LaunchedEffect` ends up copy-pasted into ten screens, and copies drift: #691 is
 * the record of exactly that happening to the permission answer this class depends on, five times
 * over.
 *
 * **The resolution order is not here.** It is
 * `com.muhabbet.app.ui.conversations.resolveName` — one definition, address book → display name →
 * number. This class only supplies the first rung's data.
 *
 * **Privacy (#425, and the constraint #549 sets out).** The name is read on the device, kept in
 * memory, and never persisted or sent anywhere: nothing here writes to storage or to
 * [com.muhabbet.app.data.remote.ApiClient]. Only SHA-256 hashes of numbers ever leave the phone,
 * and that is a different code path with its own consent. [refresh] also *clears* the map when
 * access is not [ContactsAccess.Granted], so revoking the permission takes the names off the screen
 * rather than leaving the last read cached for the rest of the session.
 */
class ContactNameDirectory(
    private val contactsProvider: ContactsProvider,
    private val contactsAccess: ContactsAccessController
) {

    private val _names = MutableStateFlow<Map<String, String>>(emptyMap())

    /**
     * Address-book names keyed by normalized E.164 number, as
     * [com.muhabbet.app.ui.conversations.toChatTarget] expects them.
     *
     * Empty until [refresh] has run and found access — which is the honest state, and why every
     * consumer of it must still have a fallback. A `StateFlow`, so a screen composed after the read
     * gets the names immediately instead of waiting for its own.
     */
    val names: StateFlow<Map<String, String>> = _names.asStateFlow()

    /**
     * Re-reads the address book, or clears the map if the app may no longer look at it.
     *
     * Called from `ContactNamesEffect` whenever [ContactsAccessController.access] changes, which
     * covers the case the per-screen copy could not: granting the permission from system settings
     * and coming back tears down no composition, so nothing keyed on `Unit` re-runs.
     */
    suspend fun refresh() {
        if (contactsAccess.access.value != ContactsAccess.Granted) {
            _names.value = emptyMap()
            return
        }
        // runCatchingCancellable, not `catch (e: Exception)`: this is called from an effect keyed on
        // the access flow, so it is cancelled routinely, and CancellationException is an
        // IllegalStateException that a plain catch would log as a failure.
        runCatchingCancellable { withContext(Dispatchers.Default) { contactsProvider.readContacts() } }
            // Keeping the previous map rather than blanking it: a failed read is not evidence that
            // the names are wrong, and dropping them would rewrite every open list to phone numbers.
            .onFailure { e -> Log.e(TAG, "Failed to read device contacts", e) }
            .onSuccess { contacts -> _names.value = contactNameMapOf(contacts) }
    }

    companion object {
        /**
         * Pure, so the keying rule is testable without a device — the shape
         * [ContactsAccessController.contactsAccessOf] uses for the same reason.
         *
         * Keys are E.164 because that is what the server sends back as `phoneNumber` and therefore
         * what the lookup in `toChatTarget` is against; a raw `0532 ...` from the address book
         * would never match. Contacts that will not normalize are dropped rather than keyed on
         * their raw form, because a key that cannot be looked up is worse than no key: it looks
         * like the address book was consulted.
         */
        fun contactNameMapOf(contacts: List<DeviceContact>): Map<String, String> = buildMap {
            contacts.forEach { contact ->
                val digits = contact.phoneNumber.filter { it.isDigit() || it == '+' }
                val normalized = normalizeToE164(digits) ?: return@forEach
                val name = contact.name.trim()
                // A blank saved name is not a name. Letting one in would win the first rung of the
                // resolution and render an empty title — the shape of #543, arriving from the
                // address book instead.
                if (name.isNotEmpty()) put(normalized, name)
            }
        }
    }
}
