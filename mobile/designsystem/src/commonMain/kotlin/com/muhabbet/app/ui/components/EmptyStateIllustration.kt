package com.muhabbet.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.muhabbet.app.ui.theme.MuhabbetSpacing

@Composable
fun EmptyChatsIllustration(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val transition = rememberInfiniteTransition(label = "empty_state")
    val floatAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2

            // Background circle
            drawCircle(
                color = surfaceVariant,
                radius = size.minDimension / 2.2f,
                center = Offset(cx, cy)
            )

            // Chat bubble 1 (left, received)
            val bubble1Y = cy - 14 + floatAnim
            drawRoundRect(
                color = primary.copy(alpha = 0.3f),
                topLeft = Offset(cx - 40, bubble1Y - 16),
                size = Size(50f, 28f),
                cornerRadius = CornerRadius(12f, 12f)
            )

            // Three dots in bubble 1
            for (i in 0..2) {
                drawCircle(
                    color = primary.copy(alpha = 0.6f),
                    radius = 3f,
                    center = Offset(cx - 26 + i * 12, bubble1Y)
                )
            }

            // Chat bubble 2 (right, sent)
            val bubble2Y = cy + 10 - floatAnim * 0.5f
            drawRoundRect(
                color = primary.copy(alpha = 0.7f),
                topLeft = Offset(cx - 10, bubble2Y - 14),
                size = Size(48f, 26f),
                cornerRadius = CornerRadius(12f, 12f)
            )

            // Lines inside bubble 2
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(cx - 2, bubble2Y - 4),
                end = Offset(cx + 30, bubble2Y - 4),
                strokeWidth = 2.5f
            )
            drawLine(
                color = Color.White.copy(alpha = 0.4f),
                start = Offset(cx - 2, bubble2Y + 4),
                end = Offset(cx + 20, bubble2Y + 4),
                strokeWidth = 2.5f
            )

            // Heart icon
            drawCircle(
                color = secondary.copy(alpha = 0.5f),
                radius = 6f,
                center = Offset(cx + 30, cy - 30 + floatAnim * 0.7f)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(MuhabbetSpacing.Small))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptySearchIllustration(
    title: String,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(96.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2

            // Magnifying glass circle
            drawCircle(
                color = surfaceVariant,
                radius = 30f,
                center = Offset(cx - 8, cy - 8)
            )
            drawCircle(
                color = primary.copy(alpha = 0.4f),
                radius = 30f,
                center = Offset(cx - 8, cy - 8),
                style = Stroke(width = 4f)
            )

            // Handle
            drawLine(
                color = primary.copy(alpha = 0.4f),
                start = Offset(cx + 14, cy + 14),
                end = Offset(cx + 32, cy + 32),
                strokeWidth = 5f
            )
        }

        Spacer(Modifier.height(MuhabbetSpacing.Large))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
