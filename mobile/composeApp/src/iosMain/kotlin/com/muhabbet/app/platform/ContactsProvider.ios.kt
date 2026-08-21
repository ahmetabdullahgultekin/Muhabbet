package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Contacts.CNContactStore
import platform.Contacts.CNAuthorizationStatusAuthorized
import platform.Contacts.CNEntityType
import platform.Contacts.CNContactFetchRequest
import platform.Contacts.CNContactGivenNameKey
import platform.Contacts.CNContactFamilyNameKey
import platform.Contacts.CNContactPhoneNumbersKey
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

class IosContactsProvider : ContactsProvider {
    private val store = CNContactStore()

    override fun hasPermission(): Boolean {
        return CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts) ==
            CNAuthorizationStatusAuthorized
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun readContacts(): List<DeviceContact> {
        if (!hasPermission()) return emptyList()

        val contacts = mutableListOf<DeviceContact>()
        try {
            val keysToFetch = listOf(
                CNContactGivenNameKey,
                CNContactFamilyNameKey,
                CNContactPhoneNumbersKey
            )
            val request = CNContactFetchRequest(keysToFetch = keysToFetch)
            store.enumerateContactsWithFetchRequest(request, error = null) { contact, _ ->
                val name = "${contact?.givenName ?: ""} ${contact?.familyName ?: ""}".trim()
                val phones = (contact?.phoneNumbers as? List<*>)?.mapNotNull { labeledValue ->
                    @Suppress("UNCHECKED_CAST")
                    val phoneNumber = (labeledValue as? platform.Contacts.CNLabeledValue)?.value
                    (phoneNumber as? platform.Contacts.CNPhoneNumber)?.stringValue
                } ?: emptyList()

                phones.forEach { phone ->
                    contacts.add(DeviceContact(name = name, phoneNumber = phone))
                }
            }
        } catch (_: Exception) {
            // Contact fetch failed
        }
        return contacts
    }

    /**
     * The app's own page in Settings. iOS shows a per-app privacy list there, so Contacts is one
     * tap away — and, as on Android, it is the only route left once the system prompt has been
     * answered, because `requestAccessForEntityType` returns immediately without prompting again.
     */
    @OptIn(ExperimentalForeignApi::class)
    override fun openSystemSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

@Composable
actual fun rememberContactsPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit {
    return {
        CNContactStore().requestAccessForEntityType(CNEntityType.CNEntityTypeContacts) { granted, _ ->
            onResult(granted)
        }
    }
}
