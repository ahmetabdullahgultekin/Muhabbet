package com.muhabbet.designsystem.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContactPhone
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every icon in the app, named for what it means rather than what it looks like.
 *
 * There were 195 scattered `Icons.*` call sites across 70 distinct icons — `ArrowBack` alone
 * appeared 25 times. Changing the back arrow meant touching 25 files, and the filled/outlined mix
 * was arbitrary (`Icons.Default.Visibility` sitting next to `Icons.Outlined.ContactPhone`) because
 * nothing recorded which style was intended.
 *
 * Naming by meaning (`Back`, `Mute`, `Sent`) rather than by glyph (`ArrowBack`, `VolumeOff`,
 * `DoneAll`) is what makes the set swappable: replacing Material icons with a drawn Muhabbet set —
 * or just correcting one choice — becomes a single-file edit.
 *
 * Declared as `val`s with getters so nothing is built until it is first read.
 */
object MuhabbetIcons {

    // Navigation and chrome
    val Back: ImageVector get() = Icons.AutoMirrored.Filled.ArrowBack
    val Close: ImageVector get() = Icons.Default.Close
    val More: ImageVector get() = Icons.Default.MoreVert
    val Search: ImageVector get() = Icons.Default.Search
    val Settings: ImageVector get() = Icons.Outlined.Settings
    val ScrollDown: ImageVector get() = Icons.Default.KeyboardArrowDown
    val Refresh: ImageVector get() = Icons.Default.Refresh
    val Add: ImageVector get() = Icons.Default.Add

    // Bottom navigation
    val TabChats: ImageVector get() = Icons.Outlined.ChatBubbleOutline
    val TabCommunities: ImageVector get() = Icons.Default.Groups
    val TabUpdates: ImageVector get() = Icons.Default.CameraAlt
    val TabCalls: ImageVector get() = Icons.Default.Call

    // Composing and sending
    val Send: ImageVector get() = Icons.AutoMirrored.Filled.Send
    val Attach: ImageVector get() = Icons.Default.AttachFile
    val Emoji: ImageVector get() = Icons.Default.EmojiEmotions
    val Gif: ImageVector get() = Icons.Default.Gif
    val Camera: ImageVector get() = Icons.Default.CameraAlt
    val Mic: ImageVector get() = Icons.Default.Mic
    val MicOff: ImageVector get() = Icons.Default.MicOff
    val Schedule: ImageVector get() = Icons.Default.Schedule
    val Poll: ImageVector get() = Icons.Default.Poll
    val Location: ImageVector get() = Icons.Default.LocationOn
    val Contact: ImageVector get() = Icons.Outlined.ContactPhone

    /** Typing a phone number, as opposed to picking someone the address book already knows. */
    val DialPad: ImageVector get() = Icons.Default.Dialpad

    // Message actions
    val Reply: ImageVector get() = Icons.AutoMirrored.Filled.Reply
    val Forward: ImageVector get() = Icons.AutoMirrored.Filled.Forward
    val Copy: ImageVector get() = Icons.Default.ContentCopy
    val Edit: ImageVector get() = Icons.Default.Edit
    val Delete: ImageVector get() = Icons.Default.Delete
    val Star: ImageVector get() = Icons.Default.Star
    val StarOutline: ImageVector get() = Icons.Default.StarBorder
    val Pin: ImageVector get() = Icons.Default.PushPin
    val Info: ImageVector get() = Icons.Default.Info
    val Share: ImageVector get() = Icons.Default.Share
    val Download: ImageVector get() = Icons.Default.Download

    // Delivery state
    val Sent: ImageVector get() = Icons.Default.Check
    val Delivered: ImageVector get() = Icons.Default.DoneAll
    val Pending: ImageVector get() = Icons.Default.AccessTime

    // Media
    val Image: ImageVector get() = Icons.Default.Image
    val Video: ImageVector get() = Icons.Default.Videocam
    val Document: ImageVector get() = Icons.Default.Description
    val Play: ImageVector get() = Icons.Default.PlayArrow
    val Pause: ImageVector get() = Icons.Default.Pause
    val Stop: ImageVector get() = Icons.Default.Stop
    val Link: ImageVector get() = Icons.Default.Link
    val Wallpaper: ImageVector get() = Icons.Default.Wallpaper
    val MediaQuality: ImageVector get() = Icons.Default.HighQuality

    // Calls
    val CallStart: ImageVector get() = Icons.Default.Call
    val CallEnd: ImageVector get() = Icons.Default.CallEnd
    val CallIncoming: ImageVector get() = Icons.Default.CallReceived
    val CallOutgoing: ImageVector get() = Icons.Default.CallMade
    val CallMissed: ImageVector get() = Icons.Default.CallMissed
    val VideoCall: ImageVector get() = Icons.Default.Videocam
    val Speaker: ImageVector get() = Icons.Default.VolumeUp

    // People and groups
    val Person: ImageVector get() = Icons.Default.Person
    val Group: ImageVector get() = Icons.Default.Group
    val GroupOutlined: ImageVector get() = Icons.Outlined.Group
    val People: ImageVector get() = Icons.Default.People
    val Channel: ImageVector get() = Icons.Default.Campaign
    val NewMessage: ImageVector get() = Icons.AutoMirrored.Filled.Message

    // Privacy, moderation and security
    val Block: ImageVector get() = Icons.Default.Block
    val Report: ImageVector get() = Icons.Default.Report
    val Lock: ImageVector get() = Icons.Default.Lock
    val Privacy: ImageVector get() = Icons.Default.PrivacyTip
    val TwoStep: ImageVector get() = Icons.Default.VerifiedUser
    val Moderation: ImageVector get() = Icons.Default.Gavel
    val Visible: ImageVector get() = Icons.Default.Visibility
    val Hidden: ImageVector get() = Icons.Default.VisibilityOff
    val MuteOff: ImageVector get() = Icons.Outlined.NotificationsOff

    // Disappearing messages
    val Timer: ImageVector get() = Icons.Default.Timer
    val TimerOff: ImageVector get() = Icons.Default.TimerOff
    val Calendar: ImageVector get() = Icons.Default.CalendarToday

    // Account
    val Logout: ImageVector get() = Icons.AutoMirrored.Filled.Logout
    val LeaveGroup: ImageVector get() = Icons.AutoMirrored.Filled.ExitToApp
}
