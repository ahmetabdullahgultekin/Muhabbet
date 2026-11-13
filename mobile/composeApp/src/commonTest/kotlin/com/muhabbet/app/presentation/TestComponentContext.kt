package com.muhabbet.app.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher

/**
 * A real Decompose component context with every Essenty piece exposed, so a test can do what the
 * platform does: dispatch back, save state, destroy, and hand the retained instances to a new
 * context — which is exactly what a configuration change is.
 */
class TestComponentContext(
    savedState: SerializableContainer? = null,
    val instanceKeeper: InstanceKeeperDispatcher = InstanceKeeperDispatcher()
) {
    val lifecycle = LifecycleRegistry()
    val stateKeeper = StateKeeperDispatcher(savedState)
    val backDispatcher = BackDispatcher()
    val context = DefaultComponentContext(
        lifecycle = lifecycle,
        stateKeeper = stateKeeper,
        instanceKeeper = instanceKeeper,
        backHandler = backDispatcher
    )

    init {
        lifecycle.resume()
    }
}
