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
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

actual class CameraPickerLauncher(
    private val launcher: () -> Unit
) {
    actual fun launch() = launcher()
}

@Composable
actual fun rememberCameraPickerLauncher(onResult: (PickedImage?) -> Unit): CameraPickerLauncher {
    return remember {
        CameraPickerLauncher {
            if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
                onResult(null)
                return@CameraPickerLauncher
            }

            val picker = UIImagePickerController()
            picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            picker.allowsEditing = false

            val delegate = CameraPickerDelegate(onResult)
            picker.delegate = delegate

            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            rootViewController?.presentViewController(picker, animated = true, completion = null)
        }
    }
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
            val fileName = "camera_${platform.Foundation.NSDate().timeIntervalSince1970.toLong()}.jpg"
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
