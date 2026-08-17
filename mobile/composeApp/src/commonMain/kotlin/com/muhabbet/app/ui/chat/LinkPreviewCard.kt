package com.muhabbet.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.LinkPreviewResponse
import io.ktor.http.encodeURLQueryComponent
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.firstUrlOrNull
import com.muhabbet.app.util.runCatchingCancellable
import org.koin.compose.koinInject

/**
 * Delegates to `util/TextUtils.findUrlSpans`, which is now the one place that decides where a URL
 * starts and ends. This file used to carry a second, ASCII-only regex, so the preview card could
 * fetch a different string than the message displayed — and could fetch a Turkish address truncated
 * at its first non-ASCII character.
 */
fun extractFirstUrl(text: String): String? = firstUrlOrNull(text)

@Composable
fun LinkPreviewCard(
    url: String,
    isOwn: Boolean,
    apiClient: ApiClient = koinInject(),
    onOpenUrl: (String) -> Unit = {}
) {
    var preview by remember(url) { mutableStateOf<LinkPreviewResponse?>(null) }

    LaunchedEffect(url) {
        runCatchingCancellable {
            val response = apiClient.get<LinkPreviewResponse>("/api/v1/link-preview?url=${url.encodeURLQueryComponent()}")
            preview = response.data
        }.onFailure { e ->
            // Purely decorative enrichment. On failure the card renders nothing at all and the
            // message still shows its raw link, so there is nothing to tell the user about.
            Log.w("LinkPreviewCard", "Failed to fetch link preview: ${e.message}")
        }
    }

    // #517: a preview card is a panel inset into a bubble, so it takes the bubble's inset pair.
    // It used to build its own — a 20%-alpha `primary` ground with `onPrimary` text on top, neither
    // of which had any relationship to the bubble behind them.
    val semanticColors = LocalSemanticColors.current
    val card = if (isOwn) semanticColors.bubbleOwnInset else semanticColors.bubbleOtherInset

    val p = preview ?: return
    if (p.title == null && p.description == null && p.imageUrl == null) return

    Surface(
        shape = MaterialTheme.shapes.small,
        color = card.container,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MuhabbetSpacing.XSmall, vertical = 2.dp)
            .clickable { onOpenUrl(url) }
    ) {
        Column(modifier = Modifier.padding(MuhabbetSpacing.Small)) {
            if (p.imageUrl != null) {
                AsyncImage(
                    model = p.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .clip(RoundedCornerShape(MuhabbetCorners.Thumbnail))
                )
                Spacer(Modifier.height(6.dp))
            }
            p.siteName?.let { siteName ->
                Text(
                    text = siteName,
                    style = MaterialTheme.typography.labelSmall,
                    color = semanticColors.secondaryText,
                    maxLines = 1
                )
            }
            p.title?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = card.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            p.description?.let { description ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = card.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
