package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

data class DeviceContact(val name: String, val phoneNumber: String)

interface ContactsProvider {
    fun hasPermission(): Boolean
    fun readContacts(): List<DeviceContact>
}

@Composable
expect fun rememberContactsPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit
