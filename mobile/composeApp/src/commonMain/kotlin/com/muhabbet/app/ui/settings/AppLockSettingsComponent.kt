package com.muhabbet.app.ui.settings

import com.arkivanov.decompose.ComponentContext
import com.muhabbet.app.presentation.retainedStore

/** The App Lock settings destination (ADR 0010). Nothing here needs to survive process death. */
class AppLockSettingsComponent(
    componentContext: ComponentContext,
    storeFactory: () -> AppLockSettingsStore,
    val onBack: () -> Unit
) : ComponentContext by componentContext {
    val store: AppLockSettingsStore = retainedStore(storeFactory)
}
