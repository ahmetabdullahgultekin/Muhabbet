package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

class IosContactsProvider : ContactsProvider {
    override fun hasPermission(): Boolean = false
    override fun readContacts(): List<DeviceContact> = emptyList()
}

@Composable
actual fun rememberContactsPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit {
    // TODO: Implement CNContactStore permission request for iOS
    return { onResult(false) }
}
