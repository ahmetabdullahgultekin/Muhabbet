package com.muhabbet.app.ui.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.muhabbet.app.data.remote.ConnectionState
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * How long the socket must stay down before the strip appears.
 *
 * Every ordinary reconnect — a tunnel, a Wi-Fi handover, the phone waking up — passes through a
 * non-CONNECTED state for a moment. A strip that appeared on each of them would flicker constantly
 * and teach the user to ignore it, which is the one outcome worse than showing nothing.
 */
private const val REVEAL_AFTER_MS = 4_000L

/**
 * The slim "you are not connected" strip.
 *
 * `WsClient` has published [ConnectionState] since it was written and **nothing has ever rendered
 * it** (#511). That is why a socket that went down and stayed down looked exactly like a healthy
 * one: the user typed, the bubble appeared, and there was no signal anywhere on screen that it had
 * gone no further than the offline queue. The socket bug is fixed separately; this is the half that
 * makes the next one visible instead of silent.
 *
 * Deliberately non-modal and deliberately quiet. It is a strip under the app bar, not a dialog —
 * losing signal is not an error the user caused and cannot be an action they have to dismiss, and
 * the chat has to stay usable while it is showing, because typing into the offline queue is a
 * perfectly reasonable thing to do once the strip has said what happens next.
 *
 * @param state the live [WsClient.connectionState]. Passed in rather than collected here so the
 *   strip stays a pure function of the connection, with no opinion about where it came from.
 */
@Composable
fun ConnectionStrip(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val connected = state == ConnectionState.CONNECTED
    var visible by remember { mutableStateOf(false) }
    // Keyed on connected-or-not, NOT on the state itself. A client that cannot reach the server
    // cycles CONNECTING -> DISCONNECTED -> RECONNECTING every couple of seconds while the backoff
    // is still short, and keying on the exact state would restart this countdown on each of those
    // — during a real outage, which is the only time the strip matters, it would never finish and
    // the strip would never appear. As a boolean it restarts only when connectivity actually
    // flips, so a blip that resolves inside the window still cancels the reveal silently.
    LaunchedEffect(connected) {
        if (connected) {
            visible = false
        } else {
            delay(REVEAL_AFTER_MS)
            visible = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = MuhabbetMotion.enterFadeUp,
        exit = MuhabbetMotion.exitFadeDown
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = MuhabbetElevation.Level4,
            modifier = modifier.fillMaxWidth().testTag("connection_strip")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MuhabbetSpacing.Medium,
                        vertical = MuhabbetSpacing.Small
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Muhabbet.icons.Refresh,
                    contentDescription = stringResource(Res.string.connection_status_label),
                    modifier = Modifier.size(MuhabbetSizes.IconSmall),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(MuhabbetSpacing.Small))
                Text(
                    text = stringResource(Res.string.connection_offline_strip),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
