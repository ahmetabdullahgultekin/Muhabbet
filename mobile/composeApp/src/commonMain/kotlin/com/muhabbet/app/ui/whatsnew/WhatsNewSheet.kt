package com.muhabbet.app.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.muhabbet.app.BuildInfo
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.release_notes_version
import com.muhabbet.composeapp.generated.resources.whatsnew_dismiss
import com.muhabbet.composeapp.generated.resources.whatsnew_intro
import com.muhabbet.composeapp.generated.resources.whatsnew_title
import com.muhabbet.designsystem.components.MuhabbetBottomSheet
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Test tag on the sheet, so an instrumented test can assert it appears exactly once per update. */
const val WhatsNewSheetTag = "whats_new_sheet"

/**
 * Shown once after an update, over whatever the user landed on after login (#672).
 *
 * The app used to say nothing at all when it changed under someone's feet. Three releases went out
 * between 0.3.8 and 0.3.10 carrying dozens of fixes — including features that had never worked —
 * and the person using it had no way to learn any of that short of opening the Play Store listing.
 *
 * An overlay, never a gate, for the same reason as the test-build notice: the screen underneath is
 * already composed and drawn, so if anything here misbehaves — string resources not having resolved
 * yet, a release with no notes — the app behind it is fully usable. Every dismissal route (the
 * button, the scrim, a downward drag, the system back gesture) runs the same lambda, so the version
 * is recorded whichever way it is closed and the sheet cannot come back tomorrow merely because it
 * was dismissed the other way.
 *
 * It shows **this version's** notes only. Someone who skipped a release finds the rest under
 * Settings → About → Release notes, which is the half of #672 that is always reachable; a sheet that
 * grew a section per skipped version would stop being something anyone reads to the end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(modifier: Modifier = Modifier) {
    val tokenStorage: TokenStorage = koinInject()
    val note = remember { releaseNoteFor(BuildInfo.VERSION) }

    // Read once into state, like the test-build notice: re-reading storage on every recomposition
    // would put a synchronous preferences read on the navigation host's every frame, and would read
    // back the version the dismissal just wrote before `visible` had a chance to turn false.
    var visible by remember {
        mutableStateOf(
            shouldShowWhatsNew(
                lastSeenVersion = tokenStorage.getLastSeenVersion(),
                currentVersion = BuildInfo.VERSION,
                hasNotes = note != null
            )
        )
    }
    if (!visible || note == null) return

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // The flag is written here rather than after the animation: a user who dismisses the sheet and
    // immediately kills the app has still seen it, and re-opening it on the next launch because the
    // hide animation did not finish would be the worse failure of the two.
    val record = {
        tokenStorage.setLastSeenVersion(BuildInfo.VERSION)
        visible = false
    }

    MuhabbetBottomSheet(
        onDismiss = record,
        modifier = modifier.testTag(WhatsNewSheetTag),
        sheetState = sheetState
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Large)) {
            Column(verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall)) {
                Text(
                    text = stringResource(Res.string.whatsnew_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(Res.string.release_notes_version, note.version),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(Res.string.whatsnew_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ReleaseNoteLines(lines = note.lines)

            MuhabbetButton(
                text = stringResource(Res.string.whatsnew_dismiss),
                // Animated out rather than removed from the composition, which is the one thing a
                // ModalBottomSheet does not do for you: dropping it would make the sheet vanish on
                // the frame the button is released while the scrim fades on its own, and the two
                // halves of the same surface would come apart.
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { record() } },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
