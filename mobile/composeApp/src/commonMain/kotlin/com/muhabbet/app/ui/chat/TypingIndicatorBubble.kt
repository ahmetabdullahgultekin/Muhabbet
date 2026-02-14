package com.muhabbet.app.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.muhabbet.app.ui.theme.MuhabbetElevation
import com.muhabbet.app.ui.theme.MuhabbetSpacing

@Composable
fun TypingIndicatorBubble(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0f at 0
                -6f at 200
                0f at 400
                0f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )

    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0f at 150
                -6f at 350
                0f at 550
                0f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )

    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0f at 300
                -6f at 500
                0f at 700
                0f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Surface(
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 4.dp,
            bottomEnd = 16.dp
        ),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = MuhabbetElevation.Level1,
        shadowElevation = MuhabbetElevation.Level1,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BouncingDot(offset = dot1Offset)
            BouncingDot(offset = dot2Offset)
            BouncingDot(offset = dot3Offset)
        }
    }
}

@Composable
private fun BouncingDot(offset: Float) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .offset(y = offset.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    )
}
