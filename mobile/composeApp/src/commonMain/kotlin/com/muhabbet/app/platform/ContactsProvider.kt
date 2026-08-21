package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

data class DeviceContact(val name: String, val phoneNumber: String)

interface ContactsProvider {
    fun hasPermission(): Boolean
    fun readContacts(): List<DeviceContact>

    /**
     * Opens the OS page where contacts access for this app can be turned on.
     *
     * The only recovery route that always works. Android stops showing the permission dialog after
     * two refusals, so a user who has said no twice can tap "grant access" forever and watch
     * nothing happen — and the app's own copy already tells them the answer is in settings
     * (`contacts_permission_denied`: *"Kişilerinizi görmek için ayarlardan izin verin"*) without
     * giving them a way to get there. `NotificationPermission.openSystemSettings` exists for
     * exactly this reason; this is its counterpart (#692).
     */
    fun openSystemSettings()
}

@Composable
expect fun rememberContactsPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit
