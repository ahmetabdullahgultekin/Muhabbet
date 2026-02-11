package com.muhabbet.app.platform

import android.content.Context
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat

class AndroidContactsProvider(private val context: Context) : ContactsProvider {

    override fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    override fun readContacts(): List<DeviceContact> {
        val contacts = mutableListOf<DeviceContact>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = if (nameCol >= 0) it.getString(nameCol) else null
                val phone = if (phoneCol >= 0) it.getString(phoneCol) else null
                if (name != null && phone != null) {
                    contacts.add(DeviceContact(name, phone))
                }
            }
        }
        return contacts.distinctBy { normalizePhone(it.phoneNumber) }
    }

    private fun normalizePhone(phone: String): String {
        return phone.filter { it.isDigit() || it == '+' }
    }
}

@Composable
actual fun rememberContactsPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult(it) }
    return { launcher.launch(android.Manifest.permission.READ_CONTACTS) }
}
