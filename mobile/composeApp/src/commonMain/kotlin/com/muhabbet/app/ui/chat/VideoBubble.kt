package com.muhabbet.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.muhabbet.designsystem.Muhabbet

@Composable
fun VideoBubble(
    thumbnailUrl: String?,
    mediaUrl: String?,
    durationSeconds: Int?,
    isOwn: Boolean,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalSemanticColors.current
    var isPlaying by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .clickable {
                    mediaUrl?.let { onVideoClick(it) }
                },
            contentAlignment = Alignment.Center
        ) {
            // Thumbnail background or placeholder
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = stringResource(Res.string.video_message),
                    modifier = Modifier.size(100.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            if (isOwn) semanticColors.bubbleOwn.container
                            else semanticColors.bubbleOther.container,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Muhabbet.icons.Video,
                        contentDescription = stringResource(Res.string.video_message),
                        tint = if (isOwn) semanticColors.bubbleOwn.content
                        else semanticColors.bubbleOther.content,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Progress ring border
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(100.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                strokeWidth = 3.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )

            // Play button overlay
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Muhabbet.icons.Play,
                        contentDescription = stringResource(Res.string.video_play),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(MuhabbetSizes.IconMedium)
                    )
                }
            }

            // Duration label
            if (durationSeconds != null) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = formatDuration(durationSeconds),
                        style = Muhabbet.text.ChatMeta,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
