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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val solidColors = listOf(
    Color(0xFFE8D5B7), Color(0xFFB7D5E8), Color(0xFFD5E8B7),
    Color(0xFFE8B7D5), Color(0xFFB7E8D5), Color(0xFFD5B7E8),
    Color(0xFF2C3E50), Color(0xFF1A1A2E), Color(0xFF16213E),
    Color(0xFF0F3460), Color(0xFF533483), Color(0xFF2C2C54)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPickerScreen(
    onBack: () -> Unit,
    wallpaperRepository: WallpaperRepository = koinInject()
) {
    var selectedType by remember { mutableStateOf(wallpaperRepository.getWallpaperType()) }
    var selectedColor by remember { mutableStateOf(wallpaperRepository.getSolidColor()) }
    var darkModeEnabled by remember { mutableStateOf(wallpaperRepository.getDarkModeWallpaperEnabled()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.wallpaper_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
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
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(color)
                                    .then(
                                        if (selectedColor == colorHex)
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                        else Modifier
                                    )
                                    .clickable {
                                        selectedColor = colorHex
                                        wallpaperRepository.setSolidColor(colorHex)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == colorHex) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
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
                                Icons.Default.Image,
                                contentDescription = stringResource(Res.string.wallpaper_custom),
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(MuhabbetSpacing.Medium))
                            Button(onClick = { /* TODO: open gallery picker */ }) {
                                Text(stringResource(Res.string.wallpaper_custom))
                            }
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
                Switch(
                    checked = darkModeEnabled,
                    onCheckedChange = {
                        darkModeEnabled = it
                        wallpaperRepository.setDarkModeWallpaperEnabled(it)
                    }
                )
            }

            Spacer(Modifier.height(MuhabbetSpacing.Medium))

            // Remove wallpaper button
            Button(
                onClick = {
                    selectedType = "DEFAULT"
                    wallpaperRepository.setWallpaperType("DEFAULT")
                    wallpaperRepository.setSolidColor(null)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.wallpaper_remove))
            }
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
            .clip(RoundedCornerShape(8.dp))
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
    return "#%02X%02X%02X".format(r, g, b)
}
