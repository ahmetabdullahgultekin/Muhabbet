package com.muhabbet.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner-radius tokens.
 *
 * The first five mirror the Material 3 shape roles one-for-one. Inside a composable prefer
 * `MaterialTheme.shapes.small` and friends — they resolve to exactly these values via
 * [MuhabbetShapes]. Reach for the [Dp] constants directly only where a `Shapes` role is not
 * available (border widths, manual `RoundedCornerShape` corner-by-corner construction).
 *
 * The remaining tokens are radii the messaging UI needs that have no Material role.
 */
object MuhabbetCorners {
    val ExtraSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 16.dp
    val ExtraLarge: Dp = 28.dp

    /** Hairline strips: status progress ticks, the accent bar on a quoted reply. */
    val Hairline: Dp = 2.dp

    /** Small inline preview images, e.g. the icon on a link-preview card. */
    val Thumbnail: Dp = 6.dp

    /** Chat message bubbles and bubble-styled cards. */
    val Bubble: Dp = 18.dp

    /** Fully-rounded floating surfaces: the reaction bar, the message input field. */
    val Pill: Dp = 24.dp
}

/**
 * The Material 3 shape scale, passed to `MaterialTheme` by [MuhabbetTheme].
 *
 * Declared explicitly rather than inherited so the radii have one home; the values currently
 * match the Material baseline.
 */
val MuhabbetShapes = Shapes(
    extraSmall = RoundedCornerShape(MuhabbetCorners.ExtraSmall),
    small = RoundedCornerShape(MuhabbetCorners.Small),
    medium = RoundedCornerShape(MuhabbetCorners.Medium),
    large = RoundedCornerShape(MuhabbetCorners.Large),
    extraLarge = RoundedCornerShape(MuhabbetCorners.ExtraLarge)
)
