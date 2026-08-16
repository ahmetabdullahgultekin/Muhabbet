package com.muhabbet.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The pure orientation-mapping half of #408 ("photos upload rotated"): given the raw EXIF
 * `Orientation` tag value, what rotation + mirroring turns the sensor-order pixel buffer upright.
 * No Android/iOS runtime needed — this is exactly the part that was easy to get subtly wrong with a
 * blanket 90° rotation, so it is checked against all 8 EXIF cases individually, including the two
 * mirrored ones.
 */
class ImageOrientationTest {

    @Test
    fun normal_and_undefined_need_no_correction() {
        assertEquals(ImageOrientationTransform.IDENTITY, transformForExifOrientation(ExifOrientation.NORMAL))
        assertEquals(ImageOrientationTransform.IDENTITY, transformForExifOrientation(ExifOrientation.UNDEFINED))
    }

    @Test
    fun unrecognized_values_fall_back_to_identity_rather_than_guessing() {
        assertEquals(ImageOrientationTransform.IDENTITY, transformForExifOrientation(99))
    }

    @Test
    fun rotate_90_is_a_plain_90_degree_rotation() {
        val transform = transformForExifOrientation(ExifOrientation.ROTATE_90)
        assertEquals(90, transform.rotationDegrees)
        assertEquals(false, transform.flipHorizontal)
    }

    @Test
    fun rotate_180_is_a_plain_180_degree_rotation() {
        val transform = transformForExifOrientation(ExifOrientation.ROTATE_180)
        assertEquals(180, transform.rotationDegrees)
        assertEquals(false, transform.flipHorizontal)
    }

    @Test
    fun rotate_270_is_a_plain_270_degree_rotation() {
        val transform = transformForExifOrientation(ExifOrientation.ROTATE_270)
        assertEquals(270, transform.rotationDegrees)
        assertEquals(false, transform.flipHorizontal)
    }

    @Test
    fun flip_horizontal_mirrors_without_rotating() {
        val transform = transformForExifOrientation(ExifOrientation.FLIP_HORIZONTAL)
        assertEquals(0, transform.rotationDegrees)
        assertEquals(true, transform.flipHorizontal)
    }

    @Test
    fun flip_vertical_is_a_180_degree_rotation_plus_mirror() {
        // A vertical flip is not its own primitive here: rotating 180 degrees and then mirroring
        // horizontally produces the same pixels, so this must NOT collapse to a plain 180 rotation
        // (that would be orientation 3, not 4) or to a plain mirror (that would be orientation 2).
        val transform = transformForExifOrientation(ExifOrientation.FLIP_VERTICAL)
        assertEquals(180, transform.rotationDegrees)
        assertEquals(true, transform.flipHorizontal)
    }

    @Test
    fun transpose_is_a_90_degree_rotation_plus_mirror() {
        val transform = transformForExifOrientation(ExifOrientation.TRANSPOSE)
        assertEquals(90, transform.rotationDegrees)
        assertEquals(true, transform.flipHorizontal)
    }

    @Test
    fun transverse_is_a_270_degree_rotation_plus_mirror() {
        val transform = transformForExifOrientation(ExifOrientation.TRANSVERSE)
        assertEquals(270, transform.rotationDegrees)
        assertEquals(true, transform.flipHorizontal)
    }

    @Test
    fun all_eight_orientations_map_to_distinct_transforms() {
        // Guards against a blanket "just rotate 90" style fix: every one of the 8 real EXIF values
        // must produce its own transform, not collapse onto a handful of common cases.
        val orientations = listOf(
            ExifOrientation.NORMAL,
            ExifOrientation.FLIP_HORIZONTAL,
            ExifOrientation.ROTATE_180,
            ExifOrientation.FLIP_VERTICAL,
            ExifOrientation.TRANSPOSE,
            ExifOrientation.ROTATE_90,
            ExifOrientation.TRANSVERSE,
            ExifOrientation.ROTATE_270
        )
        val transforms = orientations.map { transformForExifOrientation(it) }
        assertEquals(orientations.size, transforms.toSet().size)
    }
}
