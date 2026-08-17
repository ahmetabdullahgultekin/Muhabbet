package com.muhabbet.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.WallpaperRepository
import com.muhabbet.app.platform.rememberImagePickerLauncher
import com.muhabbet.app.platform.rememberWallpaperImageSaver
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
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
import com.muhabbet.designsystem.theme.MuhabbetWallpapers

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
    var darkModeEnabled by remember { mutableStateOf(wallpaperRepository.getDarkModeWallpaperEnabled()) }
    var customWallpaperSet by remember { mutableStateOf(wallpaperRepository.getCustomPath() != null) }

    // Gallery picker: on result, copy the bytes into app-private storage and persist THAT path —
    // img.fileName alone is just a label the picker made up, not a location the chat screen could
    // ever open (#380). If the copy fails, leave the previous selection in place rather than
    // pointing the chat at a file that doesn't exist.
    val wallpaperImageSaver = rememberWallpaperImageSaver()
    val galleryPicker = rememberImagePickerLauncher { pickedImage ->
        pickedImage?.let { img ->
            val savedPath = wallpaperImageSaver.save(img.fileName, img.bytes) ?: return@let
            wallpaperRepository.setCustomPath(savedPath)
            wallpaperRepository.setWallpaperType("CUSTOM")
            selectedType = "CUSTOM"
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
            // Type selection row
            Text(
                text = stringResource(Res.string.wallpaper_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(MuhabbetSpacing.Medium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)
            ) {
                WallpaperTypeButton(
                    label = stringResource(Res.string.wallpaper_default),
                    isSelected = selectedType == "DEFAULT",
                    onClick = {
                        selectedType = "DEFAULT"
                        wallpaperRepository.setWallpaperType("DEFAULT")
                    },
                    modifier = Modifier.weight(1f)
                )
                WallpaperTypeButton(
                    label = stringResource(Res.string.wallpaper_solid),
                    isSelected = selectedType == "SOLID",
                    onClick = {
                        selectedType = "SOLID"
                        wallpaperRepository.setWallpaperType("SOLID")
                    },
                    modifier = Modifier.weight(1f)
                )
                WallpaperTypeButton(
                    label = stringResource(Res.string.wallpaper_custom),
                    isSelected = selectedType == "CUSTOM",
                    onClick = {
                        selectedType = "CUSTOM"
                        wallpaperRepository.setWallpaperType("CUSTOM")
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(MuhabbetSpacing.XLarge))

            when (selectedType) {
                "SOLID" -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(solidColors) { color ->
                            val colorHex = colorToHex(color)
                            // The swatch is the one ground the palette does not choose, so the tick
                            // is derived from it rather than fixed. It used to be a hardcoded white
                            // one, which on the six pale swatches was a white tick on near-white.
                            val swatch = readableContentOn(color)
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(color)
                                    .then(
                                        if (selectedColor == colorHex)
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                                        else Modifier
                                    )
                                    .clickable {
                                        selectedColor = colorHex
                                        wallpaperRepository.setSolidColor(colorHex)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == colorHex) {
                                    // The swatch itself is a bare coloured Box with no text, so this
                                    // check mark is the only thing a screen reader can announce for it.
                                    Icon(
                                        Muhabbet.icons.Sent,
                                        contentDescription = stringResource(Res.string.a11y_selected),
                                        tint = swatch.content,
                                        modifier = Modifier.size(MuhabbetSizes.IconLarge)
                                    )
                                }
                            }
                        }
                    }
                }
                "CUSTOM" -> {
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

            // Dark mode wallpaper toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.wallpaper_dark_mode),
                    style = MaterialTheme.typography.bodyLarge
                )
                MuhabbetSwitch(
                    checked = darkModeEnabled,
                    onCheckedChange = {
                        darkModeEnabled = it
                        wallpaperRepository.setDarkModeWallpaperEnabled(it)
                    }
                )
            }

            Spacer(Modifier.height(MuhabbetSpacing.Medium))

            // Remove wallpaper button
            MuhabbetButton(
                text = stringResource(Res.string.wallpaper_remove),
                onClick = {
                    selectedType = "DEFAULT"
                    wallpaperRepository.setWallpaperType("DEFAULT")
                    wallpaperRepository.setSolidColor(null)
                },
                modifier = Modifier.fillMaxWidth(),
                role = MuhabbetButtonRole.Primary
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
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
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
