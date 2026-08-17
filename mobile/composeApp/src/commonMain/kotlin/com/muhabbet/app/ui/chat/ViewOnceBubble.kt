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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.muhabbet.designsystem.Muhabbet

/**
 * The sealed bubble a view-once message renders as.
 *
 * ### Who may open it, and how many times
 *
 * Exactly once, and only by a recipient. That is decided on the server — the tap calls
 * `POST /messages/{id}/view-once`, which burns the message in the transaction that releases its
 * media, and [Message.viewOnceViewed] carries the answer back on every subsequent load.
 *
 * The refusal used to live in a `remember { mutableStateOf(false) }` here, which made it last
 * exactly as long as the composition: scrolling the bubble out of the list and back, or rotating the
 * phone, offered the seal again. It could afford to be wrong because nothing ever reached it — the
 * flag never left the sender's device (#515), so no real view-once message had ever been rendered by
 * a recipient. Both halves are fixed together; either alone still leaks.
 *
 * The sender sees the same sealed placeholder and cannot open it. Their own copy has no `mediaUrl`
 * either: a view-once photo is unviewable by everyone once it leaves the composer, which is the
 * property the feature is named for.
 */
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
    // Entirely server-resolved. The caller seals the message optimistically on the tap and puts the
    // seal back if the reveal never reached the server, which it cannot do if the bubble keeps its
    // own copy of the answer — and a bubble that latches locally is how the previous version
    // refused a second view for exactly as long as the composition lived.
    val hasBeenViewed = message.viewOnceViewed

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
            modifier = Modifier.clickable(enabled = !hasBeenViewed && !isOwn) { onViewOnce() }
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
                        Muhabbet.icons.Hidden,
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
                            Muhabbet.icons.Visible,
                            // Announced by the label below, which says different things to the
                            // sender and the recipient; a second description here would have a
                            // screen reader offer "Tap to view" on a bubble that does not respond.
                            contentDescription = null,
                            tint = onBubbleColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(MuhabbetSizes.IconHero)
                        )
                    }

                    // The sender is told the state, not offered an action. Their bubble is not
                    // clickable (the backend refuses a self-view, so opening your own photo cannot
                    // spend the recipient's one look), and "Tap to view" on a bubble that does not
                    // respond to a tap is precisely the dead control this app keeps finding in
                    // itself.
                    Text(
                        text = "$typeLabel \u00b7 " + stringResource(
                            if (isOwn) Res.string.view_once_not_opened_yet
                            else Res.string.view_once_tap_to_view
                        ),
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
