package com.muhabbet.app.platform

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.muhabbet.app.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "MediaGallerySaver"

/** The album received media lands in, under `Pictures/` or `Movies/`. */
private const val ALBUM = "Muhabbet"

/**
 * `MediaStore` on API 29+, a legacy file write plus a scan broadcast below that.
 *
 * The split is not a nicety: `Environment.getExternalStoragePublicDirectory` is unwritable from
 * API 29 onwards under scoped storage, and `MediaStore`'s `RELATIVE_PATH` / `IS_PENDING` columns do
 * not exist before it. Neither branch can serve both, so both are here — `minSdk` is 26.
 *
 * The API ≤ 28 branch is the only reason this app declares `WRITE_EXTERNAL_STORAGE` at all, and the
 * manifest caps it at `maxSdkVersion="28"` so no modern device is asked for storage access it does
 * not need. On API 29+ the insert is permissionless because the row belongs to this app.
 */
actual class MediaGallerySaver(private val context: Context) {

    /** Always true on Android: every supported API level has one of the two paths below. */
    actual fun isSupported(): Boolean = true

    actual suspend fun save(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): GallerySaveResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(bytes, fileName, mimeType)
        } else {
            saveViaLegacyFile(bytes, fileName, mimeType)
        }
    }

    /**
     * API 29+. The row is created `IS_PENDING = 1` so the gallery never shows a half-written file,
     * and cleared once the bytes are down. If the copy throws, the pending row is deleted rather
     * than left behind — a stuck pending row is invisible to the user and impossible for them to
     * clean up.
     */
    private fun saveViaMediaStore(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): GallerySaveResult {
        val isVideo = mimeType.startsWith("video/")
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativeDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeDir/$ALBUM")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        var uri: Uri? = null
        return try {
            uri = context.contentResolver.insert(collection, values)
                ?: return GallerySaveResult.FAILED
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: return GallerySaveResult.FAILED
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            GallerySaveResult.SAVED
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore write failed for $fileName", e)
            uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            GallerySaveResult.FAILED
        }
    }

    /**
     * API 26–28. Writes into the public Pictures/Movies directory and then tells the media scanner,
     * because on these versions nothing indexes a file the app wrote on its own — without the scan
     * the photo is on disk and absent from every gallery, which reads exactly like the feature not
     * working.
     */
    @Suppress("DEPRECATION")
    private fun saveViaLegacyFile(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): GallerySaveResult {
        if (!hasLegacyWritePermission()) return GallerySaveResult.PERMISSION_REQUIRED

        val isVideo = mimeType.startsWith("video/")
        val root = Environment.getExternalStoragePublicDirectory(
            if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        )
        val album = File(root, ALBUM)
        return try {
            if (!album.exists() && !album.mkdirs()) return GallerySaveResult.FAILED
            val target = File(album, fileName)
            target.writeBytes(bytes)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mimeType), null)
            GallerySaveResult.SAVED
        } catch (e: Exception) {
            Log.e(TAG, "Legacy gallery write failed for $fileName", e)
            GallerySaveResult.FAILED
        }
    }

    private fun hasLegacyWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
}

@Composable
actual fun rememberMediaGallerySaver(): MediaGallerySaver {
    val context = LocalContext.current
    return remember(context) { MediaGallerySaver(context.applicationContext) }
}

/**
 * On API 29+ there is nothing to ask for, so the callback fires `true` without a dialog. Launching
 * `WRITE_EXTERNAL_STORAGE` there would not merely be pointless: the permission is capped at
 * `maxSdkVersion="28"` in the manifest, so the request would be denied instantly and the switch
 * would refuse to turn on for every modern device.
 */
@Composable
actual fun rememberGallerySavePermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult(it) }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            onResult(true)
        } else {
            launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}
