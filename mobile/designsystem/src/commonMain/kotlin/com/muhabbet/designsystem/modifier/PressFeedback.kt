package com.muhabbet.designsystem.modifier

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import com.muhabbet.designsystem.theme.MuhabbetMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How far a pressed element squeezes. Three named strengths instead of six private constants.
 *
 * Before this there were six copies of the same press block inside the design system — button
 * 0.97, icon button 0.88, FAB 0.94, dialog button 0.95, chip 0.95, settings row 0.98 — each with its
 * own constant and each a tweak that had to be made six times (#846). The differences that were
 * deliberate collapse onto these three; the ones that were accidents (0.94 vs 0.95 vs 0.97) do not
 * survive.
 *
 * The rule for choosing: the smaller the target, the deeper the squeeze. The same proportional
 * scale on a full-width row travels many times further in pixels than on a 48dp icon, and reads as
 * the row collapsing rather than being pressed.
 */
enum class MuhabbetPress(internal val pressedScale: Float) {
    /** Full-width rows and anything as wide as the screen. Also the app-wide default indication. */
    Subtle(0.98f),

    /** Buttons, chips, FABs, dialog buttons — the ordinary control size. */
    Standard(0.95f),

    /** Small, icon-only targets, where a shallow squeeze would be invisible. */
    Strong(0.88f),
}

/**
 * The app's press feedback as an [androidx.compose.foundation.Indication]: the platform ripple, plus
 * a [MuhabbetPress] squeeze driven by a [MuhabbetMotion] spring.
 *
 * [com.muhabbet.designsystem.theme.MuhabbetTheme] provides `MuhabbetPressIndication()` as
 * `LocalIndication`, so every `Modifier.clickable`, `selectable` and `toggleable` in the app — the
 * eighty raw ones in screens and [pressable]/[longPressable] alike — gets the same feel without
 * asking for it. Pass a stronger one explicitly only for an element the default does not suit:
 * `Modifier.clickable(interactionSource = null, indication = MuhabbetPressIndication(MuhabbetPress.Standard))`.
 *
 * It is an [IndicationNodeFactory], not a composable that remembers state: the squeeze is animated
 * inside a `Modifier.Node` and read in the draw phase, so a press invalidates one draw and never
 * recomposes the element it is attached to.
 */
fun MuhabbetPressIndication(strength: MuhabbetPress = MuhabbetPress.Subtle): IndicationNodeFactory =
    when (strength) {
        MuhabbetPress.Subtle -> SubtlePressIndication
        MuhabbetPress.Standard -> StandardPressIndication
        MuhabbetPress.Strong -> StrongPressIndication
    }

private val SubtlePressIndication = PressIndicationFactory(MuhabbetPress.Subtle)
private val StandardPressIndication = PressIndicationFactory(MuhabbetPress.Standard)
private val StrongPressIndication = PressIndicationFactory(MuhabbetPress.Strong)

private class PressIndicationFactory(private val strength: MuhabbetPress) : IndicationNodeFactory {
    // ripple() hands back a cached singleton factory, so creating it per node costs nothing.
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressIndicationNode(interactionSource, strength, ripple())

    override fun equals(other: Any?): Boolean =
        other is PressIndicationFactory && other.strength == strength

    override fun hashCode(): Int = strength.hashCode()
}

/**
 * Two delegates, not one node that is both: a [DelegatingNode] that is itself a [DrawModifierNode]
 * has its draw delegates skipped by the node chain, which would silently drop the ripple.
 */
private class PressIndicationNode(
    interactionSource: InteractionSource,
    strength: MuhabbetPress,
    ripple: IndicationNodeFactory
) : DelegatingNode() {
    init {
        delegate(PressScaleNode(interactionSource, strength))
        delegate(ripple.create(interactionSource))
    }
}

/**
 * The squeeze alone, for design-system components that wrap a Material control whose own indication
 * cannot be replaced (`Button`, `IconButton`, `FilterChip` hard-wire `ripple()`). Hand the same
 * interaction source to the control and to this modifier.
 *
 * Internal on purpose: a screen reaches press feedback through `LocalIndication`, never by pairing a
 * modifier with an interaction source by hand.
 */
internal fun Modifier.pressScale(
    interactionSource: InteractionSource,
    strength: MuhabbetPress
): Modifier = this then PressScaleElement(interactionSource, strength)

private data class PressScaleElement(
    val interactionSource: InteractionSource,
    val strength: MuhabbetPress
) : ModifierNodeElement<PressScaleNode>() {
    override fun create(): PressScaleNode = PressScaleNode(interactionSource, strength)

    override fun update(node: PressScaleNode) = node.update(interactionSource, strength)

    override fun InspectorInfo.inspectableProperties() {
        name = "pressScale"
        properties["strength"] = strength
    }
}

/**
 * The one press implementation.
 *
 * Collects press interactions and springs a scale between 1 and [MuhabbetPress.pressedScale]. The
 * scale is read only in [draw], so the node invalidates its own draw and nothing else: the copies
 * this replaced read `collectIsPressedAsState()` through a `by` delegate during composition, which
 * recomposed the whole component on every press and release.
 *
 * Several overlapping presses (two fingers) are tracked so that releasing one does not spring the
 * element back while the other is still down.
 */
internal class PressScaleNode(
    private var interactionSource: InteractionSource,
    private var strength: MuhabbetPress
) : Modifier.Node(), DrawModifierNode {

    private val scale = Animatable(1f)
    private var collection: Job? = null

    override fun onAttach() = collect()

    override fun onDetach() {
        collection = null
    }

    fun update(source: InteractionSource, newStrength: MuhabbetPress) {
        strength = newStrength
        if (source != interactionSource) {
            interactionSource = source
            if (isAttached) collect()
        }
    }

    private fun collect() {
        collection?.cancel()
        collection = coroutineScope.launch {
            val active = mutableListOf<PressInteraction.Press>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> active += interaction
                    is PressInteraction.Release -> active -= interaction.press
                    is PressInteraction.Cancel -> active -= interaction.press
                    else -> return@collect
                }
                val target = if (active.isEmpty()) 1f else strength.pressedScale
                // Animatable's mutex cancels the previous spring, so an interrupted press settles
                // from wherever it is rather than snapping.
                launch { scale.animateTo(target, MuhabbetMotion.spatialFast()) }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val s = scale.value
        if (s == 1f) {
            drawContent()
        } else {
            scale(s) { this@draw.drawContent() }
        }
    }
}
