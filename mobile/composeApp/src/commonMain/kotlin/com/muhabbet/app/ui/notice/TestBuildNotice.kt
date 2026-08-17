package com.muhabbet.app.ui.notice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.muhabbet.app.BuildInfo
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.settings_test_build_body
import com.muhabbet.composeapp.generated.resources.settings_test_build_title
import com.muhabbet.composeapp.generated.resources.test_build_notice_body_expectations
import com.muhabbet.composeapp.generated.resources.test_build_notice_body_reliance
import com.muhabbet.composeapp.generated.resources.test_build_notice_body_report
import com.muhabbet.composeapp.generated.resources.test_build_notice_dismiss
import com.muhabbet.composeapp.generated.resources.test_build_notice_title
import com.muhabbet.composeapp.generated.resources.test_build_notice_version
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetDialog
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The app's one statement about what it currently is: a build under test, being used by real people
 * as their messenger.
 *
 * It says the same thing twice on purpose, in the two places a statement like this is actually read.
 * [TestBuildNoticeDialog] catches someone once per version, when the expectation is being set;
 * [TestBuildNoticeCard] sits in Settings permanently, for the moment someone suspects the app is
 * broken and goes looking. Both draw from the same strings so the two can never drift apart.
 *
 * What this deliberately is NOT is a banner over the chat. A warning that is on screen all day stops
 * being read within a day, and it would compete with the connection-state indicator (#511) — which
 * carries information that changes, and therefore has the better claim on that space. See #519.
 */

/** Test tag on the first-run dialog, so an instrumented test can assert it appears exactly once. */
const val TestBuildNoticeDialogTag = "test_build_notice_dialog"

/**
 * True when the user has not yet acknowledged the notice for the version they are now running.
 *
 * Keyed on the exact version string rather than a boolean: a boolean would be set once, forever, and
 * the notice would never be seen again by anyone who installed an early build — which is the wrong
 * way round, since later builds are the ones whose new breakage nobody has been warned about yet.
 *
 * Pure and public so the rule is testable without a device; the round trip through storage is
 * covered in `TestBuildNoticeTest`.
 */
fun shouldShowTestBuildNotice(acknowledgedVersion: String?, currentVersion: String): Boolean =
    acknowledgedVersion != currentVersion

/**
 * Shown once per app version, over whatever the user landed on after login.
 *
 * An overlay, never a gate: the conversation list is already composed and drawn underneath, so if
 * anything about this dialog misbehaves — including string resources not having resolved yet, which
 * in Compose Multiplatform can leave a label momentarily empty — the app behind it is fully usable
 * and every dismissal route still works. The scrim tap and the system back gesture dismiss it as
 * surely as the button does, and all three write the flag, so it cannot come back tomorrow merely
 * because it was dismissed the other way.
 */
@Composable
fun TestBuildNoticeDialog(modifier: Modifier = Modifier) {
    val tokenStorage: TokenStorage = koinInject()

    // Read once into state. Re-reading storage on every recomposition would mean the frame between
    // "flag written" and "visible = false" reads back the new value and the dialog flickers; more
    // importantly it would put a synchronous preferences read on every recomposition of the
    // navigation host.
    var visible by remember {
        mutableStateOf(
            shouldShowTestBuildNotice(
                acknowledgedVersion = tokenStorage.getTestBuildNoticeAckVersion(),
                currentVersion = BuildInfo.VERSION
            )
        )
    }
    if (!visible) return

    MuhabbetDialog(
        title = stringResource(Res.string.test_build_notice_title),
        onDismiss = {
            tokenStorage.setTestBuildNoticeAckVersion(BuildInfo.VERSION)
            visible = false
        },
        modifier = modifier.testTag(TestBuildNoticeDialogTag),
        dismissLabel = stringResource(Res.string.test_build_notice_dismiss)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)) {
            Text(
                text = stringResource(Res.string.test_build_notice_body_expectations),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(Res.string.test_build_notice_body_reliance),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(Res.string.test_build_notice_body_report),
                style = MaterialTheme.typography.bodyMedium
            )
            // The version, so that a person reporting a problem can read back which build they are
            // on without hunting through Settings for it.
            Text(
                text = stringResource(Res.string.test_build_notice_version, BuildInfo.VERSION),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The permanent half, in Settings → App. Replaces the bare version line rather than sitting under
 * it: the version and the caveat are one fact, and printing the number twice on the same screen
 * reads as an oversight.
 *
 * `surfaceVariant` / `onSurfaceVariant` on purpose — it is the one container pair in the palette
 * whose contrast is documented as measured in the dark scheme (see MuhabbetPalette, where dark
 * `surfaceVariant` was moved from I20 to I15 for exactly that reason). An error or warning colour
 * would also have been legible, and would have made a calm, standing statement of fact look like
 * something had just gone wrong.
 */
@Composable
fun TestBuildNoticeCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(MuhabbetSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)
        ) {
            Icon(
                imageVector = Muhabbet.icons.Info,
                // Decorative. The two lines beside it say the whole thing; announcing "info" first
                // would only put a noise word in front of the sentence a screen reader is about to
                // read out anyway.
                contentDescription = null,
                modifier = Modifier.size(MuhabbetSizes.IconMedium)
            )
            Column(verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall)) {
                Text(
                    text = stringResource(Res.string.settings_test_build_title, BuildInfo.VERSION),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringResource(Res.string.settings_test_build_body),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
