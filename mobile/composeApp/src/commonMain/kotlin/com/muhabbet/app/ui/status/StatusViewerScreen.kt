package com.muhabbet.app.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetAlphas
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetDurations
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.StatusRepository
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.shared.dto.StatusResponse
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import kotlinx.coroutines.delay
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetDialog
import com.muhabbet.designsystem.components.MuhabbetIconButton
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun StatusViewerScreen(
    userId: String,
    displayName: String,
    onBack: () -> Unit,
    statusRepository: StatusRepository = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var statuses by remember { mutableStateOf<List<StatusResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentIndex by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val noStatusesMsg = stringResource(Res.string.status_no_statuses)
    val loadFailedMsg = stringResource(Res.string.status_load_failed)
    val deleteLabel = stringResource(Res.string.status_delete)
    val deleteConfirmMsg = stringResource(Res.string.status_delete_confirm)
    val deleteFailedMsg = stringResource(Res.string.status_delete_failed)
    val statusMediaDescription = stringResource(Res.string.status_image_description)
    val cancelLabel = stringResource(Res.string.cancel)
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    /**
     * The screen is immersive and theme-independent by design: a photograph is judged against
     * black, not against whatever the app's surface happens to be. That intent was already
     * expressed as the `scrim` semantic pair — a black container with the one foreground measured
     * on it — and this screen was reaching past it for `colorScheme.scrim` plus
     * `colorScheme.inverseOnSurface` instead (#586).
     *
     * Those two are not a pair. `inverseOnSurface` is the content colour for `inverseSurface`, and
     * in a dark scheme `inverseSurface` is *light*, so its foreground is a near-black ink: I10 in
     * the dark scheme, I00 on OLED. Drawn on `scrim` (pure black) that is **1.20:1** and **1.06:1**
     * respectively — the owner's report was not "low contrast", it was invisible text. Only the
     * light scheme happened to work, which is why it survived review.
     */
    val scrim = LocalSemanticColors.current.scrim
    val bgColor = scrim.container

    // One opaque foreground for every piece of text on this screen. Not a dimmed variant for the
    // secondary line: a story's text is read over user media, and alpha against a ground the
    // palette did not choose is exactly the mistake #678 cost us on the deleted bubble. Hierarchy
    // here is carried by the type scale.
    val onBgColor = scrim.content
    val barFg = scrim.content
    val barBg = scrim.content.copy(alpha = MuhabbetAlphas.ProgressTrack)

    // The plate that gives text its own ground when a photograph is behind it. See
    // MuhabbetAlphas.MediaScrim for why this opacity and not a smaller one.
    val mediaScrim = scrim.container.copy(alpha = MuhabbetAlphas.MediaScrim)

    // Your own status is not in your contacts' statuses, and cannot be: `findAllContactUserIds`
    // filters `m.userId != :userId`, so the poster is structurally excluded from the audience that
    // query describes. That is correct for a "contacts" query and wrong for this screen, which is
    // also how you confirm a post went out and how you delete it. The reader for the other half
    // already existed — `GET /api/v1/statuses/me` — and had no caller at all (#588).
    val isOwnStatus = remember(userId) { tokenStorage.getUserId() == userId }

    suspend fun load() {
        runCatchingCancellable {
            statuses = if (isOwnStatus) {
                statusRepository.getMyStatuses()
            } else {
                statusRepository.getContactStatuses()
                    .firstOrNull { it.userId == userId }
                    ?.statuses
                    .orEmpty()
            }
        }.onFailure { e ->
            // Already localized and already visible; the log was the missing half.
            Log.e(TAG, "Failed to load statuses for $userId", e)
            errorMsg = loadFailedMsg
        }
        isLoading = false
    }

    LaunchedEffect(userId) {
        load()
    }

    if (confirmDelete) {
        MuhabbetDialog(
            title = deleteLabel,
            onDismiss = { confirmDelete = false },
            dismissLabel = cancelLabel,
            confirmLabel = deleteLabel,
            confirmEnabled = !isDeleting,
            dismissible = !isDeleting,
            destructive = true,
            onConfirm = {
                val target = statuses.getOrNull(currentIndex)?.id
                if (target != null) {
                    isDeleting = true
                    scope.launch {
                        runCatchingCancellable { statusRepository.deleteStatus(target) }
                            .onFailure { e ->
                                Log.e(TAG, "Failed to delete status $target", e)
                                errorMsg = deleteFailedMsg
                            }
                            .onSuccess {
                                // Re-read rather than removing locally: the server decides what is
                                // still unexpired, and a viewer that trusted its own list would go
                                // on playing a status the backend has dropped.
                                currentIndex = 0
                                load()
                            }
                        isDeleting = false
                        confirmDelete = false
                    }
                }
            },
            content = { Text(deleteConfirmMsg) }
        )
    }

    // Auto-advance timer with pause support.
    //
    // `confirmDelete` is a pause reason in its own right rather than something that writes to
    // `isPaused`: a state write from the composition body would run on every recomposition and
    // fight the gesture handler that owns that flag. Keying the effect says the same thing without
    // a second writer.
    LaunchedEffect(currentIndex, statuses.size, isPaused, confirmDelete) {
        if (statuses.isEmpty() || isPaused || confirmDelete) return@LaunchedEffect
        progress = 0f
        val totalMs = MuhabbetDurations.StatusDisplayMs
        val stepMs = MuhabbetDurations.StatusProgressTickMs
        val steps = totalMs / stepMs
        for (i in 0..steps) {
            if (isPaused || confirmDelete) return@LaunchedEffect
            progress = i.toFloat() / steps
            delay(stepMs)
        }
        if (currentIndex < statuses.lastIndex) {
            currentIndex++
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = onBgColor
            )
        } else if (errorMsg != null || statuses.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = errorMsg ?: noStatusesMsg,
                    color = onBgColor,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(MuhabbetSpacing.Large))
                Text(
                    text = cancelLabel,
                    color = onBgColor,
                    modifier = Modifier.clickable { onBack() }
                )
            }
        } else {
            // Coerced rather than indexed blindly. Deleting your own status shrinks this list
            // underneath the index the viewer is sitting on, and a stale index here is a crash
            // rather than a wrong frame. The empty case is already handled above.
            val currentStatus = statuses[currentIndex.coerceIn(statuses.indices)]

            // Tap left/right + long-press to pause
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPaused = true
                                tryAwaitRelease()
                                isPaused = false
                            },
                            onTap = { offset ->
                                val halfWidth = size.width / 2
                                if (offset.x < halfWidth) {
                                    if (currentIndex > 0) currentIndex-- else onBack()
                                } else {
                                    if (currentIndex < statuses.lastIndex) currentIndex++ else onBack()
                                }
                            }
                        )
                    }
            ) {}

            // Center: status media.
            //
            // Drawn BEFORE the chrome, which is not a cosmetic ordering choice. A Box stacks its
            // children in declaration order, so while this sat last the photograph was painted
            // *over* the progress bars, the name and the timestamp — every one of which is drawn in
            // a single flat colour with nothing behind it (#586). On a portrait photo, which fits
            // to full height, the chrome was simply not on the screen.
            if (currentStatus.mediaUrl != null) {
                coil3.compose.AsyncImage(
                    model = currentStatus.mediaUrl,
                    contentDescription = statusMediaDescription,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }

            StatusViewerChrome(
                statuses = statuses,
                currentIndex = currentIndex,
                progress = progress,
                displayName = displayName,
                createdAt = currentStatus.createdAt,
                showDelete = isOwnStatus,
                deleteLabel = deleteLabel,
                onDelete = { confirmDelete = true },
                onBack = onBack,
                onBgColor = onBgColor,
                barFg = barFg,
                barBg = barBg,
                mediaScrim = mediaScrim
            )

            StatusCaption(
                content = currentStatus.content,
                hasMedia = currentStatus.mediaUrl != null,
                onBgColor = onBgColor,
                mediaScrim = mediaScrim,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * The story viewer's chrome: segmented progress, who posted it, when, and — when it is yours — the
 * way to take it down.
 *
 * Extracted from [StatusViewerScreen] to keep that composable under the 300-line rule, and because
 * everything here shares one property worth stating once: it is drawn over media the palette did
 * not choose, so it carries its own ground rather than trusting the backdrop.
 *
 * That ground is **solid across the whole of the chrome** and fades out only below it. A gradient
 * behind the text itself would be unmeasurable — a fade has no single contrast ratio, and the end
 * where it has faded to nothing is exactly where the last line of text would sit.
 */
@Composable
private fun StatusViewerChrome(
    statuses: List<StatusResponse>,
    currentIndex: Int,
    progress: Float,
    displayName: String,
    createdAt: Long,
    showDelete: Boolean,
    deleteLabel: String,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onBgColor: Color,
    barFg: Color,
    barBg: Color,
    mediaScrim: Color
) {
    // Top: progress bars + user info, on a plate of their own.
    //
    // The plate is what makes this text legible over media the palette never chose. It is
    // solid across the whole of the chrome and fades out only *below* it, so every glyph
    // here sits on a known ground rather than somewhere along a gradient — a gradient
    // cannot promise a contrast ratio at the end where it has faded to nothing.
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(mediaScrim)
                // Only the chrome is inset — the story image behind it stays full-bleed, which
                // is the whole point of the viewer. Without this the progress ticks sit under
                // the status bar.
                .safeDrawingPadding()
                .padding(top = MuhabbetSpacing.Large, start = MuhabbetSpacing.Small, end = MuhabbetSpacing.Small, bottom = MuhabbetSpacing.Small)
        ) {
            // Segmented progress bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                statuses.forEachIndexed { index, _ ->
                    val segmentProgress = when {
                        index < currentIndex -> 1f
                        index == currentIndex -> progress
                        else -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(MuhabbetCorners.Hairline))
                            .background(barBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(segmentProgress)
                                .height(3.dp)
                                .clip(RoundedCornerShape(MuhabbetCorners.Hairline))
                                .background(barFg)
                        )
                    }
                }
            }

            Spacer(Modifier.height(MuhabbetSpacing.Small))

            // User info row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                MuhabbetIconButton(
                    icon = Muhabbet.icons.Back,
                    contentDescription = stringResource(Res.string.action_back),
                    onClick = onBack,
                    tint = onBgColor
                )
                UserAvatar(
                    avatarUrl = null,
                    displayName = displayName,
                    size = MuhabbetSizes.AvatarXSmall
                )
                Spacer(Modifier.width(MuhabbetSpacing.Medium))
                Column {
                    Text(
                        text = displayName,
                        color = onBgColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = formatStatusTime(createdAt),
                        color = onBgColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (showDelete) {
                    Spacer(Modifier.weight(1f))
                    MuhabbetIconButton(
                        icon = Muhabbet.icons.Delete,
                        contentDescription = deleteLabel,
                        onClick = onDelete,
                        tint = onBgColor
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MuhabbetSpacing.XLarge)
                .background(Brush.verticalGradient(listOf(mediaScrim, Color.Transparent)))
        )
    }
}

/**
 * The words under a status, on the same plate as the chrome and mirrored: the fade sits above the
 * caption and the ground under the text is solid.
 *
 * On a text-only status the plate composites black on black and costs nothing, so there is no
 * branch here for "has media" beyond the type scale the caption is set in.
 */
@Composable
private fun StatusCaption(
    content: String?,
    hasMedia: Boolean,
    onBgColor: Color,
    mediaScrim: Color,
    modifier: Modifier = Modifier
) {
    if (content == null) return
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MuhabbetSpacing.XLarge)
                .background(Brush.verticalGradient(listOf(Color.Transparent, mediaScrim)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(mediaScrim)
                .padding(horizontal = MuhabbetSpacing.XXLarge, vertical = MuhabbetSpacing.XXLarge)
        ) {
            Text(
                text = content,
                color = onBgColor,
                // No explicit fontSize: the style below already carries one, and setting both meant
                // the type scale was being overridden by a hardcoded number that happened to agree
                // with it. Now a scale change reaches this screen too.
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                style = if (hasMedia) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatStatusTime(epochMillis: Long): String =
    DateTimeFormatter.formatTime(epochMillis)

private const val TAG = "StatusViewerScreen"
