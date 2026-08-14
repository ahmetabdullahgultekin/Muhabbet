package com.muhabbet.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ViewOnceBubble(
    message: Message,
    isOwn: Boolean,
    onViewOnce: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalSemanticColors.current
    val bubbleColor = if (isOwn) semanticColors.bubbleOwn else semanticColors.bubbleOther
    val onBubbleColor = if (isOwn) semanticColors.onBubbleOwn else semanticColors.onBubbleOther
    var hasBeenViewed by remember { mutableStateOf(false) }

    val typeLabel = when (message.contentType) {
        ContentType.IMAGE -> stringResource(Res.string.view_once_photo)
        ContentType.VIDEO -> stringResource(Res.string.view_once_video)
        else -> stringResource(Res.string.view_once_label)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = bubbleColor,
            modifier = Modifier.clickable {
                if (!hasBeenViewed) {
                    hasBeenViewed = true
                    onViewOnce()
                }
            }
        ) {
            if (hasBeenViewed) {
                // Viewed state
                Row(
                    modifier = Modifier.padding(
                        horizontal = MuhabbetSpacing.Large,
                        vertical = MuhabbetSpacing.Medium
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
                ) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = stringResource(Res.string.view_once_opened),
                        tint = onBubbleColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(MuhabbetSizes.IconMedium)
                    )
                    Text(
                        text = stringResource(Res.string.view_once_opened),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onBubbleColor.copy(alpha = 0.5f)
                    )
                }
            } else {
                // Not yet viewed.
                //
                // Deliberately a sealed placeholder rather than a preview of the content. There used
                // to be a `blur(20.dp)` thumbnail here, which failed twice over:
                //
                //  1. `Modifier.blur` has NO EFFECT below Android API 31, and minSdk is 26. On
                //     Android 8.0-11 the thumbnail rendered fully sharp — the feature's entire
                //     purpose failing silently on a large slice of devices.
                //  2. Even where it worked, a 20dp blur of a 120dp thumbnail still leaks the
                //     composition, the dominant colour and usually the subject.
                //
                // Rule this establishes: blur may degrade decoratively, never protectively.
                Column(
                    modifier = Modifier.padding(MuhabbetSpacing.Small),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(MuhabbetSizes.ViewOncePlaceholder)
                            .background(
                                onBubbleColor.copy(alpha = 0.1f),
                                MaterialTheme.shapes.medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = stringResource(Res.string.view_once_tap_to_view),
                            tint = onBubbleColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(MuhabbetSizes.IconHero)
                        )
                    }

                    Text(
                        text = "$typeLabel \u00b7 ${stringResource(Res.string.view_once_tap_to_view)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = onBubbleColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(
                            horizontal = MuhabbetSpacing.Small,
                            vertical = MuhabbetSpacing.XSmall
                        )
                    )
                }
            }
        }
    }
}
