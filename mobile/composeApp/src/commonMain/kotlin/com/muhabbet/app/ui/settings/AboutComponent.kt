package com.muhabbet.app.ui.settings

import com.arkivanov.decompose.ComponentContext

/**
 * The About destination (ADR 0010): a component with no Store, because About has no state — the
 * version comes from `BuildInfo` and the legal links are constants. What it owns is its outputs, so
 * the screen no longer receives navigation lambdas from the host's `when` branch.
 */
class AboutComponent(
    componentContext: ComponentContext,
    val onBack: () -> Unit,
    val onReleaseNotes: () -> Unit
) : ComponentContext by componentContext
