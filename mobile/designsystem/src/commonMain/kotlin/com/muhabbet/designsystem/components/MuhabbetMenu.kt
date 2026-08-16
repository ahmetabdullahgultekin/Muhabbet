package com.muhabbet.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetDepth
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.containerColor
import com.muhabbet.designsystem.theme.depth

/**
 * The one dropdown menu.
 *
 * There was no menu or picker component at all (#433). Eighteen `DropdownMenuItem` calls across
 * four files used the bare Material 3 `DropdownMenu` directly — the platform's own `extraSmall`
 * corner, its flat tonal surface, and every call site sizing its own icon by hand. The attachment
 * menu and the eight-item message long-press menu are two of the most-used surfaces in the app and
 * rendered as the same generic context menu any other Android app ships.
 *
 * [MuhabbetMenu] gives the popup its own shape and the same [MuhabbetDepth.Overlay] surface a
 * dialog or bottom sheet gets — two stacked shadows in light, a lit hairline in dark, an outline in
 * OLED — instead of M3's flat `shadowElevation`. That treatment is drawn by [depth] on the
 * `modifier`, the identical idiom [MuhabbetBottomSheet]'s siblings already use on a `Surface`;
 * `shadowElevation` is zeroed out here so the two do not stack into a second, undesigned shadow.
 *
 * One thing this deliberately does not attempt: the cross-platform `DropdownMenu` in this Compose
 * Multiplatform release exposes `shape`, `containerColor`, `tonalElevation`, `shadowElevation` and
 * `border`, but no transition override — there is no `MuhabbetMotion` seam to hook the mount/dismiss
 * animation into without reimplementing the anchor-relative popup positioning from scratch. That is
 * a materially bigger, riskier change for a surface this host cannot render, so the appear/dismiss
 * motion stays the platform default — the same call [MuhabbetBottomSheet] already made for
 * `ModalBottomSheet`, which does not override its slide-in either.
 */
@Composable
fun MuhabbetMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(MuhabbetCorners.Medium)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.depth(MuhabbetDepth.Overlay, shape),
        shape = shape,
        containerColor = MuhabbetDepth.Overlay.containerColor(),
        // depth() above draws the design system's own shadow/hairline; M3's own shadowElevation
        // would stack a second, undesigned shadow underneath it.
        shadowElevation = MuhabbetElevation.None,
        content = content
    )
}

/**
 * One row in a [MuhabbetMenu].
 *
 * Centralises what all eighteen call sites repeated by hand: the icon's size
 * (`MuhabbetSizes.IconMedium`) and its colour. Several sites also passed the row's own label back
 * in as the icon's `contentDescription` — a screen reader announcing the same row twice — so the
 * icon here is always decorative; the label is what gets announced.
 *
 * @param destructive routes both the label and the icon through the theme's semantic `error` role
 *   instead of a call site reading `MaterialTheme.colorScheme.error` (or worse, a literal red)
 *   directly — one place to change if the destructive tone ever moves.
 * @param iconTint an explicit override for the rare row whose icon carries its own state rather
 *   than the row's action — the star item's filled-vs-outline icon is `tertiary` only while
 *   starred, which `destructive` cannot express.
 */
@Composable
fun MuhabbetMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    iconTint: Color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified
            )
        },
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(MuhabbetSizes.IconMedium),
                    tint = iconTint
                )
            }
        }
    )
}
