package com.muhabbet.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muhabbet.designsystem.platform.SystemBarsEffect
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// ─── Alpha tokens ───────────────────────────────────────────

object MuhabbetAlphas {
    /** Opacity of the control bars drawn over the full-screen media viewer's backdrop. */
    const val ScrimOverlay = 0.5f

    /**
     * How far the unfilled part of a progress or result bar falls back from the filled part.
     *
     * A token rather than a number at each bar because the two that exist — the voice-note scrubber
     * and the poll result — used 0.3 and 0.2 for the same idea, and both derived the fill from a
     * colour that was not on the surface behind them. The fill is now the surface's own foreground
     * and the track is this much of it, so the pair holds wherever the bar is drawn.
     */
    const val ProgressTrack = 0.24f

    /**
     * Opacity of a surface drawn **directly on the chat wallpaper**, rather than inside a bubble.
     *
     * There is one such surface today: the date-separator pill (`DateSeparator.kt`). It exists to be
     * slightly translucent, so the wallpaper tints it and the chat reads as one surface rather than
     * a list of cards — which means whatever the wallpaper is showing reaches the label behind it.
     *
     * At the 80% it used to be hardcoded to, that reach is 20%, and 20% of an arbitrary ground is
     * more than the label can absorb. Measured through it, the label fell to **4.03:1** on a light
     * wallpaper in the dark theme (reachable: the dark-mode toggle carries a light selection into a
     * dark chat), **4.34:1** on the near-black swatch in the light theme, and **3.88:1** over a
     * pure-white photo — all under the 4.5:1 AA floor, on a screen where nothing had picked an
     * unusual colour.
     *
     * 90% is where every ground the app can put behind it clears with room: across all three themes,
     * all 24 solid swatches, both stops of all 8 gradients, and pure-white and pure-black photos, the
     * worst case is **5.42:1**. The pill is still visibly tinted by the wallpaper at 90% — the
     * translucency was never the problem, the amount of it was.
     *
     * `WallpaperContrastTest` holds this. Any future surface painted straight onto the wallpaper
     * takes this token rather than a number of its own, or it inherits the bug rather than the fix.
     */
    const val ChatOverlaySurface = 0.90f
}

// ─── Semantic colors (beyond M3 colorScheme) ────────────────

/**
 * A ground and the one foreground that is allowed on it.
 *
 * The reason this type exists is #517: the selected option in a poll was unreadable in every theme,
 * because a call site picked a filled container from one place and its text colour from another.
 * That is not a mistake anyone makes once — the same shape produced `Color.White` on
 * `colorScheme.primary` in two list rows, and it will produce a third the next time a selected state
 * is drawn, because nothing about picking a background suggests that a matching foreground exists.
 *
 * So the two travel together. `Muhabbet.colors.selected` hands back both; there is no way to reach
 * the container without the content sitting in the same expression, and
 * [com.muhabbet.designsystem.theme] has a test that measures every one of these pairs, in all three
 * themes, against the WCAG floors.
 */
@Immutable
data class MuhabbetColorPair(
    val container: Color,
    val content: Color
)

