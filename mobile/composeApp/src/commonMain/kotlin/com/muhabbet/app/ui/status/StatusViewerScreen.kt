package com.muhabbet.app.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhabbet.app.ui.theme.MuhabbetDurations
import com.muhabbet.app.ui.theme.MuhabbetSizes
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.app.data.repository.StatusRepository
import com.muhabbet.app.ui.components.UserAvatar
import com.muhabbet.shared.dto.StatusResponse
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import kotlinx.coroutines.delay
import com.muhabbet.app.util.DateTimeFormatter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun StatusViewerScreen(
    userId: String,
    displayName: String,
    onBack: () -> Unit,
    statusRepository: StatusRepository = koinInject()
) {
    var statuses by remember { mutableStateOf<List<StatusResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentIndex by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }

    val noStatusesMsg = stringResource(Res.string.status_no_statuses)
    val loadFailedMsg = stringResource(Res.string.status_load_failed)
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Surface colors for status viewer (always dark background for immersive experience)
    val bgColor = MaterialTheme.colorScheme.scrim
    val onBgColor = MaterialTheme.colorScheme.inverseOnSurface
    val onBgDim = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
    val barBg = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.3f)
    val barFg = MaterialTheme.colorScheme.inverseOnSurface

    LaunchedEffect(userId) {
        try {
            val groups = statusRepository.getContactStatuses()
            val userGroup = groups.firstOrNull { it.userId == userId }
            statuses = userGroup?.statuses ?: emptyList()
        } catch (_: Exception) {
            errorMsg = loadFailedMsg
        }
        isLoading = false
    }

    // Auto-advance timer with pause support
    LaunchedEffect(currentIndex, statuses.size, isPaused) {
        if (statuses.isEmpty() || isPaused) return@LaunchedEffect
        progress = 0f
        val totalMs = MuhabbetDurations.StatusDisplayMs
        val stepMs = MuhabbetDurations.StatusProgressTickMs
        val steps = totalMs / stepMs
        for (i in 0..steps) {
            if (isPaused) return@LaunchedEffect
            progress = i.toFloat() / steps
            delay(stepMs)
        }
        if (currentIndex < statuses.lastIndex) {
            currentIndex++
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = onBgColor
            )
        } else if (errorMsg != null || statuses.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = errorMsg ?: noStatusesMsg,
                    color = onBgColor,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(MuhabbetSpacing.Large))
                Text(
                    text = stringResource(Res.string.cancel),
                    color = onBgDim,
                    modifier = Modifier.clickable { onBack() }
                )
            }
        } else {
            val currentStatus = statuses[currentIndex]

            // Tap left/right + long-press to pause
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPaused = true
                                tryAwaitRelease()
                                isPaused = false
                            },
                            onTap = { offset ->
                                val halfWidth = size.width / 2
                                if (offset.x < halfWidth) {
                                    if (currentIndex > 0) currentIndex-- else onBack()
                                } else {
                                    if (currentIndex < statuses.lastIndex) currentIndex++ else onBack()
                                }
                            }
                        )
                    }
            ) {}

            // Top: progress bars + user info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MuhabbetSpacing.Large, start = MuhabbetSpacing.Small, end = MuhabbetSpacing.Small)
            ) {
                // Segmented progress bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    statuses.forEachIndexed { index, _ ->
                        val segmentProgress = when {
                            index < currentIndex -> 1f
                            index == currentIndex -> progress
                            else -> 0f
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(barBg)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(segmentProgress)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(barFg)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(MuhabbetSpacing.Small))

                // User info row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                            tint = onBgColor
                        )
                    }
                    UserAvatar(
                        avatarUrl = null,
                        displayName = displayName,
                        size = MuhabbetSizes.AvatarXSmall
                    )
                    Spacer(Modifier.width(MuhabbetSpacing.Medium))
                    Column {
                        Text(
                            text = displayName,
                            color = onBgColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = formatStatusTime(currentStatus.createdAt),
                            color = onBgDim,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Center: status content
            if (currentStatus.mediaUrl != null) {
                coil3.compose.AsyncImage(
                    model = currentStatus.mediaUrl,
                    contentDescription = stringResource(Res.string.status_my),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }

            val content = currentStatus.content
            if (content != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = MuhabbetSpacing.XXLarge, vertical = MuhabbetSpacing.XXLarge)
                ) {
                    Text(
                        text = content,
                        color = onBgColor,
                        fontSize = if (currentStatus.mediaUrl != null) 16.sp else 24.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        style = if (currentStatus.mediaUrl != null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun formatStatusTime(epochMillis: Long): String =
    DateTimeFormatter.formatTime(epochMillis)
