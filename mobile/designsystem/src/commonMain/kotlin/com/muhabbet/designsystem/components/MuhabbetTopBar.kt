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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.style.TextOverflow
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.theme.MuhabbetIcons
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes

/**
 * Colours every top bar shares — internal since #844, because its return type is Material's.
 *
 * There were 23 copies of a `TopAppBarDefaults.topAppBarColors(...)` block across 22 files, and
 * they did not agree: 19 painted `primary`, 4 painted `surface`, and 4 more screens set no colours
 * at all and fell through to Material's default. Two of those four are bottom-nav tabs, so simply
 * switching tabs changed the bar's colour. The two bespoke bars that used to read this object now
 * use the slot overload of [MuhabbetTopBar], so nothing outside the module names the type.
 */
internal object MuhabbetTopBarDefaults {

    /**
     * The bar's foreground palette. The container is transparent here on purpose: [MuhabbetTopBar]
     * paints it itself, in the draw phase, so the scroll lift never recomposes the bar (#845).
     *
     * The resting container is `surface`, not `primary`. A saturated bar across nineteen screens
     * was the single most recognisable inherited signature in the product, and restraint is most of
     * what reads as premium. The bar lifts to `surfaceContainer` on scroll, which is M3's own
     * elevation-by-colour and is only legible now that the container ramp is filled in.
     */
    // Not @ReadOnlyComposable: TopAppBarDefaults.topAppBarColors caches into the composition.
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun colors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * How far a screen's content has scrolled under its top bar — the design system's own state, so no
 * screen ever holds Material's `TopAppBarScrollBehavior` (#844).
 *
 * [MuhabbetScaffold] creates one, attaches its [nestedScrollConnection] to the content and hands it
 * to the bar slot, so every scrolling screen gets the lift without a line of its own (#845). It is
 * the pinned behaviour: the bar never moves, it only changes tone — a bar that slides away on a
 * messaging list hides the one search/compose affordance the user scrolled down to reach.
 *
 * The offset is accumulated from *consumed* post-scroll deltas, which is what Material's pinned
 * behaviour does too, and reset to exactly zero when an upward drag hits the top, so float drift
 * cannot leave a faint hairline on a list that is back at rest.
 */
@Stable
class MuhabbetTopBarScroll internal constructor(initialContentOffset: Float = 0f) {

    /** Negative once content has moved up under the bar; zero at the top. Read only in draw. */
    internal var contentOffset by mutableFloatStateOf(initialContentOffset)

    /** 0 at rest, 1 once content has scrolled [liftDistancePx] under the bar. */
    internal fun liftFraction(liftDistancePx: Float): Float =
        // Guarded on the sign rather than left to coerceIn: -0f / d is -0.0, which coerceIn(0f, 1f)
        // returns unchanged, so "at rest" would not compare equal to 0.
        if (liftDistancePx <= 0f || contentOffset >= 0f) 0f else (-contentOffset / liftDistancePx).coerceAtMost(1f)

    internal val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (consumed.y == 0f && available.y > 0f) {
                contentOffset = 0f
            } else {
                contentOffset += consumed.y
            }
            return Offset.Zero
        }
    }

    internal companion object {
        val Saver: Saver<MuhabbetTopBarScroll, Float> = Saver(
            save = { it.contentOffset },
            restore = { MuhabbetTopBarScroll(it) }
        )
    }
}

/** A [MuhabbetTopBarScroll] that survives configuration change, as the list position under it does. */
@Composable
fun rememberMuhabbetTopBarScroll(): MuhabbetTopBarScroll =
    rememberSaveable(saver = MuhabbetTopBarScroll.Saver) { MuhabbetTopBarScroll() }

/** Set by [MuhabbetScaffold] for its bar slot. Null outside a scaffold: the bar then stays at rest. */
internal val LocalMuhabbetTopBarScroll = staticCompositionLocalOf<MuhabbetTopBarScroll?> { null }

/**
 * The app bar for screens whose bar is a title, an optional back button, and some icon actions.
 *
 * That covers all but two bars. ChatScreen carries an avatar, a name and a presence subtitle;
 * HomeShellScreen transforms into a search field. Those use the slot overload below — same colours,
 * same lift, same hairline — instead of a raw `TopAppBar` borrowing shared colours.
 *
 * Two things beyond colour, both restrained on purpose:
 *  - The title crossfades rather than cutting when it changes — a group name that resolves after
 *    the screen is already open, or a "Chat" placeholder swapping for a real one, reads as the bar
 *    catching up rather than flickering.
 *  - As content scrolls under the bar the container lifts to `surfaceContainer` and a hairline fades
 *    in at the bottom edge, both scroll-linked over [MuhabbetSizes.TopBarLiftDistance], both gone
 *    again the moment the list is back at the top. Nothing to pass: [MuhabbetScaffold] supplies the
 *    scroll state.
 *
 * @param onBack when non-null, renders a back button. Screens that are a tab root pass null.
 * @param actions trailing icons, normally [MuhabbetIconButton]s.
 */
@Composable
fun MuhabbetTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    MuhabbetTopBar(
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
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                MuhabbetIconButton(
                    icon = MuhabbetIcons.Back,
                    contentDescription = backContentDescription,
                    onClick = onBack
                )
            }
        },
        actions = actions
    )
}

/**
 * The bar with a free-form [title] and [navigationIcon], for the two screens whose bar is more than
 * a string. Everything else — tone, lift, hairline, content colours — is identical to the string
 * overload, so a bespoke bar can no longer drift from the rest.
 *
 * **The lift is read in the draw phase.** The scroll offset changes on every frame of a scroll; read
 * during composition, as `overlappedFraction` was before #845, it would recompose the whole bar —
 * title animation included — on each of those frames. Read inside `drawWithContent`, it only re-runs
 * that one draw.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuhabbetTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val scroll = LocalMuhabbetTopBarScroll.current
    val restingColor = MaterialTheme.colorScheme.surface
    val liftedColor = MaterialTheme.colorScheme.surfaceContainer
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant
    TopAppBar(
        modifier = modifier.drawWithContent {
            val lift = scroll?.liftFraction(MuhabbetSizes.TopBarLiftDistance.toPx()) ?: 0f
            drawRect(lerp(restingColor, liftedColor, lift))
            drawContent()
            if (lift > 0f) {
                drawLine(
                    color = hairlineColor.copy(alpha = hairlineColor.alpha * lift),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = MuhabbetSizes.BorderHairline.toPx()
                )
            }
        },
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = MuhabbetTopBarDefaults.colors()
    )
}