data class MuhabbetSemanticColors(
    // ── Marks ───────────────────────────────────────────────
    // A glyph or a rule drawn ON something else. These are foregrounds, never grounds, so they have
    // no partner — what they must contrast against is whatever surface they land on, and the test
    // names those surfaces explicitly.
    val statusOnline: Color,
    val statusRead: Color,
    val statusDelivered: Color,
    val statusSending: Color,
    val callMissed: Color,
    val linkColor: Color,
    val dividerColor: Color,
    val secondaryText: Color,

    // ── Grounds ─────────────────────────────────────────────
    // Every one arrives with its foreground attached. See [MuhabbetColorPair].
    val callDecline: MuhabbetColorPair,
    val callAccept: MuhabbetColorPair,
    val bubbleOwn: MuhabbetColorPair,
    val bubbleOther: MuhabbetColorPair,

    /**
     * A panel inset into a bubble — a poll option, a quoted reply — and the same panel once it is
     * the chosen one.
     *
     * Separate from [selected] because a bubble is not a surface. The own bubble is already a copper
     * wash, so the app-wide selection colour lands on top of a ground it was never measured against;
     * that is precisely how the poll ended up drawing `onPrimary` over `bubbleOwn`.
     */
    val bubbleOwnInset: MuhabbetColorPair,
    val bubbleOwnInsetSelected: MuhabbetColorPair,
    val bubbleOtherInset: MuhabbetColorPair,
    val bubbleOtherInsetSelected: MuhabbetColorPair,

    val unreadBadge: MuhabbetColorPair,
    val chatWallpaper: MuhabbetColorPair,
    val inputBar: MuhabbetColorPair,
    val inputField: MuhabbetColorPair,

    /**
     * A filled selection on an ordinary surface: the chosen filter chip, the chosen wallpaper tab,
     * the chosen language row. Loud on purpose — it is the brand fill.
     */
    val selected: MuhabbetColorPair,

    /**
     * A selection that has to stay quiet: a whole row that is merely current, a swatch's backing
     * plate. Same decision, a tenth of the volume, and still measured.
     */
    val selectedSubtle: MuhabbetColorPair,

    /**
     * Backdrop of the immersive full-screen media viewer. Deliberately identical in every
     * variant — the viewer is theme-independent so that photos are judged against black.
     */
    val scrim: MuhabbetColorPair,

    /** Translucent bar drawn over the [scrim] to carry the viewer's controls. */
    val scrimOverlay: MuhabbetColorPair
)

/*
 * "Sending" and "delivered" share a neutral on purpose.
 *
 * Separating them by colour is what pushed the inherited palette under contrast: on a copper bubble,
 * holding both above 3:1 while keeping them distinguishable is not possible — one ink step apart
 * lands at 2.61:1. The distinction is already carried by the glyph (a clock, one tick, two ticks),
 * so a second, weaker encoding in colour buys nothing and cost the app its contrast floor.
 */

val LightSemanticColors = MuhabbetSemanticColors(
    statusOnline = MuhabbetPalette.Success,
    statusRead = MuhabbetPalette.InfoBlue,
    statusDelivered = MuhabbetPalette.Ink.I50,
    statusSending = MuhabbetPalette.Ink.I50,
    callMissed = MuhabbetPalette.Danger,
    linkColor = MuhabbetPalette.Copper.C40,
    dividerColor = MuhabbetPalette.Ink.I80,
    secondaryText = MuhabbetPalette.Ink.I40,

    callDecline = MuhabbetColorPair(MuhabbetPalette.Danger, Color.White),
    callAccept = MuhabbetColorPair(MuhabbetPalette.Success, Color.White),
    bubbleOwn = MuhabbetColorPair(MuhabbetPalette.BubbleOwnLight, MuhabbetPalette.Ink.I10),
    bubbleOther = MuhabbetColorPair(Color.White, MuhabbetPalette.Ink.I10),
    bubbleOwnInset = MuhabbetColorPair(MuhabbetPalette.CopperContainerLight, MuhabbetPalette.Ink.I10),
    bubbleOwnInsetSelected = MuhabbetColorPair(MuhabbetPalette.Copper.C80, MuhabbetPalette.OnCopperContainerLight),
    bubbleOtherInset = MuhabbetColorPair(MuhabbetPalette.Ink.I95, MuhabbetPalette.Ink.I10),
    bubbleOtherInsetSelected = MuhabbetColorPair(MuhabbetPalette.CopperContainerLight, MuhabbetPalette.OnCopperContainerLight),
    unreadBadge = MuhabbetColorPair(MuhabbetPalette.Copper.C40, Color.White),
    chatWallpaper = MuhabbetColorPair(MuhabbetPalette.WallpaperLight, MuhabbetPalette.Ink.I10),
    inputBar = MuhabbetColorPair(Color.White, MuhabbetPalette.Ink.I10),
    inputField = MuhabbetColorPair(MuhabbetPalette.Ink.I95, MuhabbetPalette.Ink.I10),
    selected = MuhabbetColorPair(MuhabbetPalette.Copper.C40, Color.White),
    selectedSubtle = MuhabbetColorPair(MuhabbetPalette.CopperContainerLight, MuhabbetPalette.OnCopperContainerLight),
    scrim = MuhabbetColorPair(Color.Black, Color.White),
    scrimOverlay = MuhabbetColorPair(Color.Black.copy(alpha = MuhabbetAlphas.ScrimOverlay), Color.White)
)

