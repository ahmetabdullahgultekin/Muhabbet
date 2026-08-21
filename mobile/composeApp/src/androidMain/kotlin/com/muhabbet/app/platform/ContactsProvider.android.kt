package com.muhabbet.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import com.muhabbet.app.util.Log

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

    /**
     * The app's own details page, which carries the Permissions entry.
     *
     * There is no per-permission deep link on Android — `ACTION_APPLICATION_DETAILS_SETTINGS` is the
     * closest the platform offers, and it is one tap from the Contacts switch. `FLAG_ACTIVITY_NEW_TASK`
     * because this class is handed the application context, not the Activity.
     *
     * A failure is logged rather than swallowed, following `AndroidNotificationPermission`: a report
     * of "the button does nothing" then reads differently from a report of "the button is not there".
     */
    override fun openSystemSettings() {
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(appDetails)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "App details settings did not resolve", e)
        }
    }

    private fun normalizePhone(phone: String): String {
        return phone.filter { it.isDigit() || it == '+' }
    }
}

private const val TAG = "ContactsProvider"

@Composable
actual fun rememberContactsPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult(it) }
    return { launcher.launch(android.Manifest.permission.READ_CONTACTS) }
}
