package com.muhabbet.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.muhabbet.app.ui.theme.LocalSemanticColors
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import coil3.compose.AsyncImage
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.ui.components.UserAvatar
import com.muhabbet.shared.dto.MessageInfoResponse
import com.muhabbet.shared.dto.RecipientDeliveryInfo
import com.muhabbet.app.ui.components.SectionHeader
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInfoScreen(
    messageId: String,
    onBack: () -> Unit,
    messageRepository: MessageRepository = koinInject()
) {
    var info by remember { mutableStateOf<MessageInfoResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(messageId) {
        try {
            info = messageRepository.getMessageInfo(messageId)
        } catch (e: Exception) {
            error = e.message
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.message_info_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            info != null -> {
                val data = info!!
                val readRecipients = data.recipients.filter { it.status == "READ" }
                val deliveredRecipients = data.recipients.filter { it.status == "DELIVERED" }
                val sentRecipients = data.recipients.filter { it.status != "READ" && it.status != "DELIVERED" }

                AnimatedVisibility(visible = true, enter = fadeIn()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding)
                    ) {
                        // ── Message preview card ──
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MuhabbetSpacing.Large),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(MuhabbetSpacing.Large)) {
                                    // Media preview (image/video thumbnail)
                                    val previewUrl = data.thumbnailUrl ?: data.mediaUrl
                                    if (previewUrl != null && data.contentType in listOf("IMAGE", "VIDEO")) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = previewUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            if (data.contentType == "VIDEO") {
                                                Icon(
                                                    Icons.Default.Image, // play overlay
                                                    contentDescription = null,
                                                    modifier = Modifier.size(40.dp),
                                                    tint = Color.White.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(MuhabbetSpacing.Medium))
                                    }

                                    // Content type badge for non-text
                                    if (data.contentType != "TEXT") {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Image,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = data.contentType,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(Modifier.height(MuhabbetSpacing.Small))
                                    }

                                    if (data.content.isNotBlank()) {
                                        Text(
                                            text = data.content.take(200),
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(Modifier.height(MuhabbetSpacing.Medium))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(MuhabbetSpacing.XSmall))
                                        Text(
                                            text = "${stringResource(Res.string.message_info_sent_at)}: ${formatTimestamp(data.sentAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // ── Read By section ──
                        if (readRecipients.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = stringResource(Res.string.message_info_read_by),
                                    dotColor = LocalSemanticColors.current.statusRead
                                )
                            }
                            items(readRecipients, key = { "read_${it.userId}" }) { recipient ->
                                RecipientRow(recipient, LocalSemanticColors.current.statusRead)
                            }
                        }

                        // ── Delivered To section ──
                        if (deliveredRecipients.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = stringResource(Res.string.message_info_delivered_to),
                                    dotColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(deliveredRecipients, key = { "del_${it.userId}" }) { recipient ->
                                RecipientRow(recipient, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // ── Sent only (waiting) ──
                        if (sentRecipients.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = stringResource(Res.string.message_info_waiting),
                                    dotColor = MaterialTheme.colorScheme.outline
                                )
                            }
                            items(sentRecipients, key = { "sent_${it.userId}" }) { recipient ->
                                RecipientRow(recipient, MaterialTheme.colorScheme.outline)
                            }
                        }

                        // Empty state: no recipients
                        if (data.recipients.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(MuhabbetSpacing.XXLarge),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(Modifier.height(MuhabbetSpacing.Small))
                                    Text(
                                        text = stringResource(Res.string.message_info_not_sent),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        item { Spacer(Modifier.height(MuhabbetSpacing.XLarge)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipientRow(recipient: RecipientDeliveryInfo, statusColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timeline dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.5f))
        )
        Spacer(Modifier.width(10.dp))

        // Avatar
        UserAvatar(
            avatarUrl = recipient.avatarUrl,
            displayName = recipient.displayName ?: recipient.userId.take(8),
            size = 40.dp
        )
        Spacer(Modifier.width(MuhabbetSpacing.Medium))

        // Name and time
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recipient.displayName ?: recipient.userId.take(8),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (recipient.updatedAt != null) {
                Text(
                    text = formatTimestamp(recipient.updatedAt!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Status icon
        val (icon, tint) = when (recipient.status) {
            "READ" -> Icons.Default.DoneAll to LocalSemanticColors.current.statusRead
            "DELIVERED" -> Icons.Default.DoneAll to MaterialTheme.colorScheme.onSurfaceVariant
            else -> Icons.Default.Check to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(icon, contentDescription = recipient.status, modifier = Modifier.size(18.dp), tint = tint)
    }
}

private fun formatTimestamp(isoString: String): String =
    DateTimeFormatter.formatFullTimestamp(isoString)
