package com.muhabbet.app.ui.chat

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.shared.dto.MessageInfoResponse
import com.muhabbet.shared.dto.RecipientDeliveryInfo
import kotlinx.datetime.toLocalDateTime
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
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Message content preview
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Text(
                                text = data.content.take(200).ifBlank { data.contentType },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 4
                            )
                            Spacer(Modifier.height(8.dp))
                            InfoRow(
                                label = stringResource(Res.string.message_info_sent_at),
                                value = formatTimestamp(data.sentAt)
                            )
                        }
                        HorizontalDivider()
                    }

                    // Recipients section
                    if (data.recipients.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(Res.string.message_info_delivered_at),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }

                        items(data.recipients, key = { it.userId }) { recipient ->
                            RecipientStatusRow(recipient)
                            HorizontalDivider()
                        }
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RecipientStatusRow(recipient: RecipientDeliveryInfo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        val (icon, tint) = when (recipient.status) {
            "READ" -> Icons.Default.DoneAll to Color(0xFF4FC3F7)
            "DELIVERED" -> Icons.Default.DoneAll to MaterialTheme.colorScheme.onSurfaceVariant
            else -> Icons.Default.Check to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(icon, contentDescription = recipient.status, modifier = Modifier.size(18.dp), tint = tint)
    }
}

private fun formatTimestamp(isoString: String): String {
    return try {
        val instant = kotlinx.datetime.Instant.parse(isoString)
        val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
        val dt = instant.toLocalDateTime(tz)
        "${dt.dayOfMonth.toString().padStart(2, '0')}.${dt.monthNumber.toString().padStart(2, '0')}.${dt.year} " +
                "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    } catch (_: Exception) {
        isoString
    }
}
