package com.muhabbet.app.ui.chat

import com.muhabbet.app.platform.RecordedAudio
import com.muhabbet.designsystem.theme.MuhabbetGestures

/**
 * The voice composer's state machine (#601).
 *
 * Before this existed there were exactly two states — not recording, and recording — and the
 * second one only ever ended one way: tapping the record button again stopped it and sent it in
 * the same motion. There was no way to end a recording without sending it, which is the bug this
 * type exists to make impossible to regress: every exit from [Held] is a named, deliberate state,
 * and none of them is "sent."
 *
 * [Idle] and [Held] are rendered by the same composable call site in [MessageInputBar] — see the
 * doc on `VoiceRecordGestureButton` for why that matters. [Locked] and [Preview] are not: by the
 * time either is reached the finger is already up, so the composer is free to swap to a
 * completely different layout for them.
 */
sealed class VoiceRecordingPhase {
    /** No recording in progress. The ordinary composer — attach button, text field, mic — is shown. */
    data object Idle : VoiceRecordingPhase()

    /**
     * A finger is down on the record control. [dragX]/[dragY] are the cumulative drag offset in
     * pixels since the press started, used both to draw the "slide to cancel" feedback and, on
     * release, to decide whether the gesture ends in [Idle] (cancelled), [Preview] (stopped), or —
     * checked continuously rather than only at release — [Locked].
     */
    data class Held(val dragX: Float, val dragY: Float) : VoiceRecordingPhase()

    /**
     * The finger slid up past the lock threshold and was lifted. The recorder keeps running
     * hands-free; the composer shows explicit Send and Cancel controls rather than resuming the
     * ordinary input row, because there is no gesture left to finish — the user must tap one.
     */
    data object Locked : VoiceRecordingPhase()

    /**
     * The recording stopped — by a plain release with no cancel or lock — and the audio is being
     * held locally, unsent, for playback before the user decides. This is the state #601 was filed
     * to add: previously, ending a recording without sliding to cancel had no outcome other than
     * sending it.
     */
    data class Preview(val audio: RecordedAudio) : VoiceRecordingPhase()
}

/**
 * Whether a release at [dragX] px (the cumulative horizontal offset [VoiceRecordGestureButton]
 * reports since the press began) discards the recording rather than handing it to
 * [VoiceRecordingPhase.Preview].
 *
 * Pulled out of the release callback so the one piece of this gesture's outcome that does not
 * depend on real touch hardware — the arithmetic decision, as opposed to the drag itself — has a
 * name and a unit test. Lock is deliberately not decided here: it is evaluated continuously while
 * still held, not at release, because the finger can be lifted straight through the lock threshold.
 */
fun isVoiceRecordingCancelledAt(dragX: Float): Boolean = dragX <= -MuhabbetGestures.VoiceCancelThresholdPx
