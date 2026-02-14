package com.muhabbet.app.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muhabbet.app.ui.theme.MuhabbetElevation
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.app.util.DateTimeFormatter
import kotlinx.datetime.Instant

@Composable
fun DateSeparatorPill(date: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = MuhabbetSpacing.Small),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            shadowElevation = MuhabbetElevation.Level1
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MuhabbetSpacing.Medium, vertical = MuhabbetSpacing.XSmall)
            )
        }
    }
}

fun formatDateForSeparator(instant: Instant, todayLabel: String = "Bugün", yesterdayLabel: String = "Dün"): String =
    DateTimeFormatter.formatDateSeparator(instant, todayLabel, yesterdayLabel)
