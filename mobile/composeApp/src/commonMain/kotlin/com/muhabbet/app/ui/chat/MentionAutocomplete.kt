package com.muhabbet.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muhabbet.app.ui.theme.LocalSemanticColors
import com.muhabbet.app.ui.theme.MuhabbetElevation
import com.muhabbet.app.ui.theme.MuhabbetSizes
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.mention_member_avatar
import com.muhabbet.shared.dto.ParticipantResponse
import com.muhabbet.shared.model.MentionRef
import org.jetbrains.compose.resources.stringResource

/**
 * Result of replacing a trailing `@query` token with a chosen member's `@DisplayName` token.
 *
 * @property text the new composer text with the token inserted.
 * @property mention the structured [MentionRef] (userId + offset into [text] + length of the token)
 *   recorded so it can ride on the outgoing `SendMessage` when [com.muhabbet.app.config.MentionsConfig.ENABLED].
 * @property cursor the char index just past the inserted token (token + trailing space).
 */
data class MentionInsertion(
    val text: String,
    val mention: MentionRef,
    val cursor: Int
)

/**
 * Detect a trailing `@<query>` token at the end of [text] (the simple WhatsApp-style case: the
 * autocomplete tracks the token the user is currently typing at the cursor/end of the field).
 *
 * Returns the query (text after `@`, may be empty right after typing `@`) when the field ends with an
 * `@` that begins a word (start-of-string or preceded by whitespace) and the query contains no
 * whitespace. Returns null otherwise. Pure + side-effect-free so it is unit-testable and the composer
 * can call it on every keystroke.
 */
fun detectMentionQuery(text: String): String? {
    val at = text.lastIndexOf('@')
    if (at < 0) return null
    // `@` must start a word: at index 0 or preceded by whitespace.
    if (at > 0 && !text[at - 1].isWhitespace()) return null
    val query = text.substring(at + 1)
    // Active token must not contain whitespace (a space closes the mention edit).
    if (query.any { it.isWhitespace() }) return null
    return query
}

/**
 * Replace the trailing `@<query>` token in [text] with `@<displayName> ` and produce the structured
 * [MentionRef]. The mention's `start` is the `@` offset and `length` covers `@DisplayName` (excluding
 * the trailing space) so highlight offsets line up with [com.muhabbet.shared.model.Message.content].
 */
fun insertMention(text: String, member: ParticipantResponse): MentionInsertion? {
    val at = text.lastIndexOf('@')
    if (at < 0) return null
    val displayName = member.displayName?.takeIf { it.isNotBlank() }
        ?: member.phoneNumber
        ?: member.userId
    val token = "@$displayName"
    val newText = text.substring(0, at) + token + " "
    return MentionInsertion(
        text = newText,
        mention = MentionRef(userId = member.userId, start = at, length = token.length),
        cursor = newText.length
    )
}

/**
 * Filter the group roster by the active `@query` (case-insensitive substring on display name / phone),
 * excluding the current user. Empty query → whole roster (minus self).
 */
fun filterMentionCandidates(
    members: List<ParticipantResponse>,
    query: String,
    currentUserId: String
): List<ParticipantResponse> {
    val q = query.trim().lowercase()
    return members.asSequence()
        .filter { it.userId != currentUserId }
        .filter { m ->
            if (q.isEmpty()) true
            else (m.displayName?.lowercase()?.contains(q) == true) ||
                (m.phoneNumber?.lowercase()?.contains(q) == true)
        }
        .toList()
}

/**
 * Member-picker popup shown above the composer while an `@query` is active. Renders nothing when the
 * candidate list is empty so the caller can mount it unconditionally.
 */
@Composable
fun MentionAutocompletePopup(
    candidates: List<ParticipantResponse>,
    onSelect: (ParticipantResponse) -> Unit,
    modifier: Modifier = Modifier
) {
    if (candidates.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = MuhabbetElevation.Level3,
        shadowElevation = MuhabbetElevation.Level3,
        modifier = modifier.fillMaxWidth()
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
            items(candidates, key = { it.userId }) { member ->
                MentionCandidateRow(member = member, onClick = { onSelect(member) })
            }
        }
    }
}

@Composable
private fun MentionCandidateRow(
    member: ParticipantResponse,
    onClick: () -> Unit
) {
    val avatarDesc = stringResource(Res.string.mention_member_avatar)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MuhabbetSpacing.Medium, vertical = MuhabbetSpacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val avatarUrl = member.avatarUrl
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = avatarDesc,
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = avatarDesc,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = member.displayName?.takeIf { it.isNotBlank() }
                ?: member.phoneNumber
                ?: member.userId,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = MuhabbetSpacing.Medium)
        )
    }
}

/**
 * Build an [androidx.compose.ui.text.AnnotatedString] that paints the mention spans of [content] in
 * the çini cobalt accent ([LocalSemanticColors.linkColor]). Out-of-bounds offsets are skipped
 * defensively. Used by [MessageBubble] when `message.mentions` is non-empty.
 */
@Composable
fun rememberMentionAnnotatedText(
    content: String,
    mentions: List<MentionRef>,
    baseColor: androidx.compose.ui.graphics.Color
): androidx.compose.ui.text.AnnotatedString {
    val accent = LocalSemanticColors.current.linkColor
    return androidx.compose.ui.text.buildAnnotatedString {
        append(content)
        for (m in mentions) {
            val start = m.start
            val end = m.start + m.length
            if (start in 0..content.length && end in start..content.length) {
                addStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = accent,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    ),
                    start, end
                )
            }
        }
    }
}
