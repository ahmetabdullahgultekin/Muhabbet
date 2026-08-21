package com.muhabbet.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muhabbet.shared.model.Message
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import com.muhabbet.designsystem.modifier.longPressable
import com.muhabbet.designsystem.modifier.pressable
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.Muhabbet
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import com.muhabbet.designsystem.components.MuhabbetBottomSheet
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.components.keyboardActionsFor
import com.muhabbet.designsystem.theme.containerColor
import com.muhabbet.designsystem.theme.depth
import com.muhabbet.designsystem.theme.MuhabbetDepth
import androidx.compose.ui.graphics.RectangleShape

@Composable
fun ReplyPreviewBar(
    replyingTo: Message,
    onCancel: () -> Unit
) {
    // Raised, not Floating: the input bar is attached to the page, not hovering over it.
    Surface(
        color = MuhabbetDepth.Raised.containerColor(),
        modifier = Modifier.depth(MuhabbetDepth.Raised, RectangleShape)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Medium, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Muhabbet.icons.Reply,
                contentDescription = stringResource(Res.string.chat_context_reply),
                modifier = Modifier.size(MuhabbetSizes.IconSmall),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(MuhabbetSpacing.Small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.chat_replying_to),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = replyingTo.content.take(50),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(com.muhabbet.designsystem.theme.MuhabbetSizes.MinTouchTarget)) {
                Icon(Muhabbet.icons.Close, contentDescription = stringResource(Res.string.action_close), modifier = Modifier.size(MuhabbetSizes.IconSmall))
            }
        }
    }
}

