package com.muhabbet.designsystem.modifier

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.muhabbet.designsystem.theme.MuhabbetElevation

/**
 * Press and long-press that follow the element's shape.
 *
 * A ripple is drawn by the `clickable` node's *indication*, and indication has no idea what the
 * element looks like — it fills the node's rectangular bounds. So a rounded card, a bubble or a
 * circular icon button whose `clickable` sits outside the clip flashes a hard rectangle over its
 * own corners on every tap, and holds it there for the whole of a long press (#703).
 *
 * The fix is entirely one of ordering, and the order is not obvious enough to be left to each call
 * site — 26 of the 27 `clickable`s in the app had it wrong:
 *
 * ```
 * shadow(elevation, shape)        // outside the clip, or the clip eats the shadow
 * clip(shape)                     // everything below draws inside the shape
 * background(color)               // shape already applied by the clip above
 * clickable / combinedClickable   // ripple is now bounded by the clip
 * ```
 *
 * **Why the shadow has to be first.** `Modifier.clip` is a graphics layer with `clip = true`, and a
 * clipping layer clips its whole subtree — including the drop shadow a descendant layer casts
 * outside its own bounds. Putting `clip(shape)` above a `Surface(shadowElevation = ...)` therefore
 * removes the shadow rather than rounding the ripple, which is why [shadowElevation] is a parameter
 * here instead of something the call site is left to arrange for itself.
 *
 * **Naming the shape is the point.** [RectangleShape][androidx.compose.ui.graphics.RectangleShape]
 * is a perfectly good answer for a full-bleed list row, where a rectangular ripple is the correct
 * one. What is not acceptable is not answering: an un-clipped `clickable` on a rounded element is
 * indistinguishable, in the source, from a deliberate rectangle. `verifyUi`'s `rawCombinedClickable`
 * rule enforces this for long-press, where the artefact stays on screen longest.
 *
 * **Clipping is not free of consequences.** A clip cuts anything drawn outside the node's bounds, so
 * do not reach for this on a control whose content deliberately overflows its own box — round the
 * layout first, or leave it rectangular and say why.
 *
 * @param shape the element's real visual shape — [androidx.compose.foundation.shape.CircleShape] for
 *   avatars and icon buttons, a `MaterialTheme.shapes` role for cards and bubbles,
 *   [androidx.compose.ui.graphics.RectangleShape] for a full-bleed row.
 * @param background painted inside the clip. Null leaves whatever is underneath showing, which is
 *   what a `Surface`- or `Card`-backed element wants.
 * @param shadowElevation drawn *outside* the clip, so a shadowed element keeps its shadow. Pass the
 *   value that would otherwise go to `Surface(shadowElevation = ...)`, and drop it there.
 */
fun Modifier.pressable(
    shape: Shape,
    enabled: Boolean = true,
    background: Color? = null,
    shadowElevation: Dp = MuhabbetElevation.None,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = this
    .shapedGround(shape, background, shadowElevation)
    .clickable(enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)

/**
 * [pressable] with a long press, and optionally a double tap.
 *
 * Long press is where the rectangular ripple was most obvious, because the effect stays on screen
 * until the finger lifts: a message bubble held down to open its context menu lit a hard rectangle
 * behind its rounded corners for the whole gesture.
 *
 * @see pressable for the ordering rule and for what [shape] should be.
 */
fun Modifier.longPressable(
    shape: Shape,
    enabled: Boolean = true,
    background: Color? = null,
    shadowElevation: Dp = MuhabbetElevation.None,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = this
    .shapedGround(shape, background, shadowElevation)
    .combinedClickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        onClick = onClick
    )

/**
 * The three draw-order nodes both entry points share, in the only order that works: shadow above the
 * clip, background below it. Private so the shape can never be attached without the interaction that
 * is the whole reason for attaching it.
 */
private fun Modifier.shapedGround(
    shape: Shape,
    background: Color?,
    shadowElevation: Dp
): Modifier = this
    .then(if (shadowElevation > MuhabbetElevation.None) Modifier.shadow(shadowElevation, shape) else Modifier)
    .clip(shape)
    .then(if (background != null) Modifier.background(background) else Modifier)
