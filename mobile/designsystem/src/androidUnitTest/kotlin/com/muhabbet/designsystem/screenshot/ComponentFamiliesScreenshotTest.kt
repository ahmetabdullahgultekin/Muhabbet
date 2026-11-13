package com.muhabbet.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.ChatStartIllustration
import com.muhabbet.designsystem.components.ContactsRingIllustration
import com.muhabbet.designsystem.components.EditableAvatar
import com.muhabbet.designsystem.components.EmptyChatsIllustration
import com.muhabbet.designsystem.components.MuhabbetBrandMark
import com.muhabbet.designsystem.components.MuhabbetDivider
import com.muhabbet.designsystem.components.MuhabbetEmptyState
import com.muhabbet.designsystem.components.MuhabbetErrorState
import com.muhabbet.designsystem.components.MuhabbetExtendedFab
import com.muhabbet.designsystem.components.MuhabbetFab
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetNavBar
import com.muhabbet.designsystem.components.MuhabbetNavItem
import com.muhabbet.designsystem.components.MuhabbetOtpField
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetSkeletonBlock
import com.muhabbet.designsystem.components.MuhabbetSkeletonConversation
import com.muhabbet.designsystem.components.MuhabbetSkeletonGate
import com.muhabbet.designsystem.components.MuhabbetSkeletonList
import com.muhabbet.designsystem.components.MuhabbetSkeletonListRow
import com.muhabbet.designsystem.components.MuhabbetSkeletonParagraph
import com.muhabbet.designsystem.components.MuhabbetStepRail
import com.muhabbet.designsystem.components.MuhabbetTextField
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.components.SectionHeader
import com.muhabbet.designsystem.components.SettingsInfoRow
import com.muhabbet.designsystem.components.SettingsNavRow
import com.muhabbet.designsystem.components.SettingsRadioGroup
import com.muhabbet.designsystem.components.SettingsRadioRow
import com.muhabbet.designsystem.components.SettingsSwitchRow
import com.muhabbet.designsystem.components.ShimmerHost
import com.muhabbet.designsystem.components.UserAvatar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for every static design-system component family (#759). Dialogs, sheets and menus open
 * their own window and are covered separately; animated states are captured at their first frame.
 * Fixture text is Turkish on purpose — it is the default locale and carries İ ı Ğ ş.
 */
// MuhabbetTopBar still exposes M3's experimental TopAppBarScrollBehavior (#762).
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1280dp-xhdpi")
class ComponentFamiliesScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun textFields() = MuhabbetSnapshotMatrix.capture(rule, "textfield") {
        Column(verticalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Medium)) {
            MuhabbetTextField(value = "", onValueChange = {}, label = "Görünen ad", placeholder = "Adınız")
            MuhabbetTextField(value = "Işıl Güneş", onValueChange = {}, label = "Görünen ad")
            MuhabbetTextField(value = "5551234", onValueChange = {}, label = "Telefon", prefix = "+90 ", error = "Numara eksik")
            MuhabbetTextField(value = "Devre dışı", onValueChange = {}, label = "Salt okunur", enabled = false)
        }
    }

    // The second field is held to 288dp — a 320dp phone less its two screen margins — where the
    // fixed 312dp row used to run off the edge (#840).
    @Test
    fun otpField() = MuhabbetSnapshotMatrix.capture(rule, "otpfield") {
        Column(verticalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Large)) {
            MuhabbetOtpField(value = "342", onValueChange = {})
            MuhabbetOtpField(
                value = "342156",
                onValueChange = {},
                isError = true,
                modifier = Modifier.width(Muhabbet.sizes.BubbleMaxWidth - Muhabbet.spacing.XXLarge)
            )
        }
    }

    @Test
    fun settingsRows() = MuhabbetSnapshotMatrix.capture(rule, "settingsrows") {
        Column {
            SectionHeader(title = "Gizlilik", icon = Muhabbet.icons.Lock)
            SettingsNavRow(title = "Engellenen kişiler", subtitle = "3 kişi", icon = Muhabbet.icons.Person, onClick = {})
            MuhabbetDivider()
            SettingsSwitchRow(title = "Okundu bilgisi", subtitle = "Kapalıyken siz de göremezsiniz", checked = true, onCheckedChange = {})
            SettingsRadioGroup(Modifier) {
                SettingsRadioRow(title = "Herkes", selected = true, onSelect = {})
                SettingsRadioRow(title = "Kişilerim", selected = false, onSelect = {})
            }
            SettingsInfoRow(title = "Sürüm", value = "0.3.11")
            SettingsNavRow(title = "Verilerimi dışa aktar", loading = true, onClick = {})
            SettingsNavRow(title = "Hesabı sil", destructive = true, icon = Muhabbet.icons.Delete, onClick = {})
        }
    }

    /**
     * Second row (#838, #839, #840): a photo URL that cannot load must still show the initials
     * gradient, never an empty disc — this module has no network fetcher, so the URL fails exactly
     * as an expired presigned one does; a phone-number name shows the person glyph; a flag name
     * keeps the whole flag; and the initial is artwork, so it is the same size at every font scale.
     */
    @Test
    fun avatars() = MuhabbetSnapshotMatrix.capture(rule, "avatar") {
        Column(verticalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Medium)) {
            AvatarRow()
            Row(horizontalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Medium)) {
                UserAvatar(avatarUrl = "https://media.invalid/expired.jpg", displayName = "Zeynep", size = Muhabbet.sizes.AvatarSmall, seed = "user-1")
                UserAvatar(avatarUrl = null, displayName = "+905000000001", size = Muhabbet.sizes.AvatarMedium, seed = "user-2")
                UserAvatar(avatarUrl = null, displayName = "\uD83C\uDDF9\uD83C\uDDF7 Ankara", size = Muhabbet.sizes.AvatarLarge, seed = "user-3")
                UserAvatar(avatarUrl = null, displayName = "Ömer", size = Muhabbet.sizes.AvatarHero, seed = "user-4")
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun AvatarRow() {
        Row(horizontalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Medium)) {
            UserAvatar(avatarUrl = null, displayName = "İsmail Şahin", size = Muhabbet.sizes.AvatarSmall)
            UserAvatar(avatarUrl = null, displayName = "ılgaz", size = Muhabbet.sizes.AvatarMedium)
            UserAvatar(avatarUrl = null, displayName = "Çay Ocağı", size = Muhabbet.sizes.AvatarLarge, isGroup = true)
            EditableAvatar(
                avatarUrl = null,
                displayName = "Ömer",
                size = Muhabbet.sizes.AvatarXLarge,
                changePhotoContentDescription = "Fotoğrafı değiştir",
                onPickPhoto = {},
            )
        }
    }

    @Test
    fun brandAndProgress() = MuhabbetSnapshotMatrix.capture(rule, "brand") {
        Column(verticalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Large)) {
            MuhabbetBrandMark()
            MuhabbetStepRail(current = 1, total = 3)
        }
    }

    // Each state fills the space it is given (it is a whole-screen state), so each gets its own
    // capture rather than being stacked — stacked, the first pushed the others off the canvas.
    @Test
    fun emptyState() = MuhabbetSnapshotMatrix.capture(rule, "emptystate") {
        MuhabbetEmptyState(title = "Henüz sohbet yok", subtitle = "Bir kişi seçerek başlayın", icon = Muhabbet.icons.Search)
    }

    @Test
    fun errorState() = MuhabbetSnapshotMatrix.capture(rule, "errorstate") {
        MuhabbetErrorState(message = "Bağlantı kurulamadı", retryLabel = "Tekrar dene", onRetry = {})
    }

    @Test
    fun loadingState() = MuhabbetSnapshotMatrix.capture(rule, "loadingstate") {
        MuhabbetLoadingState(label = "Yükleniyor")
    }

    @Test
    fun skeletons() = MuhabbetSnapshotMatrix.capture(rule, "skeleton") {
        ShimmerHost {
            Column(Modifier.width(Muhabbet.sizes.BubbleMaxWidth), verticalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Medium)) {
                MuhabbetSkeletonBlock()
                MuhabbetSkeletonParagraph()
                MuhabbetSkeletonListRow()
                MuhabbetSkeletonGate(isLoading = true, skeleton = { MuhabbetSkeletonList(rows = 2) }) { Text("içerik") }
                Column(Modifier.height(Muhabbet.sizes.AvatarXXLarge)) { MuhabbetSkeletonConversation() }
            }
        }
    }

    @Test
    fun illustrations() = MuhabbetSnapshotMatrix.capture(rule, "illustration") {
        Column(Modifier.width(Muhabbet.sizes.BubbleMaxWidth), verticalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Large)) {
            EmptyChatsIllustration(title = "Sohbet yok", subtitle = "İlk mesajı gönderin")
            Row(horizontalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Large)) {
                ContactsRingIllustration()
                ChatStartIllustration()
            }
        }
    }

    // The bars span the whole capture (a 360dp phone less the matrix's margins) as they span the
    // screen in the app; the top bar and FABs keep the narrower column they were recorded in.
    @Test
    fun chrome() = MuhabbetSnapshotMatrix.capture(rule, "chrome") {
        Column {
            Column(Modifier.width(Muhabbet.sizes.BubbleMaxWidth)) {
                MuhabbetTopBar(
                    title = "Ayarlar",
                    onBack = {},
                    backContentDescription = "Geri",
                    actions = { MuhabbetIconButton(icon = Muhabbet.icons.Search, contentDescription = "Ara", onClick = {}) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Large)) {
                    MuhabbetFab(icon = Muhabbet.icons.Add, contentDescription = "Yeni sohbet", onClick = {})
                    MuhabbetExtendedFab(text = "Yeni grup", icon = Muhabbet.icons.Group, onClick = {})
                }
            }
            // Both locales (#770): Turkish has the longest tab names, English the second longest.
            listOf(
                listOf("Sohbetler", "Güncellemeler", "Topluluklar", "Aramalar"),
                listOf("Chats", "Updates", "Communities", "Calls"),
            ).forEach { labels ->
                MuhabbetNavBar(
                    items = listOf(
                        MuhabbetNavItem(icon = Muhabbet.icons.Person, label = labels[0], badgeCount = 3),
                        MuhabbetNavItem(icon = Muhabbet.icons.Notifications, label = labels[1]),
                        MuhabbetNavItem(icon = Muhabbet.icons.Group, label = labels[2]),
                        MuhabbetNavItem(icon = Muhabbet.icons.Settings, label = labels[3]),
                    ),
                    selectedIndex = 0,
                    onSelect = {},
                )
            }
        }
    }

    @Test
    fun scaffold() = MuhabbetSnapshotMatrix.capture(rule, "scaffold") {
        Column(Modifier.width(Muhabbet.sizes.BubbleMaxWidth).height(Muhabbet.sizes.BubbleMaxWidth)) {
            MuhabbetScaffold(
                topBar = { MuhabbetTopBar(title = "Muhabbet") },
                floatingActionButton = { MuhabbetFab(icon = Muhabbet.icons.Add, contentDescription = "Yeni", onClick = {}) },
            ) { Text("İçerik") }
        }
    }
}
