package com.muhabbet.app.ui.communities

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.CommunityRepository
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.CreateCommunityRequest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCommunityScreen(
    onBack: () -> Unit,
    onCommunityCreated: (String) -> Unit,
    communityRepository: CommunityRepository = koinInject()
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val genericErrorMsg = stringResource(Res.string.error_generic)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.community_create)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(MuhabbetSpacing.XLarge)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 64) name = it },
                label = { Text(stringResource(Res.string.community_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(MuhabbetSpacing.Medium))

            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 256) description = it },
                label = { Text(stringResource(Res.string.community_description_hint)) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(MuhabbetSpacing.XLarge))

            Button(
                onClick = {
                    scope.launch {
                        isCreating = true
                        try {
                            val created = communityRepository.createCommunity(
                                CreateCommunityRequest(
                                    name = name,
                                    description = description.ifBlank { null }
                                )
                            )
                            onCommunityCreated(created.id)
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar(genericErrorMsg)
                        }
                        isCreating = false
                    }
                },
                enabled = !isCreating && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(Res.string.community_create))
                }
            }
        }
    }
}
