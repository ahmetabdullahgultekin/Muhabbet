package com.muhabbet.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.app.data.remote.GiphyClient
import com.muhabbet.app.data.remote.GiphyGif
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.muhabbet.designsystem.Muhabbet

/** Which tab of the shared GIF/sticker sheet a caller wants opened first. */
enum class GifStickerTab { GIF, STICKER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifStickerPicker(
    onDismiss: () -> Unit,
    onGifSelected: (url: String, previewUrl: String) -> Unit,
    onStickerSelected: (url: String, previewUrl: String) -> Unit,
    initialTab: GifStickerTab = GifStickerTab.GIF
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val giphyClient = remember { GiphyClient() }

    var selectedTab by remember { mutableStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var gifs by remember { mutableStateOf<List<GiphyGif>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load trending on first display and when tab changes
    LaunchedEffect(selectedTab) {
        isLoading = true
        gifs = if (selectedTab == GifStickerTab.GIF) {
            giphyClient.getTrending()
        } else {
            giphyClient.getTrendingStickers()
        }
        isLoading = false
    }

    // Debounced search
    LaunchedEffect(searchQuery, selectedTab) {
        if (searchQuery.isBlank()) {
            isLoading = true
            gifs = if (selectedTab == GifStickerTab.GIF) giphyClient.getTrending() else giphyClient.getTrendingStickers()
            isLoading = false
            return@LaunchedEffect
        }
        delay(400) // debounce
        isLoading = true
        gifs = if (selectedTab == GifStickerTab.GIF) {
            giphyClient.searchGifs(searchQuery)
        } else {
            giphyClient.searchStickers(searchQuery)
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().height(420.dp)) {
            // Tab row: GIF | Stickers
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == GifStickerTab.GIF,
                    onClick = { selectedTab = GifStickerTab.GIF; searchQuery = "" },
                    text = { Text(stringResource(Res.string.attach_gif)) }
                )
                Tab(
                    selected = selectedTab == GifStickerTab.STICKER,
                    onClick = { selectedTab = GifStickerTab.STICKER; searchQuery = "" },
                    text = { Text(stringResource(Res.string.sticker_title)) }
                )
            }

            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(Res.string.gif_search_placeholder)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Medium, vertical = MuhabbetSpacing.Small),
                singleLine = true,
                shape = RoundedCornerShape(MuhabbetCorners.Pill),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Muhabbet.icons.Close, contentDescription = stringResource(Res.string.action_close), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )

            if (gifs.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.gif_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(MuhabbetSpacing.XSmall),
                    horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall),
                    verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall),
                    modifier = Modifier.weight(1f)
                ) {
                    items(gifs, key = { it.id }) { gif ->
                        val previewUrl = gif.images.fixed_width?.url ?: gif.images.fixed_height?.url ?: ""
                        val fullUrl = gif.images.original?.url ?: previewUrl

                        AsyncImage(
                            model = previewUrl,
                            contentDescription = gif.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    scope.launch {
                                        if (selectedTab == GifStickerTab.GIF) {
                                            onGifSelected(fullUrl, previewUrl)
                                        } else {
                                            onStickerSelected(fullUrl, previewUrl)
                                        }
                                        sheetState.hide()
                                        onDismiss()
                                    }
                                },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            // GIPHY attribution
            Text(
                text = stringResource(Res.string.gif_powered_by),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = MuhabbetSpacing.Small)
            )
        }
    }
}
