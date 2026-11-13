package com.muhabbet.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.muhabbet.designsystem.theme.MuhabbetAlphas
import com.muhabbet.designsystem.theme.MuhabbetComponentSizes
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes

/** The three places a spinner legitimately appears. See [MuhabbetSpinner]. */
enum class MuhabbetSpinnerSize {
    /** Inline: inside a button, beside a label, in a search field's trailing slot. */
    Small,

    /** Icon-sized: replaces an icon while the action it stands for runs. */
    Medium,

    /** A section's own loading mark. Not a screen's — that is [MuhabbetLoadingState]. */
    Large,
}

/**
 * A progress ring — indeterminate while [progress] is null, determinate when it is not.
 *
 * Not a loading *state*: a whole screen waiting is [MuhabbetLoadingState] or a skeleton, which is
 * what `verifyUi`'s `bareProgress` ratchet keeps pushing screens towards. This is the small ring
 * that belongs *inside* something — a button that is submitting, an upload's percentage, a search
 * field fetching results — and the six hand-rolled `CircularProgressIndicator(Modifier.size(…),
 * strokeWidth = …)` copies that each picked their own size and stroke (#847).
 *
 * The stroke is chosen from the size. M3's default 4dp stroke is proportioned for its 40dp ring and
 * turns a 16dp ring into a solid disc, which is why [MuhabbetSizes.ProgressStrokeThin] exists.
 *
 * A determinate ring animates towards each new value on an effects spring, so an upload reporting
 * progress in jumps still moves smoothly and never runs past the real number.
 *
 * @param progress 0..1, clamped. Null means "working, no idea how long".
 * @param contentDescription what TalkBack announces — "Uploading", "Searching". A determinate ring
 *   additionally reports its value through the progress semantics M3 sets, so the percentage is not
 *   repeated here.
 */
@Composable
fun MuhabbetSpinner(
    modifier: Modifier = Modifier,
    size: MuhabbetSpinnerSize = MuhabbetSpinnerSize.Medium,
    progress: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null
) {
    val diameter = diameterFor(size)
    val stroke = strokeFor(size)
    val labelled = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    val ring = modifier.size(diameter).then(labelled)

    if (progress == null) {
        CircularProgressIndicator(
            modifier = ring,
            color = color,
            strokeWidth = stroke,
            trackColor = Color.Transparent
        )
    } else {
        val animated by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = MuhabbetMotion.effectsDefault(),
            label = "spinnerProgress"
        )
        CircularProgressIndicator(
            progress = { animated },
            modifier = ring,
            color = color,
            strokeWidth = stroke,
            trackColor = color.copy(alpha = MuhabbetAlphas.ProgressTrack)
        )
    }
}

private fun diameterFor(size: MuhabbetSpinnerSize): Dp = when (size) {
    MuhabbetSpinnerSize.Small -> MuhabbetComponentSizes.SpinnerSmall
    MuhabbetSpinnerSize.Medium -> MuhabbetComponentSizes.SpinnerMedium
    MuhabbetSpinnerSize.Large -> MuhabbetComponentSizes.SpinnerLarge
}

private fun strokeFor(size: MuhabbetSpinnerSize): Dp = when (size) {
    MuhabbetSpinnerSize.Small, MuhabbetSpinnerSize.Medium -> MuhabbetSizes.ProgressStrokeThin
    MuhabbetSpinnerSize.Large -> MuhabbetComponentSizes.SpinnerStrokeLarge
}
