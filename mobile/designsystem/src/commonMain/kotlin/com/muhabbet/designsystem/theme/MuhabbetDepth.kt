package com.muhabbet.designsystem.theme

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * How far a surface sits above the page.
 *
 * Four levels, not the seven raw dp values [MuhabbetElevation] carries, because a caller should be
 * choosing a *role*, not a shadow radius. The dp constants stay for the Material parameters that
 * literally take a `Dp` (`shadowElevation`, `tonalElevation`); this sits on top of them.
 */
enum class MuhabbetDepth {
    /** Page content. No separation at all. */
    Flat,

    /** Cards, list surfaces, the message input bar — attached to the page but distinct from it. */
    Raised,

    /** FAB, scroll-to-bottom pill, the reaction bar. Clearly above the content it covers. */
    Floating,

    /** Dialogs, bottom sheets, dropdown menus. Above everything, usually over a scrim. */
    Overlay,
}

/**
 * Applies [level] as depth appropriate to the theme actually rendering.
 *
 * The same level is expressed three different ways, because a shadow is invisible on `#000000`:
 *
 *  - **Light** — depth is shadow, and always *two* shadows: a tight contact shadow plus a wide
 *    ambient one. Stacking them is the single biggest difference between "has a shadow" and "looks
 *    designed"; one shadow reads as a sticker. Both are tinted with the palette's darkest neutral
 *    rather than pure black, because black over a warm surface goes grey and dead.
 *  - **Dark** — depth is *luminance*. Each level steps up the `surfaceContainer*` ladder, and the
 *    two highest levels add a 1px top hairline at ~6% white: the lit edge that sells the lift.
 *    Shadows barely register here and cost fill rate, so they are omitted.
 *  - **OLED** — no shadows at all, for the same reason plus battery. Separation is a ~10% white
 *    outline, which also stops two adjacent pure-black regions merging into one void.
 *
 * Neumorphism is deliberately absent: no inner shadow on a light surface, no dual-light emboss. It
 * fails contrast, it collapses at 1.3x font scale, and it dates instantly.
 *
 * @param shape must match the shape the surface is actually clipped to, or the shadow will not
 *   follow its corners.
 */
@Composable
fun Modifier.depth(level: MuhabbetDepth, shape: Shape): Modifier {
    if (level == MuhabbetDepth.Flat) return this
    return when (LocalThemeMode.current) {
        ResolvedThemeMode.Light -> lightDepth(level, shape)
        ResolvedThemeMode.Dark -> darkDepth(level, shape)
        ResolvedThemeMode.Oled -> oledDepth(level, shape)
    }
}

/** Two stacked shadows: contact (tight, defines the edge) then ambient (wide, defines the lift). */
private fun Modifier.lightDepth(level: MuhabbetDepth, shape: Shape): Modifier {
    // Ambient first, contact painted over it — the wide one establishes the lift, the tight one
    // keeps the edge from looking like it is floating free of the page.
    val (ambient, contact) = when (level) {
        MuhabbetDepth.Flat -> return this
        MuhabbetDepth.Raised ->
            ShadowSpec(12.dp, 4.dp, 0.06f) to ShadowSpec(2.dp, 1.dp, 0.06f)
        MuhabbetDepth.Floating ->
            ShadowSpec(24.dp, 8.dp, 0.10f) to ShadowSpec(3.dp, 1.dp, 0.08f)
        MuhabbetDepth.Overlay ->
            ShadowSpec(40.dp, 16.dp, 0.14f) to ShadowSpec(4.dp, 2.dp, 0.10f)
    }
    return this
        .dropShadow(shape, ambient.toShadow())
        .dropShadow(shape, contact.toShadow())
}

/** Depth by luminance. The container colour is applied by the caller; this adds the lit edge. */
@Composable
private fun Modifier.darkDepth(level: MuhabbetDepth, shape: Shape): Modifier = when (level) {
    MuhabbetDepth.Flat, MuhabbetDepth.Raised -> this
    MuhabbetDepth.Floating -> border(HairlineWidth, Color.White.copy(alpha = 0.06f), shape)
    MuhabbetDepth.Overlay -> border(HairlineWidth, Color.White.copy(alpha = 0.08f), shape)
}

/** No shadows on true black; an outline is the only thing that separates one void from another. */
@Composable
private fun Modifier.oledDepth(level: MuhabbetDepth, shape: Shape): Modifier = when (level) {
    MuhabbetDepth.Flat -> this
    MuhabbetDepth.Raised -> border(HairlineWidth, Color.White.copy(alpha = 0.07f), shape)
    MuhabbetDepth.Floating -> border(HairlineWidth, Color.White.copy(alpha = 0.10f), shape)
    MuhabbetDepth.Overlay -> border(HairlineWidth, Color.White.copy(alpha = 0.12f), shape)
}

private val HairlineWidth: Dp = 1.dp

/**
 * A shadow tinted with the palette's deepest neutral rather than pure black.
 *
 * Pure black over a warm surface desaturates it — the shadow reads as dirty grey instead of as
 * shade. Tinting costs nothing and is most of why one shadow looks considered and another does not.
 */
private data class ShadowSpec(val radius: Dp, val y: Dp, val alpha: Float) {
    fun toShadow(): Shadow = Shadow(
        radius = radius,
        color = ShadowTint,
        spread = 0.dp,
        offset = DpOffset(0.dp, y),
        alpha = alpha
    )
}

/** Reads from the palette so a brand change retints every shadow in the app from one place. */
private val ShadowTint = MuhabbetPalette.DarkBg

/**
 * The container colour a [MuhabbetDepth] level should paint itself.
 *
 * Pairs with [depth]: the modifier draws the separation, this supplies the fill. Kept together so a
 * caller cannot accidentally put a Floating shadow on a Flat-coloured surface.
 */
@Composable
fun MuhabbetDepth.containerColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        MuhabbetDepth.Flat -> scheme.surface
        MuhabbetDepth.Raised -> scheme.surfaceContainerLow
        MuhabbetDepth.Floating -> scheme.surfaceContainer
        MuhabbetDepth.Overlay -> scheme.surfaceContainerHigh
    }
}
