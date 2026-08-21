package com.muhabbet.app.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.muhabbet.app.BuildInfo
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.action_back
import com.muhabbet.composeapp.generated.resources.release_notes_current
import com.muhabbet.composeapp.generated.resources.release_notes_intro
import com.muhabbet.composeapp.generated.resources.release_notes_title
import com.muhabbet.composeapp.generated.resources.release_notes_version
import com.muhabbet.designsystem.components.MuhabbetDivider
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * The always-reachable half of #672: every release's notes, in order, for the moment someone
 * wonders whether a change they half-noticed was real.
 *
 * Reached from Settings → About rather than living in Settings directly. It is a fact about this
 * build, and About is where the other facts about this build already are — the version, the build
 * number, the documents. A second entry point in the Settings root would have made release notes
 * look like a setting.
 *
 * A `LazyColumn` and not a scrolling `Column`: this list gains an entry every release and never
 * loses one, so the version that eventually makes composing all of them wasteful is a matter of
 * time rather than of judgement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNotesScreen(onBack: () -> Unit) {
    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.release_notes_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(MuhabbetSpacing.XLarge),
            verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XLarge)
        ) {
            item {
                Text(
                    text = stringResource(Res.string.release_notes_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            itemsIndexed(items = AppReleaseNotes, key = { _, note -> note.version }) { index, note ->
                ReleaseNoteSection(
                    note = note,
                    isCurrent = note.version == BuildInfo.VERSION,
                    // Separators, not a border: a rule under the last release would draw a line
                    // under the end of the list and invite a scroll that finds nothing.
                    showDivider = index < AppReleaseNotes.lastIndex
                )
            }
        }
    }
}

@Composable
private fun ReleaseNoteSection(note: ReleaseNote, isCurrent: Boolean, showDivider: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)
    ) {
        Text(
            text = stringResource(Res.string.release_notes_version, note.version),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (isCurrent) {
            // Answers the question this screen is usually opened with — "am I on the one that fixed
            // it?" — without making the reader compare two version numbers themselves.
            Text(
                text = stringResource(Res.string.release_notes_current),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        ReleaseNoteLines(lines = note.lines)
        if (showDivider) MuhabbetDivider()
    }
}
