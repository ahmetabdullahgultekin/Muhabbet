package com.muhabbet.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * What the app is telling the hand — not how.
 *
 * Call sites name an intent; the mapping to a platform effect lives in one place, so retuning the
 * whole app's feel is a single file. Naming the raw `HapticFeedbackType` at 40 call sites would
 * make that impossible, and would also let "confirm" and "reject" drift into meaning whatever the
 * nearest constant happened to be.
 *
 * The app had zero haptics before this.
 */
enum class MuhabbetHapticIntent {
    /** A message left the device. */
    MessageSent,

    /** A reaction was attached to a message. */
    ReactionApplied,

    /** Long-press opened a context menu on a bubble or a conversation row. */
    ItemLongPressed,

    /** A drag crossed the threshold at which releasing would do something. */
    SwipeArmed,

    /** The armed drag was released and the action fired. */
    SwipeCommitted,

    /** Bottom-navigation tab changed. */
    TabSwitched,

    ToggleOn,
    ToggleOff,

    CallAccepted,
    CallDeclined,

    /** Pull-to-refresh passed its trigger distance. */
    RefreshTriggered,

    /** A destructive confirmation was accepted — delete, leave group, revoke device. */
    DestructiveConfirmed,

    /** Input was rejected: a wrong OTP, a failed action, a validation error. */
    InputRejected,

    /** One step of a sequence advanced — an OTP digit, a status story. */
    SegmentAdvanced,
}

/**
 * Performs haptic feedback for a [MuhabbetHapticIntent], honouring the user's preference.
 *
 * Deliberately NOT applied to: scrolling, individual keystrokes, incoming messages (that is the
 * notification channel's job), back navigation, or anything on a repeating animation. Over-haptics
 * is the fastest way to make an app feel cheap, and it costs battery.
 */
@Immutable
class MuhabbetHaptics internal constructor(
    private val delegate: HapticFeedback?,
    private val enabled: Boolean
) {
    fun perform(intent: MuhabbetHapticIntent) {
        if (!enabled) return
        delegate?.performHapticFeedback(intent.toFeedbackType())
    }

    companion object {
        /** Used before a theme is in scope, and in previews. */
        val NoOp: MuhabbetHaptics = MuhabbetHaptics(delegate = null, enabled = false)
    }
}

/*
 * Every constant below was verified to resolve in Compose Multiplatform 1.11.1 for all three
 * targets, so none of this needs a fallback. On Android these map to HapticFeedbackConstants with
 * internal API gating: below a constant's API level the platform degrades rather than throwing.
 */
private fun MuhabbetHapticIntent.toFeedbackType(): HapticFeedbackType = when (this) {
    MuhabbetHapticIntent.MessageSent -> HapticFeedbackType.Confirm
    MuhabbetHapticIntent.ReactionApplied -> HapticFeedbackType.Confirm
    MuhabbetHapticIntent.ItemLongPressed -> HapticFeedbackType.LongPress
    MuhabbetHapticIntent.SwipeArmed -> HapticFeedbackType.GestureThresholdActivate
    MuhabbetHapticIntent.SwipeCommitted -> HapticFeedbackType.GestureEnd
    MuhabbetHapticIntent.TabSwitched -> HapticFeedbackType.SegmentTick
    MuhabbetHapticIntent.ToggleOn -> HapticFeedbackType.ToggleOn
    MuhabbetHapticIntent.ToggleOff -> HapticFeedbackType.ToggleOff
    MuhabbetHapticIntent.CallAccepted -> HapticFeedbackType.Confirm
    MuhabbetHapticIntent.CallDeclined -> HapticFeedbackType.Reject
    MuhabbetHapticIntent.RefreshTriggered -> HapticFeedbackType.GestureThresholdActivate
    MuhabbetHapticIntent.DestructiveConfirmed -> HapticFeedbackType.Reject
    MuhabbetHapticIntent.InputRejected -> HapticFeedbackType.Reject
    MuhabbetHapticIntent.SegmentAdvanced -> HapticFeedbackType.SegmentTick
}

/**
 * The live haptics instance. Provided by [MuhabbetTheme]; defaults to a no-op so a component used
 * outside the theme (or in a preview) is silent rather than crashing.
 */
val LocalHaptics = staticCompositionLocalOf { MuhabbetHaptics.NoOp }

/**
 * Builds the instance the theme provides.
 *
 * @param enabled the user's "haptic feedback" preference. Checked here rather than at call sites so
 *   that turning haptics off is genuinely global — one branch, not forty.
 */
@Composable
internal fun rememberMuhabbetHaptics(enabled: Boolean): MuhabbetHaptics {
    val platform = LocalHapticFeedback.current
    return remember(platform, enabled) { MuhabbetHaptics(platform, enabled) }
}