val DarkSemanticColors = MuhabbetSemanticColors(
    statusOnline = MuhabbetPalette.SuccessOnDark,
    statusRead = MuhabbetPalette.InfoBlueOnDark,
    statusDelivered = MuhabbetPalette.Ink.I60,
    statusSending = MuhabbetPalette.Ink.I60,
    callMissed = MuhabbetPalette.DangerOnDark,
    linkColor = MuhabbetPalette.Copper.C80,
    dividerColor = MuhabbetPalette.Ink.I20,
    // I70, not I60. Every timestamp and every "3 votes" caption under an outgoing message is drawn
    // in this colour on the copper-brown own bubble, where I60 lands at 3.88:1 — below the body
    // floor, on the single most repeated piece of text in the app. It also failed on the input
    // field (4.33:1). I70 clears both at 5.75:1 and 6.43:1.
    secondaryText = MuhabbetPalette.Ink.I70,

    callDecline = MuhabbetColorPair(MuhabbetPalette.DangerOnDark, MuhabbetPalette.Ink.I05),
    callAccept = MuhabbetColorPair(MuhabbetPalette.SuccessOnDark, MuhabbetPalette.Ink.I05),
    bubbleOwn = MuhabbetColorPair(MuhabbetPalette.BubbleOwnDark, MuhabbetPalette.PaperOnDark),
    bubbleOther = MuhabbetColorPair(MuhabbetPalette.Ink.I15, MuhabbetPalette.PaperOnDark),
    bubbleOwnInset = MuhabbetColorPair(MuhabbetPalette.CopperContainerDark, MuhabbetPalette.PaperOnDark),
    bubbleOwnInsetSelected = MuhabbetColorPair(MuhabbetPalette.Copper.C60, MuhabbetPalette.Ink.I05),
    bubbleOtherInset = MuhabbetColorPair(MuhabbetPalette.Ink.I20, MuhabbetPalette.PaperOnDark),
    bubbleOtherInsetSelected = MuhabbetColorPair(MuhabbetPalette.CopperContainerDark, MuhabbetPalette.Copper.C90),
    unreadBadge = MuhabbetColorPair(MuhabbetPalette.Copper.C70, MuhabbetPalette.Ink.I05),
    chatWallpaper = MuhabbetColorPair(MuhabbetPalette.Ink.I00, MuhabbetPalette.PaperOnDark),
    inputBar = MuhabbetColorPair(MuhabbetPalette.Ink.I15, MuhabbetPalette.PaperOnDark),
    inputField = MuhabbetColorPair(MuhabbetPalette.Ink.I20, MuhabbetPalette.PaperOnDark),
    selected = MuhabbetColorPair(MuhabbetPalette.Copper.C70, MuhabbetPalette.Ink.I05),
    selectedSubtle = MuhabbetColorPair(MuhabbetPalette.CopperContainerDark, MuhabbetPalette.Copper.C90),
    scrim = MuhabbetColorPair(Color.Black, Color.White),
    scrimOverlay = MuhabbetColorPair(Color.Black.copy(alpha = MuhabbetAlphas.ScrimOverlay), Color.White)
)

