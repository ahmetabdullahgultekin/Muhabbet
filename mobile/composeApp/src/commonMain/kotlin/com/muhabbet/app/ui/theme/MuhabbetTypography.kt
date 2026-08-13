package com.muhabbet.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The app's type scale.
 *
 * Every role is named rather than left implicit so that the scale has exactly one home: a font
 * family, letter-spacing or line-height change is made here and nowhere else. Each is derived from
 * the Material 3 baseline (see [Base]) so the metrics currently match it — the point of declaring
 * them is ownership, not divergence.
 *
 * Sizes that genuinely have no Material role (chat body, list rows, bubble metadata) live in
 * [MuhabbetTextStyles] instead of being applied ad hoc with `.copy(fontSize = …)` at call sites.
 */
/**
 * The Material 3 baseline, used as the starting point for every role below.
 *
 * Material builds each of its roles from a shared default that carries `platformStyle`
 * (`includeFontPadding = false`), a centred `lineHeightStyle`, and `FontFamily.SansSerif`. A bare
 * `TextStyle(...)` has none of those, so declaring the roles from scratch — even with identical
 * sizes — restores legacy font padding, redistributes the leading, and hands font resolution back to
 * the platform default. Text gets taller, baselines shift, and `maxLines` truncation lands elsewhere,
 * everywhere in the app at once.
 *
 * Copying from the baseline keeps those three properties and changes only what is named.
 */
private val Base = Typography()

val MuhabbetTypography = Typography(
    displayLarge = Base.displayLarge.copy(
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp, fontWeight = FontWeight.Normal
    ),
    displayMedium = Base.displayMedium.copy(
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Normal
    ),
    displaySmall = Base.displaySmall.copy(
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Normal
    ),
    headlineLarge = Base.headlineLarge.copy(
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Normal
    ),
    headlineMedium = Base.headlineMedium.copy(
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Normal
    ),
    headlineSmall = Base.headlineSmall.copy(
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Normal
    ),
    titleLarge = Base.titleLarge.copy(
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Normal
    ),
    titleMedium = Base.titleMedium.copy(
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp, fontWeight = FontWeight.Medium
    ),
    titleSmall = Base.titleSmall.copy(
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium
    ),
    bodyLarge = Base.bodyLarge.copy(
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Normal
    ),
    bodyMedium = Base.bodyMedium.copy(
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp, fontWeight = FontWeight.Normal
    ),
    bodySmall = Base.bodySmall.copy(
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp, fontWeight = FontWeight.Normal
    ),
    labelLarge = Base.labelLarge.copy(
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium
    ),
    labelMedium = Base.labelMedium.copy(
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium
    ),
    labelSmall = Base.labelSmall.copy(
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium
    )
)

/**
 * Text styles for the messaging surfaces, which sit between the Material roles.
 *
 * These exist so that a chat bubble's body size or a list row's title size is stated once
 * rather than as a `.copy(fontSize = …)` at each call site. Each style is derived from the
 * Material role it is closest to, so it inherits any future font-family change made in
 * [MuhabbetTypography].
 *
 * 11.sp ([ChatMeta]) is the floor: nothing user-visible renders below it.
 */
object MuhabbetTextStyles {

    /** Contact/group name in the conversation list. Callers set the weight (bold when unread). */
    val ConversationTitle: TextStyle = MuhabbetTypography.bodyLarge.copy(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold
    )

    /** Last-message preview line in the conversation list. */
    val ConversationPreview: TextStyle = MuhabbetTypography.bodySmall.copy(fontSize = 14.sp)

    /** Trailing timestamp in the conversation list. */
    val ConversationTimestamp: TextStyle = MuhabbetTypography.labelSmall.copy(fontSize = 12.sp)

    /** Message text inside a chat bubble — a step above [MuhabbetTypography.bodyMedium]. */
    val ChatBody: TextStyle = MuhabbetTypography.bodyMedium.copy(fontSize = 15.sp)

    /** Bubble metadata: send time, "edited" marker, media duration badges. */
    val ChatMeta: TextStyle = MuhabbetTypography.labelSmall

    /** The italic "Forwarded" caption above a forwarded bubble. */
    val ChatForwardedLabel: TextStyle = MuhabbetTypography.labelSmall.copy(
        fontSize = 12.sp,
        fontStyle = FontStyle.Italic
    )

    /** App-bar title on the home shell. */
    val TopBarTitle: TextStyle = MuhabbetTypography.titleLarge.copy(
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium
    )

    /** Emoji glyph in the quick-reaction picker — sized as artwork, not as text. */
    val EmojiPicker: TextStyle = MuhabbetTypography.bodyLarge.copy(fontSize = 22.sp)

    /** Emoji glyph in a reaction badge attached to a bubble. */
    val EmojiBadge: TextStyle = MuhabbetTypography.bodyLarge.copy(fontSize = 14.sp)
}
