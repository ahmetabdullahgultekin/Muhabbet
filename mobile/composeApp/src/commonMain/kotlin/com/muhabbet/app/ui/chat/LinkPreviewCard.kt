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
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.LinkPreviewResponse
import org.koin.compose.koinInject

private val URL_REGEX = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")

fun extractFirstUrl(text: String): String? = URL_REGEX.find(text)?.value

@Composable
fun LinkPreviewCard(
    url: String,
    isOwn: Boolean,
    apiClient: ApiClient = koinInject(),
    onOpenUrl: (String) -> Unit = {}
) {
    var preview by remember(url) { mutableStateOf<LinkPreviewResponse?>(null) }

    LaunchedEffect(url) {
        try {
            val response = apiClient.get<LinkPreviewResponse>("/api/v1/link-preview?url=${java.net.URLEncoder.encode(url, "UTF-8")}")
            preview = response.data
        } catch (_: Exception) { }
    }

    val p = preview ?: return
    if (p.title == null && p.description == null && p.imageUrl == null) return

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isOwn) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.height(6.dp))
            }
            if (p.siteName != null) {
                Text(
                    text = p.siteName!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
            if (p.title != null) {
                Text(
                    text = p.title!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOwn) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (p.description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = p.description!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
