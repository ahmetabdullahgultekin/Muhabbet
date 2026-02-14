package com.muhabbet.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.muhabbet.app.ui.profile.firstGrapheme

@Composable
fun UserAvatar(
    avatarUrl: String?,
    displayName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    isGroup: Boolean = false
) {
    Surface(
        modifier = modifier.size(size).clip(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                if (isGroup) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "Group",
                        modifier = Modifier.size(size * 0.5f),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    val fontSize = when {
                        size >= 96.dp -> 36.sp
                        size >= 80.dp -> 28.sp
                        size >= 48.dp -> 18.sp
                        else -> 14.sp
                    }
                    Text(
                        text = firstGrapheme(displayName),
                        fontSize = fontSize,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
