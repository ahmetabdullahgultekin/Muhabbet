package com.muhabbet.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetDepth
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.containerColor
import com.muhabbet.designsystem.theme.depth

/**
 * A grouped surface — a settings group, a link preview, a storage summary — that sits at a named
 * [MuhabbetDepth] rather than at whatever `CardDefaults` resolves to.
 *
 * The five raw `Card(` call sites (#847) each took M3's default elevation, which on the light theme
 * is a single tonal step and on OLED is an invisible black-on-black edge. [depth] expresses the same
 * level three different ways — a two-layer shadow in light, a luminance step and lit hairline in
 * dark, an outline on OLED — and the container colour comes from the same level, so a card cannot
 * cast a Floating shadow from a Flat-coloured surface.
 *
 * **Clickable or not, decided by [onClick].** A card with an action presses back on the same
 * spatial spring every pressable control uses, its ripple is clipped to its corners (the shadow sits
 * outside the clip so it survives — see [com.muhabbet.designsystem.modifier.pressable]), and it is a
 * button to accessibility. A card without one is not focusable at all.
 *
 * @param enabled only meaningful with [onClick]; a disabled card fades and stops accepting taps.
 */
@Composable
fun MuhabbetCard(
    modifier: Modifier = Modifier,
    depth: MuhabbetDepth = MuhabbetDepth.Raised,
    shape: Shape = RoundedCornerShape(MuhabbetCorners.Medium),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(MuhabbetSpacing.Large),
    content: @Composable ColumnScope.() -> Unit
) {
    val container = depth.containerColor()
    val press = if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) CardPressedScale else 1f,
            animationSpec = MuhabbetMotion.spatialFast(),
            label = "cardPress"
        )
        // The same shadow → clip → background → clickable order Modifier.pressable owns, written out
        // here on purpose: pressable lays down a plain shadowElevation, whereas a card's ground is a
        // whole depth level (shadow in light, tonal step in dark, outline on OLED) via
        // Modifier.depth, and the press scale has to sit outside all of it. Folding MuhabbetDepth
        // into pressable would make every pressable carry a depth parameter only cards use.
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .depth(depth, shape)
            .clip(shape)
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick
            )
    } else {
        Modifier
            .depth(depth, shape)
            .clip(shape)
            .background(container)
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = modifier
                .then(press)
                .alpha(if (enabled || onClick == null) 1f else DisabledCardAlpha)
                .padding(contentPadding),
            content = content
        )
    }
}

/** Gentler than a button's 0.97: a card is a larger surface, and the same ratio reads as a lurch. */
private const val CardPressedScale = 0.985f

private const val DisabledCardAlpha = 0.38f
