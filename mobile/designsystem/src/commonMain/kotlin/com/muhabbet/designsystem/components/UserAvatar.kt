package com.muhabbet.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.muhabbet.designsystem.util.firstGrapheme
import com.muhabbet.designsystem.theme.MuhabbetPalette
import com.muhabbet.designsystem.theme.MuhabbetGradients
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background

/**
 * Circular avatar: remote image when there is one, otherwise a group glyph or the first grapheme of
 * the name.
 *
 * @param contentDescription what a screen reader announces. Defaults to [displayName], which is
 *   what the photo branch has always used. Group avatars pass an explicit label instead, because a
 *   generic group glyph carrying a person-shaped name reads wrong. Supplied by the caller rather
 *   than read from resources here: this component lives in a module with no string resources.
 */
@Composable
fun UserAvatar(
    avatarUrl: String?,
    displayName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    isGroup: Boolean = false,
    contentDescription: String? = null
) {
    if (avatarUrl != null) {
        Surface(
            modifier = modifier.size(size).clip(CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = contentDescription ?: displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    // No photo: a gradient seeded from the name rather than one shared container colour.
    //
    // This is the cheapest high-impact change in the whole system. A contact list is mostly people
    // without photos, and as identical grey circles it reads as unfinished no matter how good
    // everything around it is. Because the seed is the name, the same person is the same colour on
    // every device and after every reinstall — it reads as identity, not as decoration.
    //
    // Groups seed from the group's name for the same reason; a group is a thing with a name too.
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MuhabbetGradients.avatarFallback(displayName)),
        contentAlignment = Alignment.Center
    ) {
        if (isGroup) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = contentDescription ?: displayName,
                modifier = Modifier.size(size * GlyphRatio),
                tint = MuhabbetPalette.PaperOnDark
            )
        } else {
            // Always the light ink, never `onPrimaryContainer`: the fill is now one of six copper
            // gradients rather than a theme role, and every one of them is dark enough to carry it.
            Text(
                text = firstGrapheme(displayName),
                fontSize = initialFontSize(size),
                fontWeight = FontWeight.SemiBold,
                color = MuhabbetPalette.PaperOnDark
            )
        }
    }
}

/**
 * The initial scales with the circle rather than tracking the type scale: this is a glyph sized as
 * artwork, and a 96dp avatar with 14sp in the middle of it looks like a mistake.
 */
private fun initialFontSize(size: Dp): TextUnit = when {
    size >= 96.dp -> 36.sp
    size >= 80.dp -> 28.sp
    size >= 48.dp -> 18.sp
    else -> 14.sp
}

private const val GlyphRatio = 0.5f
