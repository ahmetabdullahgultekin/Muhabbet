package com.muhabbet.app.ui.conversations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.action_back
import com.muhabbet.composeapp.generated.resources.broadcast_detail_no_recipients
import com.muhabbet.composeapp.generated.resources.broadcast_detail_recipients
import com.muhabbet.composeapp.generated.resources.broadcast_detail_title
import com.muhabbet.composeapp.generated.resources.error_generic
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold

@Serializable
private data class BroadcastMemberEntry(
    val broadcastListId: String,
    val userId: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastDetailScreen(
    broadcastListId: String,
    broadcastListName: String,
    onBack: () -> Unit,
    apiClient: ApiClient = koinInject()
) {
    var members by remember { mutableStateOf<List<BroadcastMemberEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(broadcastListId) {
        try {
            val response = apiClient.get<List<BroadcastMemberEntry>>(
                "/api/v1/broadcasts/$broadcastListId/members"
            )
            members = response.data ?: emptyList()
        } catch (_: Exception) {
            loadError = true
        }
        isLoading = false
    }

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = broadcastListName.ifBlank { stringResource(Res.string.broadcast_detail_title) },
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            loadError -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.error_generic),
                    color = MaterialTheme.colorScheme.error
                )
            }
            members.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Muhabbet.icons.Channel,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.padding(top = MuhabbetSpacing.Medium))
                    Text(
                        text = stringResource(Res.string.broadcast_detail_no_recipients),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                item {
                    Text(
                        text = "${stringResource(Res.string.broadcast_detail_recipients)} (${members.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            horizontal = MuhabbetSpacing.XLarge,
                            vertical = MuhabbetSpacing.Medium
                        )
                    )
                    HorizontalDivider()
                }
                itemsIndexed(members) { _, member ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = MuhabbetElevation.Level1
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = MuhabbetSpacing.Large,
                                vertical = MuhabbetSpacing.Medium
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(
                                Muhabbet.icons.Person,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(MuhabbetSpacing.Medium))
                            Text(
                                text = member.userId,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
