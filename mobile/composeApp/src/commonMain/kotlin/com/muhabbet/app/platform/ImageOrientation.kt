package com.muhabbet.app.platform

/**
 * Raw values of the EXIF `Orientation` tag, per the EXIF 2.3 spec. These are the same integers
 * `androidx.exifinterface.media.ExifInterface.ORIENTATION_*` and Apple's `CGImagePropertyOrientation`
 * use, so a platform actual can pass what it reads straight into [transformForExifOrientation]
 * without importing a platform graphics API here.
 */
object ExifOrientation {
    const val UNDEFINED = 0
    const val NORMAL = 1
    const val FLIP_HORIZONTAL = 2
    const val ROTATE_180 = 3
    const val FLIP_VERTICAL = 4
    const val TRANSPOSE = 5
    const val ROTATE_90 = 6
    const val TRANSVERSE = 7
    const val ROTATE_270 = 8
}

/**
 * The rotation + mirroring needed to turn a decoded pixel buffer (which EXIF-unaware decoders hand
 * back exactly as the sensor wrote it) into the upright image the photo was actually taken as.
 *
 * [rotationDegrees] is applied first (clockwise), then [flipHorizontal] second — that order matches
 * how the four mirrored EXIF orientations (5–8) are defined, so a platform actual only ever needs
 * two operations, never a bespoke matrix per case.
 */
data class ImageOrientationTransform(
    val rotationDegrees: Int,
    val flipHorizontal: Boolean
) {
    companion object {
        val IDENTITY = ImageOrientationTransform(rotationDegrees = 0, flipHorizontal = false)
    }
}

/**
 * Maps a raw EXIF `Orientation` value to the transform that corrects it. Pure and platform-agnostic
 * on purpose: it is the part of "read EXIF, rotate, re-encode" that a unit test can actually check
 * without an Android or iOS runtime, and it is the part most likely to be wrong (all 8 EXIF cases,
 * two of them mirrored, are easy to get subtly wrong with a single blanket rotation).
 */
fun transformForExifOrientation(orientation: Int): ImageOrientationTransform = when (orientation) {
    ExifOrientation.FLIP_HORIZONTAL -> ImageOrientationTransform(rotationDegrees = 0, flipHorizontal = true)
    ExifOrientation.ROTATE_180 -> ImageOrientationTransform(rotationDegrees = 180, flipHorizontal = false)
    ExifOrientation.FLIP_VERTICAL -> ImageOrientationTransform(rotationDegrees = 180, flipHorizontal = true)
    ExifOrientation.TRANSPOSE -> ImageOrientationTransform(rotationDegrees = 90, flipHorizontal = true)
    ExifOrientation.ROTATE_90 -> ImageOrientationTransform(rotationDegrees = 90, flipHorizontal = false)
    ExifOrientation.TRANSVERSE -> ImageOrientationTransform(rotationDegrees = 270, flipHorizontal = true)
    ExifOrientation.ROTATE_270 -> ImageOrientationTransform(rotationDegrees = 270, flipHorizontal = false)
    // NORMAL and UNDEFINED (and anything unrecognized) need no correction.
    else -> ImageOrientationTransform.IDENTITY
}
