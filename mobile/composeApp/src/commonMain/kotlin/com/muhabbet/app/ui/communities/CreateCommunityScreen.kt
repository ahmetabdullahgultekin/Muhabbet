package com.muhabbet.app.ui.communities

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.muhabbet.app.data.remote.ApiException
import com.muhabbet.app.data.repository.CommunityRepository
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.CreateCommunityRequest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTextField

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
    // #446: the server refuses a name this account already used. That belongs under the name field,
    // not in a snackbar — a 409 reported as a generic failure reads as a broken button, and the one
    // thing the user needs to know is which field to change.
    var nameError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val genericErrorMsg = stringResource(Res.string.error_generic)
    val nameTakenMsg = stringResource(Res.string.community_name_taken)

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.community_create),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(MuhabbetSpacing.XLarge)
        ) {
            MuhabbetTextField(
                value = name,
                onValueChange = {
                    if (it.length <= 64) name = it
                    // The name the server refused is no longer the name in the field, so the
                    // complaint about it has to go with it.
                    nameError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(Res.string.community_name_hint),
                error = nameError,
                singleLine = true,
                imeAction = ImeAction.Next
            )

            Spacer(Modifier.height(MuhabbetSpacing.Medium))

            MuhabbetTextField(
                value = description,
                onValueChange = { if (it.length <= 256) description = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(Res.string.community_description_hint),
                singleLine = false,
                maxLines = 4,
                imeAction = ImeAction.Done
            )

            Spacer(Modifier.height(MuhabbetSpacing.XLarge))

            Button(
                onClick = {
                    scope.launch {
                        isCreating = true
                        nameError = null
                        var createFailed = false
                        try {
                            val created = communityRepository.createCommunity(
                                CreateCommunityRequest(
                                    name = name,
                                    description = description.ifBlank { null }
                                )
                            )
                            onCommunityCreated(created.id)
                        } catch (e: ApiException) {
                            // The one failure the user can actually fix from this screen (#446).
                            // Everything else stays a snackbar: it says nothing about the name, so
                            // pinning it under the name field would misdirect.
                            if (e.code == CommunityNameTakenCode) {
                                nameError = nameTakenMsg
                            } else {
                                createFailed = true
                            }
                        } catch (_: Exception) {
                            createFailed = true
                        }
                        // Clear the spinner BEFORE reporting — showSnackbar suspends until
                        // dismissed (~4s).
                        isCreating = false
                        if (createFailed) snackbarHostState.showSnackbar(genericErrorMsg)
                    }
                },
                enabled = !isCreating && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(MuhabbetSizes.IconMedium),
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
