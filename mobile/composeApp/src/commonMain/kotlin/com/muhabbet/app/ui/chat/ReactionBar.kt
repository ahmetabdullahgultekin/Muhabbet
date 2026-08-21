package com.muhabbet.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.theme.containerColor
import com.muhabbet.designsystem.theme.depth
import com.muhabbet.designsystem.theme.MuhabbetDepth
import com.muhabbet.shared.validation.ValidationRules
import com.muhabbet.designsystem.modifier.pressable
import androidx.compose.foundation.shape.CircleShape

/**
 * The six the bar offers. Sourced from the shared module rather than declared here, because the
 * backend now rejects anything outside that set (#557) \u2014 two lists would mean a button that sends
 * a reaction the server refuses. Ordered here, a set there: the order is a UI concern.
 */
val QUICK_REACTIONS: List<String> = ValidationRules.ALLOWED_REACTIONS.toList()

@Composable
fun QuickReactionBar(
    visible: Boolean,
    onReaction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(initialScale = 0.8f) + fadeIn(),
        exit = scaleOut(targetScale = 0.8f) + fadeOut(),
        modifier = modifier
    ) {
        // Floating: it sits over the bubbles it acts on. This replaces a `shadowElevation` +
        // `tonalElevation` pair with the depth level, so light gets two stacked shadows, dark gets
        // the container step plus a lit hairline, and OLED gets an outline instead of an invisible
        // shadow it would still pay fill rate for.
        val reactionBarShape = RoundedCornerShape(MuhabbetCorners.Pill)
        Surface(
            shape = reactionBarShape,
            color = MuhabbetDepth.Floating.containerColor(),
            modifier = Modifier.depth(MuhabbetDepth.Floating, reactionBarShape)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = MuhabbetSpacing.Small, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QUICK_REACTIONS.forEach { emoji ->
                    Text(
                        text = emoji,
                        style = Muhabbet.text.EmojiPicker,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .size(MuhabbetSizes.MinTouchTarget)
                            .pressable(shape = CircleShape) { onReaction(emoji) }
                            .padding(MuhabbetSpacing.XSmall)
                    )
                }
            }
        }
    }
}

@Composable
fun ReactionBadges(
    reactions: Map<String, Int>,
    currentUserReactions: Set<String>,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (reactions.isEmpty()) return
    Row(
        modifier = modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall)
    ) {
        reactions.forEach { (emoji, count) ->
            val isOwn = emoji in currentUserReactions
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isOwn) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.pressable(shape = MaterialTheme.shapes.medium) { onReactionClick(emoji) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = MuhabbetSizes.GapHairline),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(text = emoji, style = Muhabbet.text.EmojiBadge)
                    if (count > 1) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOwn) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
