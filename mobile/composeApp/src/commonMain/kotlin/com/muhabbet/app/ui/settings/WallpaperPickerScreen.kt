package com.muhabbet.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.WallpaperRepository
import com.muhabbet.app.platform.rememberImagePickerLauncher
import com.muhabbet.app.platform.rememberWallpaperImageSaver
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.LocalThemeMode
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.ResolvedThemeMode
import com.muhabbet.designsystem.theme.readableContentOn
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetSwitch
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.theme.MuhabbetWallpaperGradients
import com.muhabbet.designsystem.theme.MuhabbetWallpapers
import com.muhabbet.designsystem.modifier.pressable

// The swatches live in the design system with every other colour, so a screen still cannot name a
// hex. The old set was a navy-and-purple palette from before the rebrand; a cool violet behind
// copper bubbles reads as two different apps stacked on each other.
private val solidColors = MuhabbetWallpapers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPickerScreen(
    onBack: () -> Unit,
    wallpaperRepository: WallpaperRepository = koinInject()
) {
    var selectedType by remember { mutableStateOf(wallpaperRepository.getWallpaperType()) }
    var selectedColor by remember { mutableStateOf(wallpaperRepository.getSolidColor()) }
    var selectedGradientId by remember { mutableStateOf(wallpaperRepository.getGradientId()) }
    var darkModeEnabled by remember { mutableStateOf(wallpaperRepository.getDarkModeWallpaperEnabled()) }
    var customWallpaperSet by remember { mutableStateOf(wallpaperRepository.getCustomPath() != null) }

    // The same question ChatWallpaper asks, asked the same way: OLED is a dark theme, not a third
    // thing. Both sides of the wallpaper feature must agree on that or the picker would explain a
    // suppression the chat is not doing, or stay silent about one it is.
    val isDarkTheme = LocalThemeMode.current != ResolvedThemeMode.Light

    // Gallery picker: on result, copy the bytes into app-private storage and persist THAT path —
    // img.fileName alone is just a label the picker made up, not a location the chat screen could
    // ever open (#380). If the copy fails, leave the previous selection in place rather than
    // pointing the chat at a file that doesn't exist.
    val wallpaperImageSaver = rememberWallpaperImageSaver()
    val galleryPicker = rememberImagePickerLauncher { pickedImage ->
        pickedImage?.let { img ->
            val savedPath = wallpaperImageSaver.save(img.fileName, img.bytes) ?: return@let
            wallpaperRepository.setCustomPath(savedPath)
            wallpaperRepository.setWallpaperType(WallpaperRepository.TYPE_CUSTOM)
            selectedType = WallpaperRepository.TYPE_CUSTOM
            customWallpaperSet = true
        }
    }

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.wallpaper_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(MuhabbetSpacing.XLarge)
        ) {
            // Type selection
            Text(
                text = stringResource(Res.string.wallpaper_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(MuhabbetSpacing.Medium))

            // Two rows of two rather than one row of four. Four columns inside the screen padding
            // leave each label about 80dp on a 360dp phone, which "Renk Geçişi" does not fit in — and
            // a truncated type name on the control whose only job is to name the types is not a
            // trade worth making. The pairs are built once and chunked so a fifth type cannot be
            // added to the list without also getting a button.
            val wallpaperTypes = listOf(
                stringResource(Res.string.wallpaper_default) to WallpaperRepository.TYPE_DEFAULT,
                stringResource(Res.string.wallpaper_solid) to WallpaperRepository.TYPE_SOLID,
                stringResource(Res.string.wallpaper_gradient) to WallpaperRepository.TYPE_GRADIENT,
                stringResource(Res.string.wallpaper_custom) to WallpaperRepository.TYPE_CUSTOM
            )
            wallpaperTypes.chunked(2).forEachIndexed { rowIndex, rowTypes ->
                if (rowIndex > 0) Spacer(Modifier.height(MuhabbetSpacing.Medium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)
                ) {
                    rowTypes.forEach { (label, type) ->
                        WallpaperTypeButton(
                            label = label,
                            isSelected = selectedType == type,
                            onClick = {
                                selectedType = type
                                wallpaperRepository.setWallpaperType(type)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(MuhabbetSpacing.XLarge))

            when (selectedType) {
                WallpaperRepository.TYPE_SOLID -> {
                    WallpaperSwatchGrid {
                        items(solidColors) { color ->
                            val colorHex = colorToHex(color)
                            WallpaperSwatch(
                                brush = SolidColor(color),
                                // The swatch is the one ground the palette does not choose, so the
                                // tick is derived from it rather than fixed. It used to be a
                                // hardcoded white one, which on the pale swatches was a white tick
                                // on near-white.
                                tickColor = readableContentOn(color).content,
                                isSelected = selectedColor == colorHex,
                                onClick = {
                                    selectedColor = colorHex
                                    wallpaperRepository.setSolidColor(colorHex)
                                }
                            )
                        }
                    }
                }
                WallpaperRepository.TYPE_GRADIENT -> {
                    WallpaperSwatchGrid {
                        items(MuhabbetWallpaperGradients, key = { it.id }) { gradient ->
                            WallpaperSwatch(
                                brush = gradient.brush,
                                // Read off the far stop, the darker end of every gradient in the
                                // set: the tick sits at the swatch's centre, and at 6–13 points of
                                // L* travel both ends resolve to the same content colour anyway, so
                                // taking the worse one costs nothing and cannot be wrong.
                                tickColor = readableContentOn(gradient.stops.last()).content,
                                isSelected = selectedGradientId == gradient.id,
                                onClick = {
                                    selectedGradientId = gradient.id
                                    wallpaperRepository.setGradientId(gradient.id)
                                }
                            )
                        }
                    }
                }
                WallpaperRepository.TYPE_CUSTOM -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Muhabbet.icons.Image,
                                contentDescription = stringResource(Res.string.wallpaper_custom),
                                modifier = Modifier.size(48.dp),
                                tint = if (customWallpaperSet) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(MuhabbetSpacing.Medium))
                            if (customWallpaperSet) {
                                Text(
                                    text = stringResource(Res.string.wallpaper_gallery_set),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(MuhabbetSpacing.Small))
                            }
                            MuhabbetButton(
                                text = stringResource(Res.string.wallpaper_choose_from_gallery),
                                onClick = { galleryPicker.launch() },
                                role = MuhabbetButtonRole.Primary
                            )
                        }
                    }
                }
                else -> {
                    Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(MuhabbetSpacing.Large))

            // Dark mode wallpaper toggle.
            //
            // Off means the chosen wallpaper is not painted in the dark or OLED themes at all — see
            // WallpaperRepository.resolveWallpaper. That is the switch's actual effect and the row
            // never said it, so a user who picked a wallpaper and then switched to OLED watched it
            // vanish with nothing on any screen to connect the two (#548). The subtitle states it,
            // and the notice below fires when it is happening right now.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.wallpaper_dark_mode),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(Res.string.wallpaper_dark_mode_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(MuhabbetSpacing.Medium))
                MuhabbetSwitch(
                    checked = darkModeEnabled,
                    onCheckedChange = {
                        darkModeEnabled = it
                        wallpaperRepository.setDarkModeWallpaperEnabled(it)
                    }
                )
            }

            // Keyed on every piece of state the answer depends on, including the ones the pickers
            // above write straight through to storage: without them the notice would go on claiming
            // the wallpaper is hidden after the user had removed it.
            val selectionHidden = remember(
                isDarkTheme, darkModeEnabled, selectedType, selectedColor, selectedGradientId, customWallpaperSet
            ) {
                wallpaperRepository.isSelectionHiddenByDarkTheme(isDarkTheme)
            }
            if (selectionHidden) {
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                Text(
                    text = stringResource(Res.string.wallpaper_hidden_in_dark_theme),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(MuhabbetSpacing.Medium))

            // Remove wallpaper button
            MuhabbetButton(
                text = stringResource(Res.string.wallpaper_remove),
                onClick = {
                    // Clear the on-screen selection as well as the stored one. Removing used to null
                    // the stored colour and leave `selectedColor` set, so the grid went on showing a
                    // tick against a colour storage no longer held — a picker claiming a selection
                    // the chat is not painting, which is the whole of #380 in miniature.
                    selectedType = WallpaperRepository.TYPE_DEFAULT
                    selectedColor = null
                    selectedGradientId = null
                    wallpaperRepository.setWallpaperType(WallpaperRepository.TYPE_DEFAULT)
                    wallpaperRepository.setSolidColor(null)
                    wallpaperRepository.setGradientId(null)
                },
                modifier = Modifier.fillMaxWidth(),
                role = MuhabbetButtonRole.Primary
            )
        }
    }
}