val OledSemanticColors = DarkSemanticColors.copy(
    bubbleOther = MuhabbetColorPair(MuhabbetPalette.Ink.I00, MuhabbetPalette.PaperOnDark),
    bubbleOtherInset = MuhabbetColorPair(MuhabbetPalette.Ink.I10, MuhabbetPalette.PaperOnDark),
    chatWallpaper = MuhabbetColorPair(Color.Black, MuhabbetPalette.PaperOnDark),
    inputBar = MuhabbetColorPair(MuhabbetPalette.Ink.I00, MuhabbetPalette.PaperOnDark),
    inputField = MuhabbetColorPair(MuhabbetPalette.Ink.I10, MuhabbetPalette.PaperOnDark),
    dividerColor = MuhabbetPalette.Ink.I10
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

/**
 * Pairs an arbitrary colour with a foreground that is actually legible on it.
 *
 * For the colours the palette chose there is always a declared partner. For the ones it did not —
 * a wallpaper swatch the user picked, twelve of them running from near-white to near-black — there
 * cannot be. The wallpaper picker drew a hardcoded white tick on all twelve, so on the six pale
 * swatches the "selected" mark was invisible at roughly 1.1:1, which is the same defect as #517
 * wearing different clothes.
 *
 * Relative luminance, WCAG's own formula, then whichever of ink and paper is further away. Both ends
 * of the [MuhabbetPalette.Ink] ramp are extreme enough that the loser still clears 4.5:1 on any
 * midtone, so this cannot return an unreadable answer.
 */
fun readableContentOn(container: Color): MuhabbetColorPair {
    fun linear(channel: Float): Double {
        val v = channel.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    val luminance = 0.2126 * linear(container.red) +
        0.7152 * linear(container.green) +
        0.0722 * linear(container.blue)

    // 0.179 is where black and white contrast equally against a colour; below it, paper wins.
    val content = if (luminance > 0.179) MuhabbetPalette.Ink.I10 else MuhabbetPalette.PaperOnDark
    return MuhabbetColorPair(container, content)
}

// ─── Spacing tokens ─────────────────────────────────────────

object MuhabbetSpacing {
    /** No gap. Named so that "deliberately flush" is distinguishable from "forgot to set it". */
    val None: Dp = 0.dp
    val XSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 16.dp
    val XLarge: Dp = 24.dp
    val XXLarge: Dp = 32.dp
}

// ─── Size tokens ────────────────────────────────────────────

object MuhabbetSizes {
    val MinTouchTarget: Dp = 48.dp
    val IconSmall: Dp = 16.dp
    val IconMedium: Dp = 20.dp
    val IconLarge: Dp = 24.dp

    /** An icon carrying a state on its own, not labelling something else — e.g. the view-once eye. */
    val IconHero: Dp = 32.dp

    /** The tinted square a settings row's leading icon sits in, so the whole Settings surface reads
     *  as one family instead of loose icons on a plain row. */
    val SettingsIconTile: Dp = 36.dp

    /** The large, faded icon above an empty- or error-state message. */
    val IconEmptyState: Dp = 56.dp

    /** The soft circular backdrop behind [IconEmptyState], so a screen state reads as a considered
     *  surface rather than a floating icon. */
    val StateIconBadge: Dp = 96.dp

    /**
     * A hairline gap. Smaller than [MuhabbetSpacing.XSmall] and used only where a 4dp gap would
     * separate things that belong to each other — a bubble's caption from its body, a timestamp
     * from its tick.
     */
    val GapHairline: Dp = 2.dp

    /** The accent bar down the side of a quoted reply. */
    val QuoteBarWidth: Dp = 3.dp
    val QuoteBarHeight: Dp = 32.dp

    /** A glyph sitting inline with body text — the "edited" pencil, the forwarded arrow. */
    val IconInline: Dp = 12.dp

    /** The document-type glyph on a file attachment bubble. */
    val IconAttachment: Dp = 28.dp

    /** Stroke of a spinner drawn small — inside a badge or a button, never filling a screen. */
    val ProgressStrokeInline: Dp = 2.dp

    /** Play/pause overlay centred on a video or voice bubble. */
    val MediaControl: Dp = 48.dp

    /** Padding inside an attachment row — tighter than Medium so the glyph sits close to its name. */
    val AttachmentPadding: Dp = 10.dp

    /** A colour swatch standing in for a legend entry — the storage breakdown's category dots. */
    val IndicatorDot: Dp = 8.dp

    /** The small accent mark a [com.muhabbet.designsystem.components.SectionHeader] shows when it
     *  has neither an icon nor a legend dot — every header carries a mark now, not just some. */
    val SectionAccentWidth: Dp = 3.dp
    val SectionAccentHeight: Dp = 14.dp

    /** The delivery tick beside a bubble's timestamp — smaller than an icon, it is punctuation. */
    val IconStatusTick: Dp = 14.dp

    /**
     * The muted / pinned marks beside a conversation's title.
     *
     * Same 14.dp as [IconStatusTick] and deliberately its own name: these are row state, not
     * punctuation on a bubble, and the two should be free to diverge without one silently dragging
     * the other. Both call sites hardcoded the number until #655 added a third and the guardrail
     * refused it — which is the ratchet working exactly as intended.
     */
    val IconRowIndicator: Dp = 14.dp

    /** Accept and decline on the incoming-call screen — the largest touch targets in the app. */
    val CallActionButton: Dp = 64.dp

    /** Gap between accept and decline. Wide on purpose: these two must not be mis-tapped. */
    val CallActionGap: Dp = 64.dp

    /** A resting outline: divider-weight, present but not asking for attention. */
    val BorderHairline: Dp = 1.dp

    /**
     * A focused or errored outline, the brand mark's ring, and — since #433 — the width of the
     * ring [MuhabbetTextField] draws around itself on focus. Doubled so it reads as a state.
     */
    val BorderActive: Dp = 2.dp

    /** Gap between a text field's own outline and the focus ring drawn around it. */
    val TextFieldFocusRingSpread: Dp = 3.dp

    /** One cell of the attachment sheet's icon grid — the tinted circle behind each glyph. */
    val AttachmentSwatch: Dp = 56.dp

    /** One digit box in a verification code. Wider than tall would read as a text field. */
    val OtpBoxWidth: Dp = 44.dp
    val OtpBoxHeight: Dp = 56.dp

    /** One segment of the sign-up progress rail. */
    val StepRailSegmentWidth: Dp = 28.dp
    val StepRailSegmentHeight: Dp = 4.dp

    /**
     * The drawn artwork at the top of an onboarding step. Larger than [StateIconBadge], which sits
     * above a sentence in a list; this one is the top half of a screen the user has nothing else to
     * look at, and at 96dp it reads as an oversized icon rather than as an illustration.
     */
    val OnboardingIllustration: Dp = 132.dp

    /**
     * The copper mark that rides the top edge of the bottom bar and slides to the selected tab.
     *
     * Deliberately the same 28dp width as [StepRailSegmentWidth]: the sign-up rail and the tab rail
     * are the same idea — a short capsule saying "you are here" — and they should be recognisably
     * the same mark. One dp thinner than the step rail, because this one sits on a hairline track
     * rather than alone on the page.
     */
    val NavRailWidth: Dp = 28.dp
    val NavRailHeight: Dp = 3.dp

    /**
     * Resting height of one bottom-bar item, icon and label included.
     *
     * A minimum rather than a fixed height: at 1.3x font scale a fixed bar clips its own labels,
     * which is the single most common accessibility defect in a bottom navigation.
     */
    val NavItemMinHeight: Dp = 56.dp

    /** How far the selected tab's icon rises. Small on purpose — a hint, not a hop. */
    val NavItemLift: Dp = 2.dp

    /** How far the unread badge is pushed off the icon's top-right corner. */
    val NavBadgeOffsetX: Dp = 10.dp
    val NavBadgeOffsetY: Dp = 4.dp

    /**
     * Stroke for a spinner shrunk to icon size. M3's default stroke is proportioned for the default
     * 40dp indicator and reads as a solid disc once the diameter drops to 16–24dp.
     */
    val ProgressStrokeThin: Dp = 2.dp

    /**
     * Body height of a picker sheet. Fixed rather than wrapped: the grid inside is paged, so a
     * wrapping sheet would resize as results arrive.
     */
    val PickerSheetHeight: Dp = 420.dp

    /** Height of a placeholder text line in a skeleton — matches a body line's visual weight. */
    val SkeletonLine: Dp = 14.dp

    /** The secondary placeholder line, thinner so the two do not read as a progress bar. */
    val SkeletonLineSmall: Dp = 12.dp

    /**
     * A one-line placeholder bubble in the chat skeleton: one line of text plus the bubble's own
     * vertical padding, so the placeholder occupies the height a short message actually will.
     */
    val SkeletonBubbleShort: Dp = 40.dp

    /** A two-line placeholder bubble. Most messages are longer than one line, so most are these. */
    val SkeletonBubbleTall: Dp = 62.dp

    /**
     * How tall a scrolling picker inside a bottom sheet may grow.
     *
     * Capped rather than free: an unbounded `LazyColumn` in a sheet expands to the full screen, so
     * the sheet's own title and the confirm affordance below it scroll out of reach.
     */
    val PickerSheetMaxHeight: Dp = 360.dp

    // Avatar sizes
    val AvatarXSmall: Dp = 36.dp
    val AvatarSmall: Dp = 40.dp
    val AvatarChatList: Dp = 52.dp
    val AvatarChatBar: Dp = 42.dp
    val AvatarMedium: Dp = 48.dp
    val AvatarLarge: Dp = 56.dp
    val AvatarXLarge: Dp = 72.dp
    val AvatarXXLarge: Dp = 80.dp
    val AvatarHero: Dp = 96.dp
    val AvatarCall: Dp = 120.dp

    // Bubble dimensions
    val BubbleMinWidth: Dp = 80.dp

    /**
     * Widest a chat bubble may grow. A fixed cap rather than a fraction of the window:
     * `Modifier.fillMaxWidth(fraction)` pins a bubble to that width instead of capping it,
     * so short messages would render as wide as long ones.
     */
    val BubbleMaxWidth: Dp = 320.dp

    val BubblePaddingHorizontal: Dp = 8.dp
    val BubblePaddingVertical: Dp = 6.dp
    val ImagePreviewMaxHeight: Dp = 200.dp
    val StickerSize: Dp = 150.dp

    /**
     * The sealed tile standing in for an unopened view-once message. Deliberately not a preview —
     * see the note in `ViewOnceBubble`.
     */
    val ViewOncePlaceholder: Dp = 120.dp

    // Chat list
    val ChatListItemMinHeight: Dp = 72.dp
    val ChatListDividerInset: Dp = 84.dp
}

// ─── Duration tokens ────────────────────────────────────────

object MuhabbetDurations {
    const val TypingTimeoutMs: Long = 3000L
    const val StatusDisplayMs: Long = 5000L
    const val StatusProgressTickMs: Long = 50L
    const val CallTimerTickMs: Long = 1000L
    const val ShimmerDurationMs: Int = 1200

    /**
     * How long a screen may be loading before its skeleton is shown at all.
     *
     * A cached list or a warm connection resolves well inside this, and a placeholder that appears
     * and vanishes inside 80 ms does not read as "content is coming" — it reads as a glitch. Below
     * this threshold the correct thing to draw is nothing: the screen simply arrives.
     */
    val SkeletonAppearAfter: Duration = 180.milliseconds

    /**
     * Once shown, how long a skeleton stays regardless of when the load finishes.
     *
     * Without this the delay alone is not enough: a load that lands at 190 ms would put the skeleton
     * up for ten milliseconds, which is the same flash the delay exists to prevent, just moved. Held
     * together the two mean a skeleton is either absent or legible, never a strobe.
     */
    val SkeletonMinimumVisible: Duration = 450.milliseconds
}

// ─── Gesture tokens ─────────────────────────────────────────

object MuhabbetGestures {
    const val SwipeReplyThreshold: Float = 80f
    const val SwipeReplyMax: Float = 120f

    /**
     * How far left the record button must be dragged, in px, before releasing discards the
     * recording instead of stopping it. Evaluated only at release — dragging past it and back is a
     * deliberate "undo", matching [MuhabbetHapticIntent.SwipeArmed]/[MuhabbetHapticIntent.SwipeCommitted]
     * firing once each rather than on every frame past the line.
     */
    const val VoiceCancelThresholdPx: Float = 140f

    /**
     * How far up the record button must be dragged, in px, before the recording locks hands-free.
     * Unlike the cancel threshold this fires the instant it is crossed, not at release — the whole
     * point of locking is that the finger can then be lifted without ending the recording.
     */
    const val VoiceLockThresholdPx: Float = 100f
}

// ─── Elevation tokens ──────────────────────────────────────

object MuhabbetElevation {
    val None: Dp = 0.dp
    val Level1: Dp = 1.dp
    val Level2: Dp = 2.dp
    val Level3: Dp = 3.dp
    val Level4: Dp = 4.dp
    val Level5: Dp = 6.dp
    val Level6: Dp = 8.dp
}

@Composable
fun MuhabbetTheme(
    mode: MuhabbetThemeMode = MuhabbetThemeMode.System,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    // Only System consults the OS. Light/Dark/Oled are explicit user choices and are honoured
    // unconditionally — the previous `isDark && oledBlack` guard silently downgraded a user who
    // picked OLED on a light-mode phone all the way back to Light.
    val resolved = when (mode) {
        MuhabbetThemeMode.System -> if (isSystemInDarkTheme()) ResolvedThemeMode.Dark else ResolvedThemeMode.Light
        MuhabbetThemeMode.Light -> ResolvedThemeMode.Light
        MuhabbetThemeMode.Dark -> ResolvedThemeMode.Dark
        MuhabbetThemeMode.Oled -> ResolvedThemeMode.Oled
    }
    val colorScheme = when (resolved) {
        ResolvedThemeMode.Light -> MuhabbetLightColorScheme
        ResolvedThemeMode.Dark -> MuhabbetDarkColorScheme
        ResolvedThemeMode.Oled -> MuhabbetOledBlackColorScheme
    }
    val semanticColors = when (resolved) {
        ResolvedThemeMode.Light -> LightSemanticColors
        ResolvedThemeMode.Dark -> DarkSemanticColors
        ResolvedThemeMode.Oled -> OledSemanticColors
    }

    // Status- and navigation-bar icons are painted by the OS, outside the Compose tree, so they
    // cannot pick up the scheme by themselves. Light icons everywhere except the light theme.
    SystemBarsEffect(lightIcons = resolved != ResolvedThemeMode.Light)

    // Provided from here so that every component gets haptics for free and the user's on/off
    // preference is checked in exactly one place rather than at each call site.
    val haptics = rememberMuhabbetHaptics(enabled = hapticsEnabled)

    // Manrope has to be resolved here rather than in a top-level `val`: `Res.font` is @Composable.
    // The messaging text styles are derived from the resulting Typography and provided alongside it,
    // which is the whole reason MuhabbetTextStyles is a class — as an object it would have been
    // built from a family-free scale and every chat bubble would have stayed on the system font.
    val typography = rememberMuhabbetTypography()
    val textStyles = remember(typography) { MuhabbetTextStyles(typography) }

    CompositionLocalProvider(
        LocalSemanticColors provides semanticColors,
        LocalThemeMode provides resolved,
        LocalHaptics provides haptics,
        LocalTextStyles provides textStyles
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = MuhabbetShapes,
            content = content
        )
    }
}
