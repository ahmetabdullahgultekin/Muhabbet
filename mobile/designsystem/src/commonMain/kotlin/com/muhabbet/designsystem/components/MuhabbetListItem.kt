package com.muhabbet.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.muhabbet.designsystem.modifier.longPressable
import com.muhabbet.designsystem.modifier.pressable
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.LocalTextStyles
import com.muhabbet.designsystem.theme.MuhabbetComponentSizes
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetIcons
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing

/**
 * How much vertical room a list row takes.
 *
 * Two answers, not a free `Dp`: a screen choosing a padding is how the 14 hand-rolled person rows
 * ended up at 14 different heights (#847).
 */
enum class MuhabbetListDensity {
    /** A list that *is* the screen — chats, contacts, call history. */
    Comfortable,

    /** A list inside a sheet or a dialog, where the list is a means to an end. */
    Compact,
}

/**
 * Whether a row takes part in a selection, and if so which side of it.
 *
 * One parameter with three states rather than `selectable` plus `selected`: with two booleans,
 * `selected = true` on a row that is not selectable is expressible and means nothing — the same
 * half-a-pairing shape as `isError` without `supportingText` ([MuhabbetTextField]).
 */
enum class MuhabbetListSelection {
    /** Not in a selection at all. No selected semantics, no check mark. */
    None,

    /** In a multi-select, currently not chosen. */
    Unselected,

    /** In a multi-select, chosen. */
    Selected,
}

/**
 * The one list-row geometry: an optional leading slot, a headline, optional supporting text, and an
 * optional trailing slot.
 *
 * About fourteen screens drew this by hand — avatar, name, subtitle, trailing time or badge — each
 * with its own padding, its own text styles, its own idea of where a long name truncates, and its
 * own click modifier. This is the shared frame; [MuhabbetPersonRow] is the person-shaped preset most
 * of those screens actually want.
 *
 * **Interaction.** [onClick] and [onLongClick] are both optional:
 * - neither: the row is content, not a control, and is not focusable as a button;
 * - [onClick] only: a tap target with a rectangular ripple (correct for a full-bleed row — see
 *   [com.muhabbet.designsystem.modifier.pressable]);
 * - [onLongClick]: long-press opens it and fires `ItemLongPressed`. A row with a long-press action
 *   but no tap action uses the long-press action for a tap as well, so it is never a row that
 *   looks tappable and does nothing on the most common gesture.
 *
 * **Selection.** In [MuhabbetListSelection.Selected] the row takes the quiet selection ground
 * ([com.muhabbet.designsystem.theme.MuhabbetSemanticColors.selectedSubtle]) and reports
 * `selected` to accessibility services; in `Unselected` it reports `selected = false`, which is
 * what tells TalkBack this is a multi-select at all.
 *
 * @param enabled false greys the row and stops it accepting input, without removing it.
 * @param onClickLabel / [onLongClickLabel] what TalkBack says the actions *do* ("open chat",
 *   "show options"). Supplied by the caller because this module has no strings.
 */
