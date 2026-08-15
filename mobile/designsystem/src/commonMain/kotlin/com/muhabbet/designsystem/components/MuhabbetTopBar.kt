package com.muhabbet.designsystem.components

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
import androidx.compose.ui.text.style.TextOverflow
import com.muhabbet.designsystem.theme.MuhabbetTextStyles

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
     * Still `primary` — the inherited green — deliberately. Collapsing 23 duplicated blocks and
     * changing what they paint are two different changes, and doing them together would leave the
     * app half-rebranded: white bars above WhatsApp-green bubbles. The switch to `surface` happens
     * with the palette, as one coherent move. When it does, it is this function and nothing else.
     */
    // Not @ReadOnlyComposable: TopAppBarDefaults.topAppBarColors caches into the composition.
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun colors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primary,
        scrolledContainerColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onPrimary,
        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
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
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MuhabbetTextStyles.TopBarTitle,
                // Long group names and Turkish titles overflow more often than English ones; a bar
                // title should never wrap the layout.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
