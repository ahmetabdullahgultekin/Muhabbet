package com.muhabbet.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.breathing
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The record button's press-hold-drag gesture, and the one composable that owns it.
 *
 * This is called from the exact same slot in [MessageInputBar] whether [phase] is
 * [VoiceRecordingPhase.Idle] or [VoiceRecordingPhase.Held] — never swapped out for a structurally
 * different composable between the two. That is deliberate and load-bearing, not a style choice:
 * [Modifier.pointerInput] keeps its gesture-tracking coroutine alive only as long as the node it is
 * attached to stays in the composition. If the composer instead rendered a plain `onClick` mic
 * button while idle and swapped to a different "recording in progress" composable the instant a
 * press began — which is exactly what the pre-#601 code did with `isRecording` — the press that
 * triggers that swap tears down the very gesture that just started, and every drag after the first
 * frame goes untracked. So the icon, size, colour and position change with [phase]; the node itself
 * does not. Once a gesture ends (locked, or released), the caller is free to swap to a completely
 * different layout — see [VoiceRecordingPhase] — because there is no live pointer stream left to lose.
 *
 * The gesture itself is hand-rolled rather than [androidx.compose.foundation.gestures.detectDragGestures]
 * because that starts tracking only after the touch crosses a slop distance, so a press released
 * before any movement would never call `onDragStart` at all — meaning the shortest possible voice
 * message (tap, release immediately) would never start recording. Starting on [awaitFirstDown]
 * itself is what WhatsApp/Telegram/Signal all do, and it is the only choice under which a plain tap
 * reliably records anything.
 *
 * @param onPressStart called the instant a finger goes down. Returns whether a recording actually
 *   started — false when the microphone permission is not yet granted, in which case the gesture is
 *   abandoned here (the caller is expected to have kicked off the permission request instead) and
 *   this press produces no recording at all.
 * @param onDragUpdate the live cumulative offset from the press point, in px, while still held.
 * @param onLocked fired the instant the drag crosses the lock threshold — mid-gesture, not at
 *   release, because the entire point of locking is that the finger can be lifted afterwards without
 *   ending the recording.
 * @param onReleased fired when the finger lifts without ever crossing the lock threshold. The
 *   caller decides cancel vs. stop from the final offset — this button does not decide outcomes,
 *   only reports the gesture.
 */
@Composable
fun VoiceRecordGestureButton(
    phase: VoiceRecordingPhase,
    onPressStart: () -> Boolean,
    onDragUpdate: (dragX: Float, dragY: Float) -> Unit,
    onLocked: () -> Unit,
    onReleased: (dragX: Float, dragY: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = Muhabbet.haptics
    val cancelThresholdPx = Muhabbet.gestures.VoiceCancelThresholdPx
    val lockThresholdPx = Muhabbet.gestures.VoiceLockThresholdPx

    val held = phase as? VoiceRecordingPhase.Held
    val isHeld = held != null
    val dragX = held?.dragX ?: 0f
    val dragY = held?.dragY ?: 0f

    val recordDescription = stringResource(Res.string.chat_voice_message)
    val recordingDescription = stringResource(Res.string.voice_recording_in_progress)

    Box(
        modifier = modifier
            .size(MuhabbetSizes.MinTouchTarget)
            // Slides left with the finger, capped a little past the cancel line so it never
            // travels off under a fast flick — the hint row communicates "further than this does
            // not matter" by fully fading out at the same distance.
            .offset {
                IntOffset(dragX.coerceIn(-cancelThresholdPx * 1.2f, 0f).roundToInt(), 0)
            }
            .breathing(enabled = isHeld)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!onPressStart()) return@awaitEachGesture

                    var totalX = 0f
                    var totalY = 0f
                    var cancelArmed = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            onReleased(totalX, totalY)
                            break
                        }
                        val delta = change.positionChange()
                        change.consume()
                        totalX += delta.x
                        totalY += delta.y

                        if (totalY <= -lockThresholdPx) {
                            haptics.perform(MuhabbetHapticIntent.SwipeCommitted)
                            onLocked()
                            break
                        }

                        val nowArmed = totalX <= -cancelThresholdPx
                        if (nowArmed && !cancelArmed) {
                            cancelArmed = true
                            haptics.perform(MuhabbetHapticIntent.SwipeArmed)
                        } else if (!nowArmed && cancelArmed) {
                            cancelArmed = false
                        }
                        onDragUpdate(totalX, totalY)
                    }
                }
            }
            .background(
                color = if (isHeld) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // A hint that sliding up locks the recording. Fixed distance above the button rather than
        // tracking dragY 1:1 — the button itself already gives full-resolution feedback via its own
        // position and colour; this only needs to say "this direction, roughly this far."
        if (isHeld) {
            val lockProgress = (-dragY / lockThresholdPx).coerceIn(0f, 1f)
            Icon(
                imageVector = Muhabbet.icons.Lock,
                // Decorative: a screen-reader user cannot perform this drag gesture in the first
                // place, so naming a hint they cannot act on would only add noise. The recording
                // itself is already announced via the button's own contentDescription below.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = MinLockHintAlpha + (1f - MinLockHintAlpha) * lockProgress),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -MuhabbetSizes.MinTouchTarget)
                    .size(MuhabbetSizes.IconMedium)
            )
        }

        Icon(
            imageVector = Muhabbet.icons.Mic,
            contentDescription = if (isHeld) recordingDescription else recordDescription,
            tint = if (isHeld) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(MuhabbetSizes.IconMedium)
        )
    }
}

/** How faded the lock hint sits at rest, before any upward drag — never fully invisible, since it
 *  is the only clue that locking is possible at all. */
private const val MinLockHintAlpha = 0.35f

/**
 * Replaces the text field while [VoiceRecordingPhase.Held]: an elapsed-time readout and the
 * "slide to cancel" hint, the latter fading out as the drag approaches the cancel threshold so its
 * disappearance itself signals "you are about to let go of this."
 */
@Composable
internal fun RecordingHintRow(recordingSeconds: Int, dragX: Float, modifier: Modifier = Modifier) {
    val cancelProgress = (-dragX / Muhabbet.gestures.VoiceCancelThresholdPx).coerceIn(0f, 1f)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        RecordingDot()
        Spacer(Modifier.width(MuhabbetSpacing.Small))
        Text(
            text = DateTimeFormatter.formatDuration(recordingSeconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer { alpha = 1f - cancelProgress }
        ) {
            Icon(
                imageVector = Muhabbet.icons.Back,
                contentDescription = null,
                modifier = Modifier.size(MuhabbetSizes.IconSmall),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(MuhabbetSpacing.XSmall))
            Text(
                text = stringResource(Res.string.voice_slide_to_cancel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A plain recording indicator dot, shared by the held-drag hint row and the locked bar. */
@Composable
internal fun RecordingDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(MuhabbetSizes.IndicatorDot)
            .background(MaterialTheme.colorScheme.error, CircleShape)
    )
}
