package com.muhabbet.app.ui.whatsnew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One release's lines, as a bulleted list. Shared by the update sheet and the release-notes screen
 * so the two can never render the same release two different ways — the drift that gave this app
 * top bars in three colours starts exactly here, with a list that looked simple enough to inline.
 *
 * Every colour is a scheme role, so the list is legible in light, dark and OLED without any of the
 * three being a special case: the text takes `onSurface` (the pairing the palette guarantees
 * contrast for) and the marker takes `primary`, which is decorative — it carries no meaning the
 * text does not, and a screen reader reads only the sentence.
 */
@Composable
fun ReleaseNoteLines(
    lines: List<StringResource>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)
    ) {
        lines.forEach { line ->
            ReleaseNoteLine(text = stringResource(line))
        }
    }
}

@Composable
private fun ReleaseNoteLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)) {
        // The marker sits in a box as tall as one line of body text and centres itself in it, rather
        // than taking a hand-tuned top padding. A fixed offset is correct at exactly one font scale
        // and drifts off the first line at every other — and a user who has turned text size up is
        // the last person who should be shown a misaligned list.
        Box(
            modifier = Modifier.height(MuhabbetSizes.IconMedium),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(MuhabbetSizes.IndicatorDot)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
