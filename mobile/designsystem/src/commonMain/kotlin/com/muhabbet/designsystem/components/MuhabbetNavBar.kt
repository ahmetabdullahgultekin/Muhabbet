package com.muhabbet.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import kotlin.math.roundToInt

/**
 * One destination in [MuhabbetNavBar].
 *
 * A data class rather than a slot API on purpose. The bar has to know how many destinations there
 * are and which one is selected in order to place the travelling rail at all, and a `RowScope`
 * content lambda hides both. It also means every item is guaranteed the same treatment — the four
 * hand-written `NavigationBarItem` blocks this replaced had drifted into four copies of an identical
 * eleven-line colour block, which is exactly how a bar ends up with three different selected states.
 *
 * @param contentDescription what a screen reader announces for the icon. Defaults to [label],
 *   because on a bottom bar the label and the icon always say the same thing; pass something else
 *   only when they genuinely differ.
 * @param badgeCount unread items behind this destination. Zero renders nothing. **No call site feeds
 *   this yet** — the bottom bar has never shown a count and this component is not the place to
 *   invent one, but a nav bar that structurally cannot show a badge would have to be rebuilt the day
 *   the count exists, so the affordance is here and the number is not faked.
 */
@Immutable
data class MuhabbetNavItem(
    val icon: ImageVector,
    val label: String,
    val contentDescription: String? = null,
    val badgeCount: Int = 0,
    val testTag: String? = null
)

/**
 * The bottom navigation bar.
 *
 * This is the surface a user looks at for the entire session, and until now it was the most
 * recognisably stock thing in the app. Material's selected-item treatment is a filled capsule behind
 * the icon; the previous call site had already set `indicatorColor = Color.Transparent` to get rid
 * of it, which left selection encoded in **colour alone** — a fail for anyone who cannot separate
 * copper from grey, and visually no different from a row of four disabled icons.
 *
 * What replaces it comes from vocabulary the app already had rather than from a new idea:
 *
 *  - **A copper capsule rides the bar's top edge and slides to the selected tab.** It is the same
 *    mark as [MuhabbetStepRail]'s segment, at the same 28dp width, so "you are here" looks the same
 *    in sign-up and in the shell. It sits on a full-width hairline track in `outlineVariant`, so the
 *    mark reads as the lit part of a rail rather than as a stray dash, and the bar gets its
 *    separation from the content above it without a shadow — a bottom bar's shadow points off the
 *    bottom of the screen, and is invisible on OLED besides.
 *  - **The selected icon lifts by 2dp** and its label goes to SemiBold. Two more non-colour signals,
 *    both small; the rail is the one doing the work.
 *
 * **Motion is bounded in its own domain.** The rail's position is a spring in *tab-index* space and
 * is clamped to `0..lastIndex` by [railOffsetPx] before it becomes a pixel offset, so the spatial
 * spring's overshoot can never place the mark outside the bar. The lift and the colour are `effects`
 * springs (damping exactly 1), so neither can overshoot at all — an under-damped value undershooting
 * zero into a `padding()` is what took 0.3.0 down, and nothing here can reach a padding.
 *
 * Built on `Surface` and `Row` rather than on Material's `NavigationBar`, because the rail needs the
 * bar's exact width divided by the item count and `NavigationBar` inserts its own item spacing. The
 * two things it gave away for free are handled explicitly: the navigation-bar window insets are
 * applied below, and the row is a [selectableGroup] so a screen reader announces "1 of 4".
 */
