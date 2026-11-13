package com.muhabbet.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.muhabbet.designsystem.theme.LocalReducedMotion
import com.muhabbet.designsystem.theme.MuhabbetGradients
import com.muhabbet.designsystem.theme.MuhabbetIcons
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetPalette
import com.muhabbet.designsystem.util.avatarInitial

/**
 * Circular avatar, built in layers so it is never an empty circle (#838).
 *
 *  1. **Base, always drawn:** a copper gradient keyed on [seed], with the group glyph, the name's
 *     initial ([avatarInitial]), or a person glyph when the name has no usable initial (a phone
 *     number, punctuation).
 *  2. **Photo, on top, only once it has loaded.** While [avatarUrl] is loading, after it fails, and
 *     after a presigned URL has expired (#296), the base is what shows — the person is still
 *     recognisable. The old component made these two mutually exclusive branches, so any URL at all
 *     meant a grey disc until the image arrived, and forever if it never did.
 *
 * The photo fades in on an effects spring, and appears at once under reduced motion.
 *
 * The initial is artwork, sized as a fraction of the circle in dp and converted so the user's font
 * scale does not apply (#840): at 2.0× the old sp-sized glyph overflowed a 40dp circle, and the
 * circle itself does not grow with the font. The name it stands for is announced in full.
 *
 * @param seed a stable identity for the fallback colour — the user or conversation **id**. Defaults
 *   to [displayName] only so legacy call sites keep compiling; a name is presentation, and keying
 *   colour on it repaints a person every time they are renamed.
 * @param contentDescription what a screen reader announces. Defaults to [displayName]. Group avatars
 *   pass an explicit label instead, because a generic group glyph carrying a person-shaped name reads
 *   wrong. Supplied by the caller rather than read from resources here: this module has no strings.
 */
@Composable
fun UserAvatar(
    avatarUrl: String?,
    displayName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    isGroup: Boolean = false,
    contentDescription: String? = null,
    seed: String = displayName
) {
    val description = contentDescription ?: displayName
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MuhabbetGradients.avatarFallback(seed))
            // One node with one name, whichever layer is visible. The image and the glyph below are
            // decoration of this node, not separate things to swipe through.
            .clearAndSetSemantics {
                this.contentDescription = description
                role = Role.Image
            },
        contentAlignment = Alignment.Center
    ) {
        AvatarGlyph(displayName = displayName, isGroup = isGroup, size = size)
        if (avatarUrl != null) AvatarPhoto(avatarUrl)
    }
}

@Composable
private fun AvatarGlyph(displayName: String, isGroup: Boolean, size: Dp) {
    // Always the light ink, never `onPrimaryContainer`: the fill is one of six copper gradients
    // rather than a theme role, and every one of them is dark enough to carry it.
    val initial = if (isGroup) null else remember(displayName) { avatarInitial(displayName) }
    if (initial == null) {
        Icon(
            imageVector = if (isGroup) MuhabbetIcons.Group else MuhabbetIcons.Person,
            contentDescription = null,
            modifier = Modifier.size(size * GlyphRatio),
            tint = MuhabbetPalette.PaperOnDark
        )
    } else {
        Text(
            text = initial,
            fontSize = initialFontSize(size),
            fontWeight = FontWeight.SemiBold,
            color = MuhabbetPalette.PaperOnDark,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun AvatarPhoto(avatarUrl: String) {
    var loaded by remember(avatarUrl) { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    val alpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = if (reduced) snap() else MuhabbetMotion.effectsDefault(),
        label = "avatarPhoto"
    )
    AsyncImage(
        model = avatarUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        // Loading and Error both leave `loaded` false, so the base layer stays visible; only a
        // decoded image covers it.
        onState = { loaded = it is AsyncImagePainter.State.Success },
        modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }
    )
}

/**
 * The initial's size: a fixed fraction of the circle, in dp, expressed as sp with the font scale
 * divided back out. `Dp.toSp()` does exactly that — it returns the sp that renders at this many dp
 * under the current font scale — so the glyph is the same size at 1.0× and 2.0×.
 */
@Composable
private fun initialFontSize(size: Dp): TextUnit = with(LocalDensity.current) { (size * InitialRatio).toSp() }

/** 36sp in a 96dp circle was the hand-tuned original; the same proportion now applies everywhere. */
private const val InitialRatio = 0.375f
private const val GlyphRatio = 0.5f
