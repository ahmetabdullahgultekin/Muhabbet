package com.muhabbet.app.data.local

import com.muhabbet.app.platform.ContactsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/**
 * What the app is allowed to do with the address book, as far as the OS is concerned.
 *
 * Three states rather than a boolean, because "not granted" hides two situations that need
 * different offers. Someone who has never been asked gets the system dialog; someone who has
 * already refused may never see that dialog again — Android stops showing it after two denials —
 * so the only thing left to offer them is the system settings page.
 *
 * There is deliberately no fourth `PermanentlyDenied`. Telling the two denials apart needs
 * `shouldShowRequestPermissionRationale`, which needs an Activity, and `AndroidContactsProvider`
 * holds the application context. A state the app cannot observe is a state it must not claim.
 */
enum class ContactsAccess {
    /** The OS permission is held. The address book can be read. */
    Granted,

    /** The system dialog has never been put in front of this user. Asking is worth a try. */
    NotAsked,

    /**
     * Asked, and still not granted. The dialog may or may not appear again, so the recovery route
     * offered here is [ContactsProvider.openSystemSettings], which always works.
     */
    Denied
}

/**
 * The one answer to "does this app have contacts access, and may it match the address book".
 *
 * A singleton, for the reason [PrivacySettingsController] is one and for the same failure. Until
 * #691 the answer lived in **five** separate `remember { mutableStateOf(...) }` slots —
 * `NewConversationScreen:84`, `PeoplePicker:116` and `ConversationListScreen:222` for the
 * permission, `NewConversationScreen:94` and `PeoplePicker:121` for the #425 consent — each read
 * once when its composable first ran and never again. None could learn that another had changed.
 *
 * The visible failure was not the disagreement between screens but the round trip through system
 * settings, which is the route the app's own copy tells the user to take: *"Rehber erişimi
 * reddedildi. Kişilerinizi görmek için ayarlardan izin verin."* Granting there and coming back tears
 * down no composition — the same mechanism [com.muhabbet.app.platform.AppVisibility] documents for
 * #478 — so every one of those five copies still said no. The conversation list kept showing raw
 * phone numbers and the "Rehber Erişimi Ver" wall stayed up, which reads as the grant not having
 * worked, so the user grants it again.
 *
 * Hence a `StateFlow` and [refresh]. A screen that was already open when access changed sees the
 * change; a screen composed afterwards sees the same value; and `ContactsAccessRefreshEffect`
 * re-reads the OS on every return to the foreground, which is the case a per-screen `remember`
 * cannot express at all.
 *
 * **The two gates stay two gates.** [access] is the OS permission — authorisation to read the
 * address book *on the device*. [syncConsented] is #425 — the user agreeing that hashes derived
 * from other people's numbers may be sent to a server. They are recorded separately here for the
 * same reason `KnownPeopleSource.peopleFromDeviceContacts` insists on both: the people in the
 * address book are not users of this service and agreed to nothing.
 */
class ContactsAccessController(
    private val contactsProvider: ContactsProvider,
    private val tokenStorage: TokenStorage
) {

    private val _access = MutableStateFlow(readAccess())

    /**
     * Re-read on [refresh], never cached beyond it. A `StateFlow`, so a collector that subscribes
     * later still learns the current state and repeated reports of the same state emit nothing.
     */
    val access: StateFlow<ContactsAccess> = _access.asStateFlow()

    private val _syncConsented = MutableStateFlow(tokenStorage.getContactSyncConsentAt() != null)

    /** Whether the user has agreed to contact matching (#425). Persisted, so it survives a restart. */
    val syncConsented: StateFlow<Boolean> = _syncConsented.asStateFlow()

    /**
     * Re-reads the OS and republishes.
     *
     * Called from the permission-result callback and on every return to the foreground. Cheap — a
     * `checkSelfPermission` on Android, an authorisation-status read on iOS — so there is no reason
     * to be clever about when to skip it.
     */
    fun refresh() {
        _access.value = readAccess()
    }

    /**
     * Records that the system dialog is about to be shown. Call this **before** launching it.
     *
     * Before rather than in the callback, following `NotificationPermissionGate`'s reasoning and its
     * history: a user who answers the dialog by swiping the app away never delivers a result, and a
     * flag written in the callback would leave the ask unrecorded. The state would stay [NotAsked]
     * forever, so the app would keep offering a dialog that Android has already stopped showing —
     * a request that silently does nothing, which is much harder to notice than a broken one.
     */
    fun onPermissionRequested() {
        tokenStorage.setContactsPermissionAsked()
        refresh()
    }

    /**
     * Records the #425 consent. One place, so the timestamp format and the flow update cannot drift
     * apart the way the two `remember` copies did.
     */
    fun grantSyncConsent() {
        tokenStorage.setContactSyncConsentAt(Clock.System.now().toString())
        _syncConsented.value = true
    }

    private fun readAccess(): ContactsAccess = contactsAccessOf(
        hasPermission = contactsProvider.hasPermission(),
        hasBeenAsked = tokenStorage.getContactsPermissionAsked()
    )

    companion object {
        /**
         * Pure, so the rule is testable without a device — the same shape as
         * `shouldShowTestBuildNotice` and `shouldRequestNotificationPermission`.
         *
         * The permission wins over the asked flag: a user who refused and later granted from system
         * settings is [Granted], not [Denied], even though they were once asked and once said no.
         */
        fun contactsAccessOf(hasPermission: Boolean, hasBeenAsked: Boolean): ContactsAccess = when {
            hasPermission -> ContactsAccess.Granted
            hasBeenAsked -> ContactsAccess.Denied
            else -> ContactsAccess.NotAsked
        }
    }
}
