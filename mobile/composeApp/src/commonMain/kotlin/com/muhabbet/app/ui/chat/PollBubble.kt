package com.muhabbet.app.ui.chat

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
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetAlphas
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
import com.muhabbet.designsystem.modifier.pressable

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

    // #517. This used to draw `colorScheme.onPrimary` on everything when `isOwn` — but the bubble
    // behind it is not `primary`, it is `bubbleOwn`, a pale copper wash in light and a deep
    // copper-brown in dark. So the question, the options and the selected option were all white on
    // near-white, or near-black on dark brown: unreadable in both, exactly as reported.
    //
    // The option rows are a panel inset into a bubble, which is what `bubbleOwnInset` is. Container
    // and content arrive together and both are measured, so the selected row cannot drift from its
    // own foreground again.
    val semanticColors = LocalSemanticColors.current
    val bubble = if (isOwn) semanticColors.bubbleOwn else semanticColors.bubbleOther
    val option = if (isOwn) semanticColors.bubbleOwnInset else semanticColors.bubbleOtherInset
    val optionChosen =
        if (isOwn) semanticColors.bubbleOwnInsetSelected else semanticColors.bubbleOtherInsetSelected

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
            color = bubble.content,
            modifier = Modifier.padding(horizontal = MuhabbetSpacing.XSmall, vertical = MuhabbetSpacing.XSmall)
        )

        Spacer(Modifier.height(MuhabbetSpacing.XSmall))

        pollData.options.forEachIndexed { index, optionText ->
            val voteCount = pollResult?.votes?.getOrNull(index)?.count ?: 0
            val totalVotes = pollResult?.totalVotes ?: 0
            val fraction = if (totalVotes > 0) voteCount.toFloat() / totalVotes else 0f
            val isMyVote = pollResult?.myVote == index
            val row = if (isMyVote) optionChosen else option

            Surface(
                shape = MaterialTheme.shapes.small,
                color = row.container,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .pressable(shape = MaterialTheme.shapes.small) {
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
                            color = row.content,
                            fontWeight = if (isMyVote) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        // Only when the tallies actually arrived. Printing the 0 that stands in for
                        // "results not loaded" told the user, in the same typeface as a real result,
                        // that nobody had voted.
                        if (pollResult != null) {
                            Spacer(Modifier.width(MuhabbetSpacing.Small))
                            Text(
                                text = "$voteCount",
                                style = MaterialTheme.typography.labelSmall,
                                // No alpha. A tally is a number the user reads, and the smaller
                                // style already carries the hierarchy; fading it was costing
                                // contrast the pair had just been chosen to guarantee.
                                color = row.content
                            )
                        }
                    }
                    if (totalVotes > 0) {
                        Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            // The bar reads against its own track, and both are drawn on the row it
                            // sits in — so both come off that row's pair rather than off `primary`,
                            // which is not the colour behind them.
                            color = row.content,
                            trackColor = row.content.copy(alpha = MuhabbetAlphas.ProgressTrack)
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
                // Drawn on the bubble itself, not on an option row, so it takes the bubble's
                // secondary mark — the same one every timestamp uses, and measured against both
                // bubble colours.
                color = semanticColors.secondaryText,
                modifier = Modifier.padding(start = MuhabbetSpacing.XSmall, top = 2.dp)
            )
        }
    }
}
