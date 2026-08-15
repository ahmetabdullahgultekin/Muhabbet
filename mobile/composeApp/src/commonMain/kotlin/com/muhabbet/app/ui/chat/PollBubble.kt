package com.muhabbet.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.PollData
import com.muhabbet.shared.dto.PollResultResponse
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.error_action_failed
import com.muhabbet.composeapp.generated.resources.poll_vote_count
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
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
    // A bubble has no snackbar host, so a failed vote is reported inline under the poll.
    var voteFailed by remember(messageId) { mutableStateOf(false) }
    val voteFailedText = stringResource(Res.string.error_action_failed)
    val pollData = remember(pollContent) {
        try {
            pollJson.decodeFromString<PollData>(pollContent)
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(messageId) {
        runCatchingCancellable { pollResult = messageRepository.getPollResults(messageId) }
            .onFailure { e ->
                // Best-effort enrichment: the poll question and options still render from the message
                // body, only the tallies are missing. Not worth interrupting the chat for.
                Log.w("PollBubble", "Failed to load poll results for $messageId: ${e.message}")
            }
    }

    if (pollData == null) return

    Column(modifier = modifier.padding(horizontal = MuhabbetSpacing.XSmall, vertical = MuhabbetSpacing.XSmall)) {
        Text(
            text = pollData.question,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (isOwn) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MuhabbetSpacing.XSmall, vertical = MuhabbetSpacing.XSmall)
        )

        Spacer(Modifier.height(MuhabbetSpacing.XSmall))

        pollData.options.forEachIndexed { index, optionText ->
            val voteCount = pollResult?.votes?.getOrNull(index)?.count ?: 0
            val totalVotes = pollResult?.totalVotes ?: 0
            val fraction = if (totalVotes > 0) voteCount.toFloat() / totalVotes else 0f
            val isMyVote = pollResult?.myVote == index

            Surface(
                shape = MaterialTheme.shapes.small,
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
                            // Clear the previous failure the moment the user retries: tapping an
                            // option again IS the retry, and leaving the red line up during it made
                            // the error look permanent and the poll look broken.
                            voteFailed = false
                            // runCatchingCancellable: this bubble lives in a LazyColumn, so
                            // scrolling it off screen mid-vote cancels the request. A plain catch
                            // read that as a failed vote and left the error behind on a bubble the
                            // user had already scrolled past.
                            runCatchingCancellable { pollResult = messageRepository.votePoll(messageId, index) }
                                .onFailure { e ->
                                    // Nothing moves on the bar when a vote fails, so the tap reads
                                    // as if it registered. Say otherwise.
                                    Log.e("PollBubble", "Failed to vote on poll $messageId", e)
                                    voteFailed = true
                                }
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(MuhabbetSpacing.Small)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = optionText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isOwn) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isMyVote) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(MuhabbetSpacing.Small))
                        Text(
                            text = "$voteCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    if (totalVotes > 0) {
                        Spacer(Modifier.height(MuhabbetSpacing.XSmall))
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

        if (voteFailed) {
            Text(
                text = voteFailedText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = MuhabbetSpacing.XSmall, top = 2.dp)
            )
        }

        val totalVoteCount = pollResult?.totalVotes ?: 0
        if (totalVoteCount > 0) {
            Text(
                text = pluralStringResource(Res.plurals.poll_vote_count, totalVoteCount, totalVoteCount),
                style = MaterialTheme.typography.labelSmall,
                color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = MuhabbetSpacing.XSmall, top = 2.dp)
            )
        }
    }
}
