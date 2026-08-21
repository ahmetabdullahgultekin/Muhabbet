package com.muhabbet.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import com.muhabbet.app.BuildInfo
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.components.SettingsNavRow
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val TAG = "AboutScreen"

// The three documents already live on the marketing site (#340) — this screen is the first place
// inside the app that links to them (#614). Plain constants rather than string resources: a URL
// is not text a translator ever touches, and the pages themselves are addressed by content, not by
// the app's current locale.
private const val PRIVACY_POLICY_URL = "https://muhabbet.rollingcatsoftware.com/privacy.html"
private const val KVKK_NOTICE_URL = "https://muhabbet.rollingcatsoftware.com/aydinlatma.html"
private const val TERMS_URL = "https://muhabbet.rollingcatsoftware.com/terms.html"

/**
 * App identity, build info, and the legal documents KVKK Md. 10 and Google Play both require to be
 * reachable from inside the product, not merely hosted somewhere the user was never told about.
 *
 * Open-source licences were deliberately left off: there is no existing mechanism in this codebase
 * that generates a dependency licence list (no aboutlibraries-style Gradle plugin, no bundled
 * manifest), and hand-writing one here would drift from the real dependency set the moment it
 * changed. Left as a follow-up rather than invented.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onReleaseNotes: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val openFailedMsg = stringResource(Res.string.error_open_failed)

    // Same division of labour as ChatScreen.openExternally: opening is a platform call that can
    // genuinely fail (nothing installed to handle an https URL, the platform refusing it), and a
    // silent no-op here would be indistinguishable from a dead row.
    fun open(url: String) {
        runCatchingCancellable { uriHandler.openUri(url) }
            .onFailure { e ->
                Log.e(TAG, "Failed to open $url externally", e)
                scope.launch { snackbarHostState.showSnackbar(openFailedMsg) }
            }
    }

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.about_title),
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
                .verticalScroll(rememberScrollState())
                .padding(MuhabbetSpacing.XLarge)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                // Read from BuildInfo, never hand-typed — the single most useful line in a bug
                // report is the build it happened on, and it drifts the moment anyone hardcodes it.
                Text(
                    text = stringResource(Res.string.about_version_build, BuildInfo.VERSION, BuildInfo.VERSION_CODE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(MuhabbetSpacing.XLarge))
            HorizontalDivider()
            Spacer(Modifier.height(MuhabbetSpacing.Large))

            // Directly under the version, because it is the same fact read the other way round:
            // the number above says which build this is, and this says what that build changed
            // (#672). It is also the only route back to an update sheet somebody dismissed.
            SettingsNavRow(
                title = stringResource(Res.string.release_notes_title),
                subtitle = stringResource(Res.string.release_notes_row_subtitle),
                icon = Muhabbet.icons.Info,
                iconContentDescription = null,
                onClick = onReleaseNotes
            )

            Spacer(Modifier.height(MuhabbetSpacing.Large))

            SettingsSectionTitle(stringResource(Res.string.about_legal_section))
            Spacer(Modifier.height(MuhabbetSpacing.Medium))

            SettingsNavRow(
                title = stringResource(Res.string.about_privacy_policy),
                icon = Muhabbet.icons.Document,
                iconContentDescription = stringResource(Res.string.about_privacy_policy),
                onClick = { open(PRIVACY_POLICY_URL) }
            )
            Spacer(Modifier.height(MuhabbetSpacing.Small))
            SettingsNavRow(
                title = stringResource(Res.string.about_kvkk_notice),
                icon = Muhabbet.icons.Document,
                iconContentDescription = stringResource(Res.string.about_kvkk_notice),
                onClick = { open(KVKK_NOTICE_URL) }
            )
            Spacer(Modifier.height(MuhabbetSpacing.Small))
            SettingsNavRow(
                title = stringResource(Res.string.about_terms),
                icon = Muhabbet.icons.Document,
                iconContentDescription = stringResource(Res.string.about_terms),
                onClick = { open(TERMS_URL) }
            )
        }
    }
}
