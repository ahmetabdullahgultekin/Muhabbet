package com.muhabbet.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetComponentSizes
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetTone
import com.muhabbet.designsystem.theme.colors

/**
 * An unread count.
 *
 * Two places drew this with M3's raw `Badge` and `count.toString()` (#847): the bottom bar and the
 * conversation row. So a busy group read "1342" in a circle sized for one digit, the count jumped
 * rather than changed, and the colour pair lived nowhere it could be measured.
 *
 * - **Capped**: anything above [max] reads "[max]+" — see [formatBadgeCount].
 * - **Animated**: a rising count slides up into place, a falling one slides down, on an effects
 *   spring so the digits never overshoot their slot.
 * - **One declared pair**: [com.muhabbet.designsystem.theme.MuhabbetSemanticColors.unreadBadge],
 *   which `SemanticColorContrastTest` measures both as a pair and against every ground a badge sits
 *   on.
 * - **Absent at zero**: a badge reading "0" is noise, so [count] ≤ 0 draws nothing at all.
 *
 * @param contentDescription what TalkBack says. The capped digits alone ("99+") are not a sentence,
 *   so callers pass a localized "12 unread messages"; it defaults to the digits only so that a
 *   badge is never silent.
 */
@Composable
fun MuhabbetCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    max: Int = DefaultBadgeMax,
    contentDescription: String? = null
) {
    val label = formatBadgeCount(count, max) ?: return
    val colors = LocalSemanticColors.current.unreadBadge
    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = MuhabbetComponentSizes.CountBadgeMinSize,
                minHeight = MuhabbetComponentSizes.CountBadgeMinSize
            )
            .background(colors.container, RoundedCornerShape(MuhabbetCorners.Pill))
            .padding(horizontal = MuhabbetComponentSizes.CountBadgePaddingHorizontal)
            .clearAndSetSemantics { this.contentDescription = contentDescription ?: label },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val rising = targetState > initialState
                (slideInVertically(MuhabbetMotion.offsetSpatialDefault()) { if (rising) it else -it } +
                    fadeIn(MuhabbetMotion.effectsDefault()))
                    .togetherWith(
                        slideOutVertically(MuhabbetMotion.offsetSpatialDefault()) { if (rising) -it else it } +
                            fadeOut(MuhabbetMotion.effectsDefault())
                    )
                    .using(SizeTransform(clip = true))
            },
            label = "countBadge"
        ) { shown ->
            Text(
                text = formatBadgeCount(shown, max).orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.content,
                maxLines = 1
            )
        }
    }
}

/**
 * The text a count badge shows, or null when it should not be drawn.
 *
 * Pure so the cap is tested without composing: `0 → null`, `7 → "7"`, `99 → "99"`,
 * `100 → "99+"`. A [max] below 1 is treated as 1, because a cap of zero would render every badge as
 * "0+", which says nothing.
 */
fun formatBadgeCount(count: Int, max: Int = DefaultBadgeMax): String? {
    if (count <= 0) return null
    val cap = max.coerceAtLeast(1)
    return if (count > cap) "$cap+" else count.toString()
}

/** WhatsApp, Telegram and Signal all cap at two digits; a third would widen every row's trailing slot. */
const val DefaultBadgeMax: Int = 99

/**
 * A small, non-interactive label: "Admin", "Bot", "Linked", "Failed".
 *
 * Not a chip — a chip is a control, this is a statement — so it takes no click and has no touch
 * target. The colour comes from a [MuhabbetTone], never from the call site, so text and ground are
 * always the measured pair.
 *
 * @param icon optional leading glyph. Decorative to accessibility: the [text] already says it.
 */
@Composable
fun MuhabbetPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: MuhabbetTone = MuhabbetTone.Neutral,
    icon: ImageVector? = null
) {
    val colors = tone.colors()
    Row(
        modifier = modifier
            .heightIn(min = MuhabbetComponentSizes.PillMinHeight)
            .background(colors.container, RoundedCornerShape(MuhabbetCorners.Pill))
            .padding(horizontal = MuhabbetComponentSizes.PillPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(MuhabbetComponentSizes.PillIcon)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
