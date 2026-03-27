package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImage
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.Foundation.NSData
import kotlin.time.Clock
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

actual class CameraPickerLauncher(
    private val onResult: (PickedImage?) -> Unit
) {
    // Strong reference: kept alive for the full lifetime of the picker session.
    // UIImagePickerController.delegate is a weak reference in ObjC, so we must
    // retain the delegate ourselves to prevent premature deallocation.
    private var activeDelegate: CameraPickerDelegate? = null

    actual fun launch() {
        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            onResult(null)
            return
        }

        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.allowsEditing = false

        val delegate = CameraPickerDelegate {
            activeDelegate = null  // release after use
            onResult(it)
        }
        activeDelegate = delegate  // retain strongly
        picker.delegate = delegate

        val rootViewController = findKeyWindow()?.rootViewController
        rootViewController?.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun rememberCameraPickerLauncher(onResult: (PickedImage?) -> Unit): CameraPickerLauncher {
    return remember { CameraPickerLauncher(onResult) }
}

/**
 * Returns the key window's root view controller using the scene-based API
 * (iOS 13+). Falls back to the legacy `keyWindow` for compatibility.
 */
private fun findKeyWindow(): UIWindow? {
    val connectedScenes = UIApplication.sharedApplication.connectedScenes
    for (scene in connectedScenes) {
        val windowScene = scene as? UIWindowScene ?: continue
        val keyWindow = windowScene.windows.firstOrNull { (it as UIWindow).isKeyWindow }
        if (keyWindow != null) return keyWindow as UIWindow
    }
    @Suppress("DEPRECATION")
    return UIApplication.sharedApplication.keyWindow
}

@OptIn(ExperimentalForeignApi::class)
private class CameraPickerDelegate(
    private val onResult: (PickedImage?) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        picker.dismissViewControllerAnimated(true) {
            val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
            if (image == null) {
                onResult(null)
                return@dismissViewControllerAnimated
            }

            val data = UIImageJPEGRepresentation(image, 0.9) // 90% quality
            if (data == null) {
                onResult(null)
                return@dismissViewControllerAnimated
            }

            val bytes = data.toByteArray()
            val fileName = "camera_${Clock.System.now().toEpochMilliseconds()}.jpg"
            onResult(PickedImage(bytes = bytes, mimeType = "image/jpeg", fileName = fileName))
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) {
            onResult(null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = this.length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return bytes
}
