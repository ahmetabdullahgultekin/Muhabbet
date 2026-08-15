package com.muhabbet.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.muhabbet.designsystem.generated.resources.Res
import com.muhabbet.designsystem.generated.resources.manrope_bold
import com.muhabbet.designsystem.generated.resources.manrope_medium
import com.muhabbet.designsystem.generated.resources.manrope_regular
import com.muhabbet.designsystem.generated.resources.manrope_semibold
import org.jetbrains.compose.resources.Font

/**
 * The app's type scale, in Manrope.
 *
 * Every role is named rather than left implicit so that the scale has exactly one home: a font
 * family, letter-spacing or line-height change is made here and nowhere else.
 *
 * The scale used to match the Material 3 baseline to the decimal place, which is a clone signal in
 * its own right — the giveaway being `bodyLarge`'s 0.5sp tracking, the most recognisable "stock
 * Android" tell in any app. See [MuhabbetTypeScale] for what moved and why.
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

/**
 * The scale, with no family applied yet.
 *
 * Family and metrics are separated because resolving `Res.font` is `@Composable` while the scale is
 * not: keeping the numbers in a plain `val` means they stay reviewable as data, and the family gets
 * grafted on once per theme.
 *
 * What moved off the Material baseline, and why:
 * - `bodyLarge` 0.5sp and `bodyMedium` 0.25sp tracking are now 0. Loose tracking on body copy at
 *   these sizes is the single most recognisable stock-Android signal there is, and Manrope is drawn
 *   with enough sidebearing not to want it.
 * - Display and headline take negative tracking. At 32–57sp the default spacing reads as loose;
 *   large type wants to be set tighter than small type, which is the one typographic rule the
 *   Material baseline does not encode.
 * - Display line heights tighten from Material's ~1.12 ratio to ~1.05. A headline that leads like
 *   body copy reads as an accident.
 * - `titleLarge` goes from `Normal` to `SemiBold`. It is a title.
 */
internal val MuhabbetTypeScale = Typography(
    displayLarge = Base.displayLarge.copy(
        fontSize = 57.sp, lineHeight = 60.sp, letterSpacing = (-1.0).sp, fontWeight = FontWeight.Normal
    ),
    displayMedium = Base.displayMedium.copy(
        fontSize = 45.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp, fontWeight = FontWeight.Normal
    ),
    displaySmall = Base.displaySmall.copy(
        fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp, fontWeight = FontWeight.Normal
    ),
    headlineLarge = Base.headlineLarge.copy(
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp, fontWeight = FontWeight.Normal
    ),
    headlineMedium = Base.headlineMedium.copy(
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.25).sp, fontWeight = FontWeight.Normal
    ),
    headlineSmall = Base.headlineSmall.copy(
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Normal
    ),
    titleLarge = Base.titleLarge.copy(
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold
    ),
    titleMedium = Base.titleMedium.copy(
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp, fontWeight = FontWeight.Medium
    ),
    titleSmall = Base.titleSmall.copy(
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium
    ),
    bodyLarge = Base.bodyLarge.copy(
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Normal
    ),
    bodyMedium = Base.bodyMedium.copy(
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Normal
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
 * Manrope, subset to Latin + Latin Extended-A + general punctuation + the lira sign, and instanced
 * from the variable original to four static weights (~50 KB each).
 *
 * Static rather than variable: Google Fonts publishes only the variable file, and variable-axis
 * support on Skia/iOS could not be verified from this repo. Four static instances are the
 * predictable choice, and 208 KB against an ~82 MB debug APK is noise.
 *
 * Latin Extended-A is what carries `İ ı Ğ ğ Ş ş`, which are not optional in a Turkish product —
 * a subset that drops them renders the app's own name wrong. Verified present in the built subset,
 * along with `₺`.
 */
@Composable
private fun manropeFamily(): FontFamily = FontFamily(
    Font(Res.font.manrope_regular, FontWeight.Normal),
    Font(Res.font.manrope_medium, FontWeight.Medium),
    Font(Res.font.manrope_semibold, FontWeight.SemiBold),
    Font(Res.font.manrope_bold, FontWeight.Bold)
)

/**
 * Applies [family] to all fifteen roles without disturbing `platformStyle` or `lineHeightStyle`.
 *
 * Fifteen mechanical lines, because Material 3's `Typography` has no `defaultFontFamily` parameter
 * the way Material 2 did, and rebuilding the roles with `TextStyle(fontFamily = …)` would throw away
 * exactly the properties [Base] exists to preserve.
 */
private fun Typography.withFontFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family)
)

/** The scale with Manrope grafted on. Built once per theme rather than per read. */
@Composable
internal fun rememberMuhabbetTypography(): Typography {
    val family = manropeFamily()
    return remember(family) { MuhabbetTypeScale.withFontFamily(family) }
}

/**
 * Text styles for the messaging surfaces, which sit between the Material roles.
 *
 * These exist so that a chat bubble's body size or a list row's title size is stated once rather
 * than as a `.copy(fontSize = …)` at each call site.
 *
 * **This is a class and not an object, and that is load-bearing.** As an `object` its properties
 * were initialised once from a top-level `Typography` that had no font family — so every chat
 * bubble, conversation title and timestamp in the app would have kept rendering in the system font
 * while everything around them switched to Manrope. The consumers are `ConversationRow`,
 * `MessageBubble`, `VideoBubble`, `ReactionBar`, `HomeShellScreen` and `MuhabbetTopBar`: precisely
 * the highest-traffic surfaces in the product, so the failure would have been both total and easy
 * to miss.
 *
 * 11.sp ([ChatMeta]) is the floor: nothing user-visible renders below it.
 */
@Immutable
class MuhabbetTextStyles internal constructor(typography: Typography) {

    /** Contact/group name in the conversation list. Callers set the weight (bold when unread). */
    val ConversationTitle: TextStyle = typography.bodyLarge.copy(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold
    )

    /** Last-message preview line in the conversation list. */
    val ConversationPreview: TextStyle = typography.bodySmall.copy(fontSize = 14.sp)

    /** Trailing timestamp in the conversation list. */
    val ConversationTimestamp: TextStyle = typography.labelSmall.copy(fontSize = 12.sp)

    /** Message text inside a chat bubble — a step above `bodyMedium`. */
    val ChatBody: TextStyle = typography.bodyMedium.copy(fontSize = 15.sp)

    /** Bubble metadata: send time, "edited" marker, media duration badges. */
    val ChatMeta: TextStyle = typography.labelSmall

    /** The italic "Forwarded" caption above a forwarded bubble. */
    val ChatForwardedLabel: TextStyle = typography.labelSmall.copy(
        fontSize = 12.sp,
        fontStyle = FontStyle.Italic
    )

    /** App-bar title. */
    val TopBarTitle: TextStyle = typography.titleLarge.copy(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold
    )

    /** Emoji glyph in the quick-reaction picker — sized as artwork, not as text. */
    val EmojiPicker: TextStyle = typography.bodyLarge.copy(fontSize = 22.sp)

    /** Emoji glyph in a reaction badge attached to a bubble. */
    val EmojiBadge: TextStyle = typography.bodyLarge.copy(fontSize = 14.sp)
}

/**
 * Read through `Muhabbet.text`.
 *
 * The default is the family-free scale, matching how [LocalSemanticColors] defaults to the light
 * set: a preview rendered outside `MuhabbetTheme` gets the system font rather than crashing. Inside
 * the theme — which is every real screen — it is always the Manrope-bearing instance.
 */
val LocalTextStyles = staticCompositionLocalOf { MuhabbetTextStyles(MuhabbetTypeScale) }