@Composable
fun MuhabbetListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    selection: MuhabbetListSelection = MuhabbetListSelection.None,
    density: MuhabbetListDensity = MuhabbetListDensity.Comfortable,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    headlineColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val haptics = LocalHaptics.current
    val textStyles = LocalTextStyles.current
    val selectedGround = LocalSemanticColors.current.selectedSubtle
    val isSelected = selection == MuhabbetListSelection.Selected

    // Effects spring: the ground fades to the selection colour, it must not overshoot into a tone
    // the palette does not have.
    val selectionAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = MuhabbetMotion.effectsDefault(),
        label = "listItemSelection"
    )

    val interaction = when {
        onLongClick != null -> Modifier.longPressable(
            shape = RectangleShape,
            enabled = enabled,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onLongClick = {
                haptics.perform(MuhabbetHapticIntent.ItemLongPressed)
                onLongClick()
            },
            onClick = onClick ?: onLongClick
        )

        onClick != null -> Modifier.pressable(
            shape = RectangleShape,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = if (selection == MuhabbetListSelection.None) Role.Button else Role.Checkbox,
            onClick = onClick
        )

        else -> Modifier
    }

    val selectionSemantics = if (selection == MuhabbetListSelection.None) {
        Modifier
    } else {
        Modifier.semantics { selected = isSelected }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeightFor(density, twoLine = supporting != null))
            .background(selectedGround.container.copy(alpha = selectedGround.container.alpha * selectionAlpha))
            .then(selectionSemantics)
            .then(interaction)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .padding(horizontal = MuhabbetSpacing.Large, vertical = verticalPaddingFor(density)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)
    ) {
        if (leading != null) leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                style = textStyles.ConversationTitle,
                color = if (isSelected) selectedGround.content else headlineColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = textStyles.ConversationPreview,
                    color = if (isSelected) selectedGround.content else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * A person (or a group) as a list row: avatar, name, an optional line under it, an optional trailing
 * slot for a time, a badge or an action.
 *
 * The preset for the pattern #847 counted fourteen times. The avatar size follows the density —
 * the chat list's 52dp when comfortable, 40dp in a sheet — and in a multi-select a check disc rides
 * the avatar's corner rather than a checkbox being added to the row, so the row does not reflow
 * when selection starts.
 *
 * @param avatarContentDescription defaults to null: the name is already the headline, and a screen
 *   reader announcing it twice per row is noise. Pass one only when the avatar says something the
 *   name does not.
 */
@Composable
fun MuhabbetPersonRow(
    name: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    isGroup: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    selection: MuhabbetListSelection = MuhabbetListSelection.None,
    density: MuhabbetListDensity = MuhabbetListDensity.Comfortable,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    avatarContentDescription: String? = null
) {
    val avatarSize = avatarSizeFor(density)
    MuhabbetListItem(
        headline = name,
        modifier = modifier,
        supporting = supporting,
        trailing = trailing,
        selection = selection,
        density = density,
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick,
        onClickLabel = onClickLabel,
        onLongClickLabel = onLongClickLabel,
        leading = {
            Box {
                Box(
                    modifier = if (avatarContentDescription == null) Modifier.clearAndSetSemantics {} else Modifier
                ) {
                    UserAvatar(
                        avatarUrl = avatarUrl,
                        displayName = name,
                        size = avatarSize,
                        isGroup = isGroup,
                        contentDescription = avatarContentDescription
                    )
                }
                SelectionMark(
                    visible = selection == MuhabbetListSelection.Selected,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    )
}

/**
 * The check disc on a selected avatar. Brand fill with its own declared foreground, ringed in the
 * surface colour so it reads as sitting *on* the avatar rather than merging into it. Decorative to
 * accessibility — the row already reports `selected`.
 */
@Composable
private fun SelectionMark(visible: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalSemanticColors.current.selected
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(MuhabbetMotion.spatialFast()),
        exit = scaleOut(MuhabbetMotion.spatialFast())
    ) {
        Box(
            modifier = Modifier
                .size(MuhabbetComponentSizes.SelectionMark)
                .border(MuhabbetComponentSizes.SelectionMarkRing, MaterialTheme.colorScheme.surface, CircleShape)
                .background(colors.container, CircleShape)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MuhabbetIcons.Sent,
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(MuhabbetSizes.IconInline)
            )
        }
    }
}

internal fun minHeightFor(density: MuhabbetListDensity, twoLine: Boolean): Dp = when (density) {
    MuhabbetListDensity.Comfortable ->
        if (twoLine) MuhabbetComponentSizes.ListItemTwoLineComfortable else MuhabbetComponentSizes.ListItemOneLineComfortable
    MuhabbetListDensity.Compact ->
        if (twoLine) MuhabbetComponentSizes.ListItemTwoLineCompact else MuhabbetComponentSizes.ListItemOneLineCompact
}

private fun verticalPaddingFor(density: MuhabbetListDensity): Dp = when (density) {
    MuhabbetListDensity.Comfortable -> MuhabbetSpacing.Medium
    MuhabbetListDensity.Compact -> MuhabbetSpacing.Small
}

private fun avatarSizeFor(density: MuhabbetListDensity): Dp = when (density) {
    MuhabbetListDensity.Comfortable -> MuhabbetSizes.AvatarChatList
    MuhabbetListDensity.Compact -> MuhabbetSizes.AvatarSmall
}

/** M3's disabled-content opacity. */
private const val DisabledAlpha = 0.38f
