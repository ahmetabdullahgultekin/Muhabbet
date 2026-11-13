package com.muhabbet.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dimensions owned by the second wave of components (#847): list items, person rows, the count
 * badge, pills, the search field, spinners and the tab rail.
 *
 * Kept in their own object rather than appended to [MuhabbetSizes] for one reason that is about
 * ownership, not taste: each of these numbers belongs to exactly one component and is meaningless
 * outside it, whereas [MuhabbetSizes] is the vocabulary screens share (touch target, icon sizes,
 * avatar sizes). Mixing the two is how a screen ends up borrowing `CountBadgeMinSize` as "a small
 * square" and the badge can then never change without moving that screen too.
 *
 * Lives under `theme/` because that is the only place `verifyUi`'s `dpLiteral` rule lets a raw `dp`
 * stand; the components themselves name these.
 */
object MuhabbetComponentSizes {

    // ── List items ──────────────────────────────────────────────

    /**
     * Minimum height of a two-line list row at comfortable density — the chat list's 72dp, so a
     * person row in "New chat" and a conversation row in the inbox have the same rhythm.
     */
    val ListItemTwoLineComfortable: Dp = MuhabbetSizes.ChatListItemMinHeight

    /** One-line comfortable row: M3's own one-line list height. */
    val ListItemOneLineComfortable: Dp = 56.dp

    /**
     * Two-line compact row — pickers inside sheets and dialogs, where the list is a means to an end
     * rather than the screen.
     */
    val ListItemTwoLineCompact: Dp = 60.dp

    /**
     * One-line compact row. Never below the touch target: a compact row is still a row someone
     * taps.
     */
    val ListItemOneLineCompact: Dp = MuhabbetSizes.MinTouchTarget

    /** The check disc laid over a selected person's avatar. */
    val SelectionMark: Dp = 20.dp

    /** The ring that separates the [SelectionMark] from the avatar it overlaps. */
    val SelectionMarkRing: Dp = 2.dp

    // ── Count badge ─────────────────────────────────────────────

    /**
     * Smallest the count badge may be. A single digit renders as a circle of this diameter; more
     * digits grow it into a capsule, never shrink it.
     */
    val CountBadgeMinSize: Dp = 20.dp

    /** Horizontal padding inside a multi-digit badge, so "99+" does not touch its own edges. */
    val CountBadgePaddingHorizontal: Dp = 6.dp

    // ── Pill ────────────────────────────────────────────────────

    val PillMinHeight: Dp = 24.dp
    val PillPaddingHorizontal: Dp = 10.dp
    val PillIcon: Dp = 14.dp

    // ── Spinner ─────────────────────────────────────────────────

    /** Inline: inside a button, beside a label, in a search field. */
    val SpinnerSmall: Dp = 16.dp

    /** Icon-sized: replaces an icon while its action runs. */
    val SpinnerMedium: Dp = 24.dp

    /** Standalone: the one spinner a section shows while it loads. */
    val SpinnerLarge: Dp = 40.dp

    /** Stroke for [SpinnerLarge]; the two smaller sizes use [MuhabbetSizes.ProgressStrokeThin]. */
    val SpinnerStrokeLarge: Dp = 4.dp

    // ── Tabs ────────────────────────────────────────────────────

    /**
     * Height of the selected-tab indicator. Same 3dp capsule as the bottom bar's rail
     * ([MuhabbetSizes.NavRailHeight]), because both say "you are here".
     */
    val TabIndicatorHeight: Dp = MuhabbetSizes.NavRailHeight

    // ── Search field ────────────────────────────────────────────

    /** The search field's resting height: the touch target plus room for 1.3× text. */
    val SearchFieldMinHeight: Dp = 52.dp
}