@Composable
fun MuhabbetNavBar(
    items: List<MuhabbetNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val lastIndex = items.lastIndex

    // Spatial: this is a thing moving across a distance, so it is allowed to overshoot and settle.
    val railPosition by animateFloatAsState(
        targetValue = selectedIndex.coerceIn(0, lastIndex).toFloat(),
        animationSpec = MuhabbetMotion.spatialDefault(),
        label = "navRailPosition"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            // The surface colour still runs behind the system navigation bar; only the content is
            // inset. Without this the labels sit under the gesture pill, because the app draws
            // edge-to-edge whether it asks to or not since targetSdk 36.
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val itemWidth = maxWidth / items.size
                Column {
                    NavRail(
                        itemWidth = itemWidth,
                        position = { railPosition },
                        lastIndex = lastIndex
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEachIndexed { index, item ->
                            NavItem(
                                item = item,
                                selected = index == selectedIndex,
                                onClick = { onSelect(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The hairline track and the copper mark that travels along it. */
@Composable
private fun NavRail(
    itemWidth: Dp,
    position: () -> Float,
    lastIndex: Int
) {
    Box(modifier = Modifier.fillMaxWidth().height(MuhabbetSizes.NavRailHeight)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MuhabbetSizes.BorderHairline)
                .align(Alignment.TopStart)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                // Read inside the layout lambda so the spring animates without recomposing the four
                // items on every frame.
                .offset { IntOffset(railOffsetPx(position(), itemWidth, lastIndex), 0) }
                .width(itemWidth),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .width(MuhabbetSizes.NavRailWidth)
                    .height(MuhabbetSizes.NavRailHeight)
                    .clip(RoundedCornerShape(MuhabbetSizes.NavRailHeight / 2))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/**
 * Converts a spring's tab-index position into a horizontal pixel offset, clamped to the tabs that
 * exist.
 *
 * The clamp is here — where the animated value's own domain is known — rather than at the layout
 * call, so there is exactly one place that has to be right. [MuhabbetMotion.spatialDefault] is
 * under-damped by design and will overshoot past the first and last tab; without this the mark would
 * slide off the end of the bar on every trip to an edge tab.
 */
private fun Density.railOffsetPx(position: Float, itemWidth: Dp, lastIndex: Int): Int =
    (position.coerceIn(0f, lastIndex.toFloat()) * itemWidth.toPx()).roundToInt()

@Composable
private fun RowScope.NavItem(
    item: MuhabbetNavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val inactive = LocalSemanticColors.current.secondaryText
    // Effects spring: damping is exactly 1, so a colour animation cannot overshoot into a hue that
    // is not in the palette.
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else inactive,
        animationSpec = MuhabbetMotion.effectsDefault(),
        label = "navItemColor"
    )
    // 0..1, and it cannot leave that range: an effects spring does not overshoot. Multiplied into a
    // graphicsLayer translation, never into a padding.
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MuhabbetMotion.effectsDefault(),
        label = "navItemLift"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = MuhabbetSizes.NavItemMinHeight)
            // `selectable` is what publishes `selected` and `Role.Tab` to the accessibility tree;
            // the explicit `semantics` block below states the same thing so that the guarantee
            // survives someone swapping this for a plain `clickable`.
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics { this.selected = selected }
            .padding(vertical = MuhabbetSpacing.Small)
            .then(item.testTag?.let { Modifier.testTag(it) } ?: Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NavItemIcon(item = item, tint = contentColor, lift = { lift })
        Spacer(Modifier.height(MuhabbetSizes.GapHairline))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // The icon already carries the description, and `selectable` merges this whole column
            // into one node — leaving both means TalkBack says "Chats, Chats, tab, selected". The
            // description defaults to the label, so what is announced still matches what is on
            // screen; a badge count is *not* cleared, because that genuinely is extra information.
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

@Composable
private fun NavItemIcon(
    item: MuhabbetNavItem,
    tint: Color,
    lift: () -> Float
) {
    val liftPx = with(LocalDensity.current) { MuhabbetSizes.NavItemLift.toPx() }
    Box(
        modifier = Modifier.graphicsLayer { translationY = -lift() * liftPx },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.contentDescription ?: item.label,
            tint = tint,
            modifier = Modifier.size(MuhabbetSizes.IconLarge)
        )
        if (item.badgeCount > 0) {
            Badge(
                containerColor = LocalSemanticColors.current.unreadBadge.container,
                contentColor = LocalSemanticColors.current.unreadBadge.content,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = MuhabbetSizes.NavBadgeOffsetX, y = -MuhabbetSizes.NavBadgeOffsetY)
            ) {
                Text(item.badgeCount.toString())
            }
        }
    }
}
