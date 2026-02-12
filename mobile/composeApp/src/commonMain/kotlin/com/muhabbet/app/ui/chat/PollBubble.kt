package com.muhabbet.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.shared.dto.PollData
import com.muhabbet.shared.dto.PollResultResponse
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject

private val pollJson = Json { ignoreUnknownKeys = true }

@Composable
fun PollBubble(
    messageId: String,
    pollContent: String,
    isOwn: Boolean,
    modifier: Modifier = Modifier,
    messageRepository: MessageRepository = koinInject()
) {
    val scope = rememberCoroutineScope()
    var pollResult by remember { mutableStateOf<PollResultResponse?>(null) }
    val pollData = remember(pollContent) {
        try {
            pollJson.decodeFromString<PollData>(pollContent)
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(messageId) {
        try {
            pollResult = messageRepository.getPollResults(messageId)
        } catch (_: Exception) { }
    }

    if (pollData == null) return

    Column(modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(
            text = pollData.question,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (isOwn) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        Spacer(Modifier.height(4.dp))

        pollData.options.forEachIndexed { index, optionText ->
            val voteCount = pollResult?.votes?.getOrNull(index)?.count ?: 0
            val totalVotes = pollResult?.totalVotes ?: 0
            val fraction = if (totalVotes > 0) voteCount.toFloat() / totalVotes else 0f
            val isMyVote = pollResult?.myVote == index

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isMyVote) {
                    if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.primaryContainer
                } else {
                    if (isOwn) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clickable {
                        scope.launch {
                            try {
                                pollResult = messageRepository.votePoll(messageId, index)
                            } catch (_: Exception) { }
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = optionText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isOwn) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isMyVote) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$voteCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    if (totalVotes > 0) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.primary,
                            trackColor = if (isOwn) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        if ((pollResult?.totalVotes ?: 0) > 0) {
            Text(
                text = "${pollResult?.totalVotes ?: 0} votes",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}
