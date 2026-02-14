package com.muhabbet.app.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.LocationData
import kotlinx.serialization.json.Json

private val locationJson = Json { ignoreUnknownKeys = true }

@Composable
fun LocationBubble(
    content: String,
    isOwn: Boolean,
    modifier: Modifier = Modifier
) {
    val locationData = remember(content) {
        try {
            locationJson.decodeFromString<LocationData>(content)
        } catch (_: Exception) {
            null
        }
    }

    if (locationData == null) return

    Row(
        modifier = modifier.padding(horizontal = MuhabbetSpacing.Small, vertical = MuhabbetSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = if (isOwn) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(MuhabbetSpacing.Small))
        Column {
            locationData.label?.let { labelText ->
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOwn) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "%.5f, %.5f".format(locationData.latitude, locationData.longitude),
                style = MaterialTheme.typography.bodySmall,
                color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
