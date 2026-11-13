package com.muhabbet.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.muhabbet.designsystem.theme.MuhabbetIcons
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes

/** What an image shows while it has nothing to show yet. */
enum class MuhabbetImagePlaceholder {
    /** The skeleton sweep — for content the user is waiting on: a media bubble, a gallery cell. */
    Shimmer,

    /**
     * A flat container colour — for many small images at once (a GIF grid, a sticker tray), where a
     * dozen sweeping tiles read as noise rather than as "loading".
     */
    Solid,
}

/**
 * A remote image with a placeholder, an error fallback and a crossfade between them.
 *
 * Twelve raw `AsyncImage` call sites (#847) had none of the three: a slow image was a blank hole, a
 * failed one stayed a blank hole forever, and a loaded one popped in. Each of those is a different
 * message to the user — "coming", "not coming", "here" — and all three looked the same.
 *
 * - **Loading** paints [placeholder] inside [shape].
 * - **Failed** (including a null [model]) paints the same ground with [fallbackIcon] on it, so a
 *   broken image is visibly broken rather than visibly empty.
 * - **Loaded** fades the image in over the placeholder on an effects spring — never overshooting
 *   into a flash. [crossfade] = false snaps it, for images that are swapped rapidly (a pager).
 *
 * Loads through Coil's singleton, which the app configures once with
 * [com.muhabbet.designsystem.image.InstallMuhabbetImageLoader].
 *
 * @param contentDescription what TalkBack says; the node reports `Role.Image`. Null makes the image
 *   decorative and removes it from the accessibility tree — correct when an adjacent label already
 *   says what it is.
 */
@Composable
fun MuhabbetImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: MuhabbetImagePlaceholder = MuhabbetImagePlaceholder.Shimmer,
    crossfade: Boolean = true,
    fallbackIcon: ImageVector = MuhabbetIcons.Image
) {
    if (model == null) {
        MuhabbetImageFrame(
            phase = MuhabbetImagePhase.Failed,
            painter = null,
            contentDescription = contentDescription,
            modifier = modifier,
            shape = shape,
            contentScale = contentScale,
            placeholder = placeholder,
            crossfade = crossfade,
            fallbackIcon = fallbackIcon
        )
        return
    }

    val context = LocalPlatformContext.current
    // The component owns the fade (over its own placeholder), so Coil's is switched off for this
    // request: the loader's default crossfade exists for raw AsyncImage call sites only.
    val request = remember(model, context) {
        ImageRequest.Builder(context).data(model).crossfade(false).build()
    }
    val painter = rememberAsyncImagePainter(model = request, contentScale = contentScale)
    val state by painter.state.collectAsState()
    MuhabbetImageFrame(
        phase = state.toPhase(),
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        shape = shape,
        contentScale = contentScale,
        placeholder = placeholder,
        crossfade = crossfade,
        fallbackIcon = fallbackIcon
    )
}

/** The three things an image can be doing, from the user's side. */
internal enum class MuhabbetImagePhase { Loading, Failed, Loaded }

internal fun AsyncImagePainter.State.toPhase(): MuhabbetImagePhase = when (this) {
    is AsyncImagePainter.State.Success -> MuhabbetImagePhase.Loaded
    is AsyncImagePainter.State.Error -> MuhabbetImagePhase.Failed
    is AsyncImagePainter.State.Loading, AsyncImagePainter.State.Empty -> MuhabbetImagePhase.Loading
}

/**
 * The drawing half of [MuhabbetImage], separated from the loading half so every phase can be
 * rendered deterministically in a golden without a network.
 */
@Composable
internal fun MuhabbetImageFrame(
    phase: MuhabbetImagePhase,
    painter: Painter?,
    contentDescription: String?,
    modifier: Modifier,
    shape: Shape,
    contentScale: ContentScale,
    placeholder: MuhabbetImagePlaceholder,
    crossfade: Boolean,
    fallbackIcon: ImageVector
) {
    val loaded = phase == MuhabbetImagePhase.Loaded && painter != null
    val imageAlpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = if (crossfade) MuhabbetMotion.effectsDefault() else snap(),
        label = "imageCrossfade"
    )
    val semantics = if (contentDescription == null) {
        Modifier.clearAndSetSemantics {}
    } else {
        Modifier.clearAndSetSemantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    }

    Box(modifier = modifier.clip(shape).then(semantics), contentAlignment = Alignment.Center) {
        // The ground stays underneath until the image is fully opaque, so the fade is placeholder →
        // image rather than placeholder → nothing → image.
        if (imageAlpha < 1f) {
            when {
                phase == MuhabbetImagePhase.Failed -> FailedGround(fallbackIcon)
                placeholder == MuhabbetImagePlaceholder.Shimmer -> Box(Modifier.fillMaxSize().shimmer(RectangleShape))
                else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
            }
        }
        if (loaded) painter?.let { loadedPainter ->
            Image(
                painter = loadedPainter,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = imageAlpha }
            )
        }
    }
}

/** `onSurfaceVariant` on `surfaceContainerHigh`: a measured pair (SemanticColorContrastTest). */
@Composable
private fun FailedGround(icon: ImageVector) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(MuhabbetSizes.IconLarge)
        )
    }
}
