package com.muhabbet.designsystem.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.theme.MuhabbetTheme
import com.muhabbet.designsystem.theme.MuhabbetThemeMode

/**
 * The one matrix every design-system golden is rendered in (#759).
 *
 * Three themes × three font scales covers what has actually broken before: contrast per variant
 * (the OLED divider at 1.09:1), and layout at large text — design doc §10 names 11sp `ChatMeta`,
 * the 72dp list row and the 320dp bubble as the first things to give way at 1.3×. 2.0 is Android's
 * largest system setting since API 34. RTL is one extra variant rather than a full axis: mirroring
 * bugs are structural, not per-theme.
 *
 * **The clock is driven by hand.** Spinners, shimmer and breathing loops are infinite animations;
 * with the test clock auto-advancing, Compose never reports idle and a capture waits forever. So
 * auto-advance is off, every variant is rendered into the same composition by swapping state, and
 * the clock is moved a fixed [SETTLE_MS] before each capture — long enough for entry springs to
 * land, and identical on every run, which is what makes an animated component's golden
 * deterministic at all.
 *
 * Goldens live under `src/androidUnitTest/goldens/<component>/` and are compared by
 * `verifyRoborazziDebug`.
 */
object MuhabbetSnapshotMatrix {

    val themes = listOf(MuhabbetThemeMode.Light, MuhabbetThemeMode.Dark, MuhabbetThemeMode.Oled)
    val fontScales = listOf(1.0f, 1.3f, 2.0f)

    private const val GOLDEN_ROOT = "src/androidUnitTest/goldens"
    private const val SETTLE_MS = 1_000L

    private data class Variant(val name: String, val theme: MuhabbetThemeMode, val fontScale: Float, val direction: LayoutDirection)

    private val variants: List<Variant> = themes.flatMap { theme ->
        fontScales.map { scale -> Variant("${theme.storageKey}_x${(scale * 10).toInt()}", theme, scale, LayoutDirection.Ltr) }
    } + Variant("light_x10_rtl", MuhabbetThemeMode.Light, 1.0f, LayoutDirection.Rtl)

    /** Captures [content] in every theme × font scale, plus one RTL rendering in Light at 1.0. */
    fun capture(rule: ComposeContentTestRule, component: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        var current by mutableStateOf(variants.first())
        var composed: Variant? = null
        rule.setContent {
            val v = current
            SideEffect { composed = v }
            MuhabbetTheme(mode = v.theme, hapticsEnabled = false) {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, v.fontScale),
                    LocalLayoutDirection provides v.direction,
                ) {
                    Box(
                        Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(Muhabbet.spacing.Large)
                    ) { content() }
                }
            }
        }
        variants.forEach { v ->
            current = v
            // Recompose with the new variant *before* moving the clock. A state write from the test
            // thread reaches the recomposer only when snapshot apply notifications are sent, and
            // advancing a paused clock does not send them — so without this every frame rendered
            // the previous variant, and each golden carried its predecessor's theme and font scale
            // (light_x13 was really light_x10, dark_x10 was light_x20). waitForIdle sends them and
            // recomposes; with auto-advance off it does not move the clock, so timing is unchanged.
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(SETTLE_MS)
            // The harness checks itself: a golden filed under one variant must be a rendering of it.
            check(composed == v) { "capturing ${v.name} but the composition last applied ${composed?.name}" }
            rule.onRoot().captureRoboImage("$GOLDEN_ROOT/$component/${component}_${v.name}.png")
        }
    }
}