@Composable
fun EditModeBar(
    editModeText: String,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = MuhabbetElevation.Level4
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Medium, vertical = MuhabbetSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Muhabbet.icons.Edit,
                contentDescription = stringResource(Res.string.chat_context_edit),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(MuhabbetSpacing.Small))
            Text(
                text = editModeText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCancel, modifier = Modifier.size(com.muhabbet.designsystem.theme.MuhabbetSizes.MinTouchTarget)) {
                Icon(Muhabbet.icons.Close, contentDescription = stringResource(Res.string.action_close), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageInputBar(
    messageText: String,
    onTextChange: (String) -> Unit,
    isEditing: Boolean,
    isUploading: Boolean,
    onSend: () -> Unit,
    recordingPhase: VoiceRecordingPhase,
    recordingSeconds: Int,
    onRecordPressStart: () -> Boolean,
    onRecordDragUpdate: (dragX: Float, dragY: Float) -> Unit,
    onRecordLocked: () -> Unit,
    onRecordReleased: (dragX: Float, dragY: Float) -> Unit,
    onImagePick: () -> Unit,
    onFilePick: () -> Unit,
    onPollCreate: () -> Unit,
    onLocationShare: () -> Unit,
    onGifPick: () -> Unit = {},
    onStickerPick: () -> Unit = {},
    onCameraPick: () -> Unit = {},
    viewOnceEnabled: Boolean = false,
    onViewOnceToggle: () -> Unit = {},
    onScheduleSend: () -> Unit = {}
) {
    var showAttachMenu by remember { mutableStateOf(false) }
    val fieldBackground = LocalSemanticColors.current.inputField.container
    // Only Idle and Held ever reach this composable — see the doc on VoiceRecordGestureButton for
    // why Locked/Preview are rendered by an entirely different composable one level up instead.
    val isHeldForRecording = recordingPhase is VoiceRecordingPhase.Held

    Surface(
        color = LocalSemanticColors.current.inputBar.container,
        tonalElevation = MuhabbetElevation.None
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Small, vertical = MuhabbetSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attach button — opens the attachment sheet below, not a menu anchored to this icon.
            //
            // The only control left standing beside the field. It used to be one of four (a smiley,
            // this, a view-once eye, and the mic), which left the single most-used input in the app
            // with about half the row on a 1080px screen (#479). The smiley moved inside the field
            // as a leading affordance, and view-once moved into the attachment sheet, where the
            // media it applies to is actually chosen.
            //
            // It also carries whether view-once is armed, in its tint and in its label. The eye was
            // a bad control but it was an always-visible one, and view-once is the one composer
            // setting with a privacy consequence — a state that can only be seen by reopening the
            // sheet that sets it is not a state the user can be held to. This costs no width: the
            // entrance to the setting is the natural place to show it is on.
            //
            // Hidden rather than merely disabled while a recording is held: WhatsApp/Telegram both
            // give the "slide to cancel" hint the full width the field just gave up, and there is
            // nothing this button could usefully do mid-recording anyway.
            if (!isHeldForRecording) {
                IconButton(
                    onClick = { showAttachMenu = true },
                    enabled = !isUploading && !isEditing
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(MuhabbetSizes.IconLarge),
                            strokeWidth = MuhabbetSizes.ProgressStrokeThin
                        )
                    } else {
                        Icon(
                            imageVector = Muhabbet.icons.Attach,
                            contentDescription = stringResource(
                                if (viewOnceEnabled) Res.string.attach_file_view_once_on
                                else Res.string.attach_file
                            ),
                            tint = if (viewOnceEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isHeldForRecording) {
                RecordingHintRow(
                    recordingSeconds = recordingSeconds,
                    dragX = recordingPhase.dragX,
                    modifier = Modifier.weight(1f)
                )
            } else {
            OutlinedTextField(
                value = messageText,
                onValueChange = onTextChange,
                placeholder = { Text(stringResource(Res.string.chat_message_placeholder)) },
                modifier = Modifier.weight(1f).testTag("message_input"),
                // Grows line by line as it fills and scrolls inside itself past the cap, which is
                // what a composer has to do — a message is not a form field. Named rather than a
                // bare 4 so the cap is a decision and not a leftover.
                maxLines = ComposerMaxLines,
                shape = RoundedCornerShape(MuhabbetCorners.Pill),
                // Sticker shortcut — opens the shared GIF/sticker sheet on its Stickers tab.
                // A leading affordance inside the field rather than a sibling of it: it belongs to
                // the input, and as a sibling it was spending a whole 48dp slot of the row saying
                // so. (Emoji themselves need no button: the system keyboard supplies them.)
                leadingIcon = {
                    MuhabbetIconButton(
                        icon = Muhabbet.icons.Emoji,
                        contentDescription = stringResource(Res.string.attach_sticker),
                        onClick = onStickerPick,
                        enabled = !isUploading && !isEditing,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                // The bug this pane was reported for: `ImeAction.Send` was declared and no handler
                // was supplied, so the keyboard drew a send key that did nothing at all. Guarded on
                // blank for the same reason the send button is: an empty message is not a message.
                keyboardActions = keyboardActionsFor(ImeAction.Send) {
                    if (messageText.isNotBlank()) onSend()
                },
                // Filled and borderless rather than outlined. `inputFieldBackground` has been in the
                // semantic palette since it was written and had no reader until now; a composer that
                // reads as one continuous pill is also what lets the leading icon sit inside it
                // without looking bolted on.
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = fieldBackground,
                    unfocusedContainerColor = fieldBackground,
                    disabledContainerColor = fieldBackground,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
            }

            Spacer(Modifier.width(MuhabbetSpacing.XSmall))

            // The mic and the send button occupy the same spot and swap as soon as the field has
            // text, which was an instant cut. A scale crossfade makes it read as one control
            // changing what it does, which is what it is. Spatial: it is a size change.
            //
            // This target never actually changes value across Idle -> Held (messageText is
            // untouched while the field is hidden for recording), so AnimatedContent never runs a
            // transition for that step and keeps rendering the same VoiceRecordGestureButton
            // instance rather than creating a new one — see that composable's doc for why that
            // matters more than it looks.
            AnimatedContent(
                targetState = messageText.isBlank() && !isEditing,
                transitionSpec = {
                    (scaleIn(Muhabbet.motion.spatialFast()) + fadeIn(Muhabbet.motion.effectsFast()))
                        .togetherWith(
                            scaleOut(Muhabbet.motion.effectsFast()) + fadeOut(Muhabbet.motion.effectsFast())
                        )
                },
                label = "micSendMorph"
            ) { showMic ->
            if (showMic) {
                VoiceRecordGestureButton(
                    phase = recordingPhase,
                    onPressStart = onRecordPressStart,
                    onDragUpdate = onRecordDragUpdate,
                    onLocked = onRecordLocked,
                    onReleased = onRecordReleased
                )
            } else {
                // Tap = send now; long-press (text messages only) = schedule for later.
                val sendDescription = stringResource(if (isEditing) Res.string.action_save else Res.string.action_send)
                val scheduleDescription = stringResource(Res.string.schedule_send_action)
                Surface(
                    shape = CircleShape,
                    color = if (isEditing) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(MuhabbetSizes.MinTouchTarget)
                        .testTag("send_button")
                        .longPressable(
                            shape = CircleShape,
                            enabled = messageText.isNotBlank(),
                            onClickLabel = sendDescription,
                            onLongClickLabel = scheduleDescription,
                            onLongClick = if (!isEditing) onScheduleSend else null,
                            onClick = onSend
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isEditing) Muhabbet.icons.Sent else Muhabbet.icons.Send,
                            contentDescription = sendDescription,
                            tint = if (isEditing) MaterialTheme.colorScheme.onTertiary
                            else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(MuhabbetSizes.IconMedium)
                        )
                    }
                }
            }
            }
        }
    }

    if (showAttachMenu) {
        AttachmentSheet(
            onDismiss = { showAttachMenu = false },
            onImagePick = onImagePick,
            onFilePick = onFilePick,
            onPollCreate = onPollCreate,
            onLocationShare = onLocationShare,
            onGifPick = onGifPick,
            onCameraPick = onCameraPick,
            viewOnceEnabled = viewOnceEnabled,
            onViewOnceToggle = onViewOnceToggle
        )
    }
}

/**
 * How far the composer grows before it starts scrolling inside itself.
 *
 * Four lines is roughly a paragraph — enough to see what you wrote before sending it, and short
 * enough that the message list is still the larger half of the screen with the keyboard up.
 */
private const val ComposerMaxLines = 4

/**
 * The attachment picker, opened from [MessageInputBar]'s attach button.
 *
 * Was a bare `DropdownMenu` floating over the chat with six left-aligned text rows (#433) — the
 * single most-used surface in the app after the composer itself, rendered as a generic Android
 * context menu with no relationship to the palette around it. WhatsApp, Telegram and Signal each
 * treat this as a signature surface: a labelled grid of actions in a sheet, not a text list.
 *
 * No "video message" entry: there is no video recorder in this app. No caller ever passed
 * `onVideoRecord`, no expect/actual capture exists on either platform, and `CameraPicker` is
 * stills-only — the old menu item opened the menu, closed it, and did nothing. Removed rather than
 * wired, because wiring it would mean building a recorder, an encoder and an upload path first.
 *
 * Since #479 it also carries the view-once switch, which used to be a permanent unlabelled eye in
 * the composer row. It only ever applied to photos, so this is where the decision is actually made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    onDismiss: () -> Unit,
    onImagePick: () -> Unit,
    onFilePick: () -> Unit,
    onPollCreate: () -> Unit,
    onLocationShare: () -> Unit,
    onGifPick: () -> Unit,
    onCameraPick: () -> Unit,
    viewOnceEnabled: Boolean,
    onViewOnceToggle: () -> Unit
) {
    val imageLabel = stringResource(Res.string.attach_image)
    val documentLabel = stringResource(Res.string.attach_document)
    val pollLabel = stringResource(Res.string.attach_poll)
    val locationLabel = stringResource(Res.string.attach_location)
    val gifLabel = stringResource(Res.string.attach_gif)
    val cameraLabel = stringResource(Res.string.attach_camera)

    MuhabbetBottomSheet(onDismiss = onDismiss) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AttachmentGridItem(Muhabbet.icons.Image, imageLabel, Modifier.weight(1f)) { onDismiss(); onImagePick() }
            AttachmentGridItem(Muhabbet.icons.Document, documentLabel, Modifier.weight(1f)) { onDismiss(); onFilePick() }
            AttachmentGridItem(Muhabbet.icons.Camera, cameraLabel, Modifier.weight(1f)) { onDismiss(); onCameraPick() }
        }
        Spacer(Modifier.height(MuhabbetSpacing.Large))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AttachmentGridItem(Muhabbet.icons.Poll, pollLabel, Modifier.weight(1f)) { onDismiss(); onPollCreate() }
            AttachmentGridItem(Muhabbet.icons.Location, locationLabel, Modifier.weight(1f)) { onDismiss(); onLocationShare() }
            AttachmentGridItem(Muhabbet.icons.Gif, gifLabel, Modifier.weight(1f)) { onDismiss(); onGifPick() }
        }
        Spacer(Modifier.height(MuhabbetSpacing.Large))
        ViewOnceRow(enabled = viewOnceEnabled, onToggle = onViewOnceToggle)
    }
}

/**
 * The view-once switch, at the bottom of the attachment sheet.
 *
 * It was an eye in the composer row with no label at all, and the owner's report of it was simply
 * "I could not understand what it does" (#479). Two things were wrong beyond the missing label:
 *
 * - **The glyph was inverted.** Off — the state every ordinary message is sent in — drew the
 *   *crossed-out* eye, so the resting composer permanently signalled "something here is hidden".
 *   It now reads the way it means: a plain eye while photos are ordinary, a struck-through eye once
 *   they will vanish after one look.
 * - **Tint was the only signal of on/off**, which a screen reader cannot see and a colour-blind
 *   user may not either. The whole row is one `Role.Switch` node now, so the state is announced.
 *
 * The description says "photo" rather than "media" because that is the truth: `viewOnce` is only
 * applied on the two image paths. There is no video recorder, and a document or a poll marked
 * view-once would be a promise the backend never made.
 */
@Composable
private fun ViewOnceRow(enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MuhabbetCorners.Medium))
            .toggleable(value = enabled, onValueChange = { onToggle() }, role = Role.Switch)
            .heightIn(min = MuhabbetSizes.MinTouchTarget)
            .padding(horizontal = MuhabbetSpacing.Small, vertical = MuhabbetSpacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (enabled) Muhabbet.icons.Hidden else Muhabbet.icons.Visible,
            // The row's own toggleable semantics already announce the label and the state; a second
            // description here would make a screen reader read the setting out twice.
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(MuhabbetSizes.IconLarge)
        )
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.view_once_label),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(Res.string.view_once_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(MuhabbetSpacing.Small))
        Switch(checked = enabled, onCheckedChange = null)
    }
}

/** One cell of the [AttachmentSheet] grid: a tinted circular swatch with its label underneath. */
@Composable
private fun AttachmentGridItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .pressable(shape = RoundedCornerShape(MuhabbetCorners.Medium), onClick = onClick)
            .padding(vertical = MuhabbetSpacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(MuhabbetSizes.AttachmentSwatch)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(MuhabbetSizes.IconAttachment)
                )
            }
        }
        Spacer(Modifier.height(MuhabbetSpacing.XSmall))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
