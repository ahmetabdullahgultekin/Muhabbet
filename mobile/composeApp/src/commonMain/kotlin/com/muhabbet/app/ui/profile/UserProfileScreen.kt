package com.muhabbet.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.shared.model.UserProfile
import kotlinx.datetime.toLocalDateTime
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    onBack: () -> Unit,
    conversationRepository: ConversationRepository = koinInject()
) {
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val errorMsg = stringResource(Res.string.profile_load_failed)

    LaunchedEffect(userId) {
        try {
            profile = conversationRepository.getUserProfile(userId)
        } catch (e: Exception) {
            error = errorMsg
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.profile_view_title)) },
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
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        } else if (profile != null) {
            val p = profile!!
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(32.dp))

                // Large avatar
                com.muhabbet.app.ui.components.UserAvatar(
                    avatarUrl = p.avatarUrl,
                    displayName = p.displayName ?: "?",
                    size = 96.dp
                )

                Spacer(Modifier.height(16.dp))

                // Display name
                Text(
                    text = p.displayName ?: stringResource(Res.string.unknown),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                // Online status
                if (p.isOnline) {
                    Text(
                        text = stringResource(Res.string.chat_online),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4CAF50)
                    )
                } else if (p.lastSeenAt != null) {
                    val time = formatLastSeen(p.lastSeenAt!!)
                    Text(
                        text = stringResource(Res.string.profile_last_seen, time),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()

                // Phone number
                ProfileInfoRow(
                    label = stringResource(Res.string.profile_phone),
                    value = p.phoneNumber
                )

                HorizontalDivider()

                // About
                ProfileInfoRow(
                    label = stringResource(Res.string.profile_about_label),
                    value = p.about ?: stringResource(Res.string.profile_no_about)
                )

                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

internal fun firstGrapheme(text: String): String {
    if (text.isEmpty()) return "?"
    val ch = text[0]
    // Simple ASCII letter — fast path
    if (ch.code < 0x80) return ch.uppercase()
    // Surrogate pair (emoji or supplementary char) — take the pair
    if (ch.isHighSurrogate() && text.length > 1 && text[1].isLowSurrogate()) {
        var end = 2
        // Walk over ZWJ, variation selectors, skin tones
        while (end < text.length) {
            val c = text[end]
            // ZWJ (U+200D)
            if (c == '\u200D') {
                end++
                // Include the next character (or surrogate pair) after ZWJ
                if (end < text.length) {
                    end++
                    if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) {
                        end++
                    }
                }
            }
            // Variation Selectors (U+FE0E, U+FE0F)
            else if (c == '\uFE0F' || c == '\uFE0E') {
                end++
            }
            // Skin tone modifiers (U+1F3FB-1F3FF) are surrogate pairs: D83C DFxx
            else if (c == '\uD83C' && end + 1 < text.length) {
                val low = text[end + 1]
                if (low.code in 0xDFFB..0xDFFF) {
                    end += 2
                } else break
            }
            else break
        }
        return text.substring(0, end)
    }
    // Regular non-ASCII character (Turkish, Arabic, etc.)
    return ch.toString()
}

private fun formatLastSeen(instant: kotlinx.datetime.Instant): String {
    return try {
        val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
        val dt = instant.toLocalDateTime(tz)
        val now = kotlinx.datetime.Clock.System.now().toLocalDateTime(tz)
        if (dt.date == now.date) {
            "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
        } else {
            "${dt.dayOfMonth.toString().padStart(2, '0')}.${dt.monthNumber.toString().padStart(2, '0')} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
        }
    } catch (_: Exception) {
        ""
    }
}
