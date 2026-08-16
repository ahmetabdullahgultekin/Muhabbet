package com.muhabbet.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes

/**
 * Colours and metrics every top bar shares — including the two bespoke ones.
 *
 * There were 23 copies of a `TopAppBarDefaults.topAppBarColors(...)` block across 22 files, and
 * they did not agree: 19 painted `primary`, 4 painted `surface`, and 4 more screens set no colours
 * at all and fell through to Material's default. Two of those four are bottom-nav tabs, so simply
 * switching tabs changed the bar's colour.
 */
object MuhabbetTopBarDefaults {

    /**
     * The one top-bar palette.
     *
     * `surface`, not `primary`. A saturated bar across nineteen screens was the single most
     * recognisable inherited signature in the product, and restraint is most of what reads as
     * premium; the accent is worth more spent on the few things that actually need pointing at.
     * The bar lifts to `surfaceContainer` on scroll, which is M3's own elevation-by-colour and is
     * only legible now that the container ramp is filled in.
     *
     * This landing with the palette rather than with the component extraction is deliberate: doing
     * it earlier would have left the app half-rebranded, white bars above green bubbles.
     */
    // Not @ReadOnlyComposable: TopAppBarDefaults.topAppBarColors caches into the composition.
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun colors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The app bar for screens whose bar is a title, an optional back button, and some icon actions.
 *
 * That covers 23 of the 26 hand-rolled bars. The remaining three keep their own implementation
 * because they genuinely differ — ChatScreen carries an avatar, a name and a presence subtitle;
 * HomeShellScreen transforms into a search field — but they pull their colours from
 * [MuhabbetTopBarDefaults] so nothing drifts again. A single bar with nine parameters covering all
 * of them would be a god-component; what is shared here is the tokens and the defaults, not
 * enforced uniformity.
 *
 * Two things beyond colour, both restrained on purpose since a saturated bar was already ruled out
 * above:
 *  - The title crossfades rather than cutting when it changes — a group name that resolves after
 *    the screen is already open, or a "Chat" placeholder swapping for a real one, reads as the bar
 *    catching up rather than flickering.
 *  - A hairline fades in at the bottom edge as content scrolls under the bar, driven directly by
 *    [TopAppBarScrollBehavior]'s own `overlappedFraction` rather than a second animation — a second,
 *    quieter signal alongside the colour lift, gone again the moment the list is back at the top.
 *
 * @param onBack when non-null, renders a back button. Screens that are a tab root pass null.
 * @param actions trailing icons, normally [MuhabbetIconButton]s.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuhabbetTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val overlapped = scrollBehavior?.state?.overlappedFraction ?: 0f
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant
    TopAppBar(
        modifier = modifier.drawWithContent {
            drawContent()
            if (overlapped > 0f) {
                drawLine(
                    color = hairlineColor.copy(alpha = overlapped.coerceIn(0f, 1f)),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = MuhabbetSizes.BorderHairline.toPx()
                )
            }
        },
        title = {
            AnimatedContent(
                targetState = title,
                transitionSpec = { MuhabbetMotion.enterFadeUp togetherWith MuhabbetMotion.exitFadeDown },
                label = "topBarTitle"
            ) { animatedTitle ->
                Text(
                    text = animatedTitle,
                    style = Muhabbet.text.TopBarTitle,
                    // Long group names and Turkish titles overflow more often than English ones; a bar
                    // title should never wrap the layout.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            if (onBack != null) {
                MuhabbetIconButton(
                    icon = com.muhabbet.designsystem.theme.MuhabbetIcons.Back,
                    contentDescription = backContentDescription,
                    onClick = onBack
                )
            }
        },
        actions = actions,
        colors = MuhabbetTopBarDefaults.colors(),
        scrollBehavior = scrollBehavior
    )
}
