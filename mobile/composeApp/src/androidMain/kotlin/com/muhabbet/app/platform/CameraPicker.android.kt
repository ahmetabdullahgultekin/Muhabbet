package com.muhabbet.app.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.muhabbet.app.util.Log
import java.io.File

private const val TAG = "CameraPicker"

actual class CameraPickerLauncher(
    private val launcher: () -> Unit
) {
    actual fun launch() = launcher()
}

/**
 * Requests CAMERA at the moment of the tap, because the app **declares** the permission in its
 * manifest. Android's rule is that an app which declares CAMERA must also hold it before starting
 * `ACTION_IMAGE_CAPTURE` — which is what `TakePicture` launches — so without the grant the launch
 * threw `SecurityException` and took the process down (#399). The same defect shape as #372, where
 * RECORD_AUDIO is declared and never requested.
 *
 * Ruled out while diagnosing, so nobody re-investigates it: the FileProvider is correct. The
 * manifest authority `${applicationId}.fileprovider` matches the one built here, and
 * `res/xml/file_paths.xml` declares `<cache-path name="camera_images" path="." />`, which covers
 * `cacheDir/camera_*.jpg`. This was never the usual "Failed to find configured root" crash, and it
 * has nothing to do with Google Play services — image capture needs none.
 */
@Composable
actual fun rememberCameraPickerLauncher(onResult: (PickedImage?) -> Unit): CameraPickerLauncher {
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = photoUri
        if (success && uri != null) {
            onResult(readCameraImage(context, uri))
        } else {
            onResult(null)
        }
    }

    fun capture() {
        val uri = createTempImageUri(context)
        photoUri = uri
        captureLauncher.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            capture()
        } else {
            // Same shape as a cancelled capture, which is the contract this callback already has.
            // Logged so a denial is distinguishable from a cancel when someone reports "the camera
            // button does nothing".
            Log.e(TAG, "Camera permission denied; capture not started")
            onResult(null)
        }
    }

    return remember(captureLauncher, permissionLauncher) {
        CameraPickerLauncher {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) capture() else permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

private fun createTempImageUri(context: Context): Uri {
    val imageFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
}

private fun readCameraImage(context: Context, uri: Uri): PickedImage? {
    return try {
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return null
        val fileName = "camera_${System.currentTimeMillis()}.jpg"
        PickedImage(bytes = bytes, mimeType = "image/jpeg", fileName = fileName)
    } catch (e: Exception) {
        // Used to be swallowed silently, which made a capture that succeeded but could not be read
        // indistinguishable from the user pressing back.
        Log.e(TAG, "Captured photo could not be read back", e)
        null
    }
}