/**
 * The grid both swatch tabs are laid out in.
 *
 * Shared so that solids and gradients cannot drift into two different column counts or two different
 * gutters — they sit behind the same four type buttons and have to read as one screen.
 */
@Composable
private fun ColumnScope.WallpaperSwatchGrid(content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium),
        modifier = Modifier.weight(1f),
        content = content
    )
}

/**
 * One pickable wallpaper, solid or gradient.
 *
 * Takes a [Brush] rather than a [Color] so the gradient tab is the same control as the solid tab
 * rather than a copy of it: `SolidColor(color)` is a brush, and the selection border, the tick and
 * the touch target then cannot diverge between the two.
 */
@Composable
private fun WallpaperSwatch(
    brush: Brush,
    tickColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(brush)
            .then(
                if (isSelected)
                    Modifier.border(MuhabbetSizes.BorderActive, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            // The swatch is a bare filled Box with no text, so this check mark is the only thing a
            // screen reader has to announce for it.
            Icon(
                Muhabbet.icons.Sent,
                contentDescription = stringResource(Res.string.a11y_selected),
                tint = tickColor,
                modifier = Modifier.size(MuhabbetSizes.IconLarge)
            )
        }
    }
}

@Composable
private fun WallpaperTypeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .pressable(
                shape = MaterialTheme.shapes.small,
                background = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                onClick = onClick
            )
            .padding(vertical = MuhabbetSpacing.Medium),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun colorToHex(color: Color): String {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return "#${r.toString(16).padStart(2, '0').uppercase()}${g.toString(16).padStart(2, '0').uppercase()}${b.toString(16).padStart(2, '0').uppercase()}"
}
