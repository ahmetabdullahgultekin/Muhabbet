package com.muhabbet.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.painter.BrushPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetCard
import com.muhabbet.designsystem.components.MuhabbetCheckbox
import com.muhabbet.designsystem.components.MuhabbetCountBadge
import com.muhabbet.designsystem.components.MuhabbetDateTimeLabels
import com.muhabbet.designsystem.components.MuhabbetDateTimePicker
import com.muhabbet.designsystem.components.MuhabbetDateTimeSheet
import com.muhabbet.designsystem.components.MuhabbetImage
import com.muhabbet.designsystem.components.MuhabbetImageFrame
import com.muhabbet.designsystem.components.MuhabbetImagePhase
import com.muhabbet.designsystem.components.MuhabbetImagePlaceholder
import com.muhabbet.designsystem.components.MuhabbetListDensity
import com.muhabbet.designsystem.components.MuhabbetListItem
import com.muhabbet.designsystem.components.MuhabbetListSelection
import com.muhabbet.designsystem.components.MuhabbetPersonRow
import com.muhabbet.designsystem.components.MuhabbetPill
import com.muhabbet.designsystem.components.MuhabbetPullRefresh
import com.muhabbet.designsystem.components.MuhabbetRadio
import com.muhabbet.designsystem.components.MuhabbetSearchField
import com.muhabbet.designsystem.components.MuhabbetSpinner
import com.muhabbet.designsystem.components.MuhabbetSpinnerSize
import com.muhabbet.designsystem.components.MuhabbetTabs
import com.muhabbet.designsystem.theme.MuhabbetDepth
import com.muhabbet.designsystem.theme.MuhabbetIcons
import com.muhabbet.designsystem.theme.MuhabbetTone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the second wave of components (#847): rows, media, inputs, progress, badges,
 * surfaces, navigation and pickers. Same matrix as every other golden — three themes, three font
 * scales, one RTL — and the same fixture rule: Turkish copy carrying İ ı Ğ ş, never product strings.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1280dp-xhdpi")
class CatalogueScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun listItems() = MuhabbetSnapshotMatrix.capture(rule, "listitem") {
        Column {
            MuhabbetListItem(headline = "Bildirimler", onClick = {})
            MuhabbetListItem(
                headline = "Depolama ve veri kullanımı ağ ayarları uzun bir başlık",
                supporting = "Ağ kullanımı, otomatik indirme",
                trailing = { Text("12 GB") },
                onClick = {}
            )
            MuhabbetListItem(
                headline = "Sıkı yoğunluk",
                supporting = "Bir sayfanın içindeki liste",
                density = MuhabbetListDensity.Compact,
                onClick = {}
            )
            MuhabbetListItem(headline = "Devre dışı", supporting = "Şu an kullanılamıyor", enabled = false, onClick = {})
        }
    }

    @Test
    fun personRows() = MuhabbetSnapshotMatrix.capture(rule, "personrow") {
        Column {
            MuhabbetPersonRow(
                name = "Işıl Güneş",
                avatarUrl = null,
                supporting = "Merhaba! Yarın görüşelim mi?",
                trailing = { MuhabbetCountBadge(count = 3) },
                onClick = {}
            )
            MuhabbetPersonRow(
                name = "Aile Grubu",
                avatarUrl = null,
                isGroup = true,
                supporting = "Ömer: Fotoğraf",
                trailing = { MuhabbetPill(text = "Yönetici", tone = MuhabbetTone.Brand) },
                onClick = {}
            )
            MuhabbetPersonRow(
                name = "Çağrı Şahin",
                avatarUrl = null,
                supporting = "Seçili",
                selection = MuhabbetListSelection.Selected,
                onClick = {}
            )
            MuhabbetPersonRow(
                name = "İpek Doğan",
                avatarUrl = null,
                selection = MuhabbetListSelection.Unselected,
                density = MuhabbetListDensity.Compact,
                onClick = {}
            )
        }
    }

    @Test
    fun images() = MuhabbetSnapshotMatrix.capture(rule, "image") {
        val tile = Modifier.size(Muhabbet.sizes.AvatarXXLarge)
        val shape = RoundedCornerShape(Muhabbet.corners.Medium)
        // A gradient painter stands in for a decoded bitmap: deterministic, no network, and visibly
        // not a placeholder.
        val loaded = BrushPainter(
            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Small)) {
            MuhabbetImageFrame(
                phase = MuhabbetImagePhase.Loading, painter = null, contentDescription = null, modifier = tile,
                shape = shape, contentScale = ContentScale.Crop, placeholder = MuhabbetImagePlaceholder.Shimmer,
                crossfade = true, fallbackIcon = MuhabbetIcons.Image
            )
            MuhabbetImageFrame(
                phase = MuhabbetImagePhase.Loading, painter = null, contentDescription = null, modifier = tile,
                shape = shape, contentScale = ContentScale.Crop, placeholder = MuhabbetImagePlaceholder.Solid,
                crossfade = true, fallbackIcon = MuhabbetIcons.Image
            )
            MuhabbetImageFrame(
                phase = MuhabbetImagePhase.Loaded, painter = loaded, contentDescription = null, modifier = tile,
                shape = shape, contentScale = ContentScale.Crop, placeholder = MuhabbetImagePlaceholder.Shimmer,
                crossfade = true, fallbackIcon = MuhabbetIcons.Image
            )
            // A null model is the failed state through the public entry point.
            MuhabbetImage(model = null, contentDescription = "Fotoğraf", modifier = tile, shape = shape)
        }
    }

    @Test
    fun searchField() = MuhabbetSnapshotMatrix.capture(rule, "searchfield") {
        Column(verticalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Medium)) {
            MuhabbetSearchField(query = "", onQueryChange = {}, onSearch = {}, placeholder = "Sohbetlerde ara", clearContentDescription = "Temizle")
            MuhabbetSearchField(query = "Işıl", onQueryChange = {}, onSearch = {}, placeholder = "Sohbetlerde ara", clearContentDescription = "Temizle", loading = true)
            MuhabbetSearchField(query = "", onQueryChange = {}, onSearch = {}, placeholder = "Devre dışı", clearContentDescription = "Temizle", enabled = false)
        }
    }

    @Test
    fun spinners() = MuhabbetSnapshotMatrix.capture(rule, "spinner") {
        Row(horizontalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Large)) {
            MuhabbetSpinner(size = MuhabbetSpinnerSize.Small)
            MuhabbetSpinner(size = MuhabbetSpinnerSize.Medium)
            MuhabbetSpinner(size = MuhabbetSpinnerSize.Large)
            MuhabbetSpinner(size = MuhabbetSpinnerSize.Medium, progress = 0.4f)
            MuhabbetSpinner(size = MuhabbetSpinnerSize.Large, progress = 0.75f)
        }
    }

    @Test
    fun badgesAndPills() = MuhabbetSnapshotMatrix.capture(rule, "badge") {
        Column(verticalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Medium)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Small)) {
                MuhabbetCountBadge(count = 1)
                MuhabbetCountBadge(count = 42)
                MuhabbetCountBadge(count = 1342)
                // Zero draws nothing; it stays in the fixture so a regression that renders "0" shows.
                MuhabbetCountBadge(count = 0)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Small)) {
                MuhabbetTone.entries.forEach { tone -> MuhabbetPill(text = tone.name) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Muhabbet.spacing.XSmall)) {
                MuhabbetTone.entries.forEach { tone ->
                    MuhabbetPill(text = "Bağlı · $tone", tone = tone, icon = MuhabbetIcons.Lock)
                }
            }
        }
    }

    @Test
    fun cards() = MuhabbetSnapshotMatrix.capture(rule, "card") {
        Column(verticalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Large)) {
            MuhabbetDepth.entries.forEach { depth ->
                MuhabbetCard(depth = depth, modifier = Modifier.fillMaxWidth()) {
                    Text("Derinlik · $depth")
                    Text("Kartın içeriği ğüşıöç")
                }
            }
            MuhabbetCard(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Dokunulabilir kart") }
            MuhabbetCard(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Devre dışı kart") }
        }
    }

    @Test
    fun tabs() = MuhabbetSnapshotMatrix.capture(rule, "tabs") {
        Column(verticalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Large)) {
            MuhabbetTabs(tabs = listOf("Sohbetler", "Durum", "Aramalar"), selectedIndex = 0, onSelect = {})
            MuhabbetTabs(tabs = listOf("Medya", "Belgeler", "Bağlantılar"), pagerState = rememberPagerState(initialPage = 2) { 3 })
        }
    }

    @Test
    fun selectionControls() = MuhabbetSnapshotMatrix.capture(rule, "selection") {
        Column {
            Row {
                MuhabbetCheckbox(checked = true, onCheckedChange = {})
                MuhabbetCheckbox(checked = false, onCheckedChange = {})
                MuhabbetCheckbox(checked = true, onCheckedChange = {}, enabled = false)
            }
            Row {
                MuhabbetRadio(selected = true, onClick = {})
                MuhabbetRadio(selected = false, onClick = {})
                MuhabbetRadio(selected = false, onClick = {}, enabled = false)
            }
        }
    }

    @Test
    fun pullRefresh() = MuhabbetSnapshotMatrix.capture(rule, "pullrefresh") {
        MuhabbetPullRefresh(
            isRefreshing = true,
            onRefresh = {},
            modifier = Modifier.width(Muhabbet.sizes.BubbleMaxWidth).height(Muhabbet.sizes.PickerSheetMaxHeight)
        ) {
            LazyColumn {
                items(3) { index -> MuhabbetListItem(headline = "Sohbet ${index + 1}", supporting = "Yenileniyor…") }
            }
        }
    }

    @Test
    fun dateTimePicker() = MuhabbetSnapshotMatrix.capture(rule, "datetimepicker") {
        MuhabbetDateTimePicker(labels = Labels, onConfirm = {}, initialDateUtcMillis = FixedDate, initialHour = 14, initialMinute = 30)
    }

    @Test
    fun dateTimeSheet() = MuhabbetSnapshotMatrix.capture(rule, "datetimesheet", wholeScreen = true) {
        Box(Modifier.fillMaxWidth()) {
            MuhabbetDateTimeSheet(
                labels = Labels,
                onConfirm = {},
                onDismiss = {},
                initialDateUtcMillis = FixedDate,
                minDateUtcMillis = FixedDate,
                error = "Bu saat geçti"
            )
        }
    }

    private companion object {
        val Labels = MuhabbetDateTimeLabels(
            dateTitle = "Tarih seçin",
            timeTitle = "Saat seçin",
            next = "İleri",
            back = "Geri",
            confirm = "Zamanla"
        )

        /**
         * 2025-03-14 00:00 UTC. A fixed month in the past, so the calendar never shows "today" —
         * whose ring would otherwise move the golden every day it ran.
         */
        const val FixedDate = 1_741_910_400_000L
    }
}
