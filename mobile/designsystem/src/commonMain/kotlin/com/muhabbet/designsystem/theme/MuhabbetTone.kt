package com.muhabbet.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * What a small, non-interactive label is saying — "Admin", "Bot", "Linked", "Failed" — which
 * decides its colour pair.
 *
 * A role rather than a colour for the same reason [MuhabbetButtonRole][com.muhabbet.designsystem.components.MuhabbetButtonRole]
 * is: a screen naming `errorContainer` for its background has to remember to name
 * `onErrorContainer` for its text, and #517 is what happens the time it does not. A tone hands
 * back both halves at once, and every tone in every theme is measured by
 * `SemanticColorContrastTest`.
 */
enum class MuhabbetTone {
    /** No opinion: a count, a type label. The quietest container the theme has. */
    Neutral,

    /** The brand's own voice: "Admin", "Owner", "New". */
    Brand,

    /** Informational, cool on purpose so it never reads as the brand: "Bot", "Beta". */
    Info,

    /** Connected or confirmed — "Linked", "Verified". Green, so used sparingly. */
    Success,

    /** Something is wrong or irreversible: "Failed", "Blocked". */
    Danger,
}

/**
 * The container/content pair for this tone, in the theme actually rendering.
 */
@Composable
@ReadOnlyComposable
fun MuhabbetTone.colors(): MuhabbetColorPair =
    toneColors(this, MaterialTheme.colorScheme, LocalThemeMode.current != ResolvedThemeMode.Light)

/**
 * The pure mapping behind [colors], so the contrast test can measure every tone against every
 * scheme without composing anything.
 *
 * Four of the five tones are M3 container roles the schemes already declare and the contrast test
 * already covers; [MuhabbetTone.Success] is the one M3 has no role for, so it reads the palette's
 * own success containers and is measured in its own right.
 */
internal fun toneColors(tone: MuhabbetTone, scheme: ColorScheme, dark: Boolean): MuhabbetColorPair = when (tone) {
    MuhabbetTone.Neutral -> MuhabbetColorPair(scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
    MuhabbetTone.Brand -> MuhabbetColorPair(scheme.primaryContainer, scheme.onPrimaryContainer)
    MuhabbetTone.Info -> MuhabbetColorPair(scheme.tertiaryContainer, scheme.onTertiaryContainer)
    MuhabbetTone.Danger -> MuhabbetColorPair(scheme.errorContainer, scheme.onErrorContainer)
    MuhabbetTone.Success -> if (dark) {
        MuhabbetColorPair(MuhabbetPalette.SuccessContainerDark, MuhabbetPalette.OnSuccessContainerDark)
    } else {
        MuhabbetColorPair(MuhabbetPalette.SuccessContainerLight, MuhabbetPalette.OnSuccessContainerLight)
    }
}
