package com.muhabbet.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing

/**
 * A verification code, as one box per digit.
 *
 * The screen previously used a single `OutlinedTextField` labelled "code", which gives no sense of
 * how long the code is, no feedback per keystroke, and — on the one screen where a user is most
 * likely to be holding a second device — no indication of progress.
 *
 * One real `BasicTextField` drives the whole thing and the boxes are decoration. Six separate fields
 * with focus-forwarding between them is the usual approach and it is the wrong one: it breaks
 * paste, breaks backspace across boundaries, and fights the platform's SMS autofill, which wants to
 * deliver the whole code to a single field.
 *
 * @param onFilled fired once the last digit lands, so the caller can submit without the user having
 *   to reach for a button. Given the code arrives by SMS, the tap after the sixth digit is pure
 *   ceremony.
 * @param masked draws a dot per entered digit instead of the digit. Off for an SMS code, which the
 *   user is reading off another screen anyway and needs to be able to check; on for a two-step PIN,
 *   which is a secret the user knows and which is entered in public (#566).
 */
@Composable
fun MuhabbetOtpField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    isError: Boolean = false,
    enabled: Boolean = true,
    masked: Boolean = false,
    onFilled: (String) -> Unit = {}
) {
    val haptics = LocalHaptics.current
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    // A shake is a scripted gesture, not a physical settle, so it is keyframed rather than sprung —
    // the one deliberate exception to the spring-by-default rule. A spring per hop would take three
    // times as long and read as a wobble.
    LaunchedEffect(isError) {
        if (isError) {
            haptics.perform(MuhabbetHapticIntent.InputRejected)
            shake.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = ShakeDurationMs
                    ShakeAmplitude at 60
                    -ShakeAmplitude * 0.75f at 120
                    ShakeAmplitude * 0.5f at 180
                    -ShakeAmplitude * 0.25f at 240
                    0f at ShakeDurationMs
                }
            )
        } else {
            shake.snapTo(0f)
        }
    }

    BasicTextField(
        value = value,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(length)
            if (digits == value) return@BasicTextField
            if (digits.length > value.length) {
                haptics.perform(MuhabbetHapticIntent.SegmentAdvanced)
            }
            onValueChange(digits)
            if (digits.length == length) onFilled(digits)
        },
        modifier = modifier
            .graphicsLayer { translationX = shake.value }
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled),
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        ),
        // The real field is never seen: it exists to own the cursor, the keyboard and the paste
        // buffer. Transparent rather than zero-sized so the touch target stays the boxes' size.
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)) {
                repeat(length) { index ->
                    OtpDigitBox(
                        digit = value.getOrNull(index),
                        isActive = isFocused && index == value.length.coerceAtMost(length - 1),
                        isError = isError,
                        masked = masked
                    )
                }
            }
        }
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun OtpDigitBox(digit: Char?, isActive: Boolean, isError: Boolean, masked: Boolean) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.error
            isActive -> MaterialTheme.colorScheme.primary
            digit != null -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = MuhabbetMotion.effectsFast(),
        label = "otpBoxBorder"
    )
    val boxShape = RoundedCornerShape(MuhabbetCorners.Medium)
    Box(
        modifier = Modifier
            .width(MuhabbetSizes.OtpBoxWidth)
            .height(MuhabbetSizes.OtpBoxHeight)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = boxShape
            )
            .border(
                width = if (isActive || isError) MuhabbetSizes.BorderActive else MuhabbetSizes.BorderHairline,
                color = borderColor,
                shape = boxShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            // Masked at the point of drawing rather than by a VisualTransformation: the real field
            // is invisible here, so there is nothing for a transformation to transform.
            text = digit?.let { if (masked) MaskGlyph else it.toString() }.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

/** U+2022 BULLET — the same glyph `PasswordVisualTransformation` uses by default. */
private const val MaskGlyph = "\u2022"

private const val ShakeAmplitude = 12f
private const val ShakeDurationMs = 320
