package com.muhabbet.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.muhabbet.designsystem.theme.MuhabbetGradients
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes

/**
 * The Muhabbet mark: an M inside a copper ring.
 *
 * Replaces a flat `Surface(80.dp)` filled with the theme's primary colour and a system-font letter,
 * which is what the auth screen opened with — the first thing a new user sees, and the only place
 * the product got to introduce itself.
 *
 * The letter is a **logogram, not copy**. It is part of the mark and is never translated, which is
 * why it is a literal here rather than a caller-supplied string; the module's no-strings rule is
 * about user-visible text, and this is artwork. It is hidden from screen readers for the same
 * reason — an accessibility service reading out "M" is noise, so the caller labels the mark if it
 * needs a label.
 *
 * Everything is drawn: a border brush and a glyph, no raster asset. The APK-bloat rule in the design
 * doc says illustrations are `Canvas` or vector, never bitmaps, and a logo is the easiest place to
 * break that rule by accident.
 */
@Composable
fun MuhabbetBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = MuhabbetSizes.AvatarXXLarge
) {
    // Entry is driven from a one-shot flag rather than from `remember { 1f }`, because a spring
    // needs two values to travel between. It settles rather than snapping, so an interrupted
    // recomposition mid-entry does not jump.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else EnterScale,
        animationSpec = MuhabbetMotion.spatialDefault(),
        label = "brandMarkEnter"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(width = MuhabbetSizes.BorderActive, brush = MuhabbetGradients.brandRing, shape = CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "M",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private const val EnterScale = 0.8f
