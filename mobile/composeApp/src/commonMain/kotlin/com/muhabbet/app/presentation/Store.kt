package com.muhabbet.app.presentation

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.CoroutineContext

/**
 * The presentation state holder every stateful destination owns (ADR 0010).
 *
 * A Store is retained by its component's `InstanceKeeper` (see [retainedStore]), so it survives a
 * configuration change — rotation, theme, the language switch — and dies exactly when its navigation
 * entry does. That is the property `remember { mutableStateOf(...) }` could never have: composition
 * state dies with the composition, which on this app means every rotation and every push of a
 * screen on top.
 *
 * The contract is deliberately small, and is the same for every screen:
 *  - [state] — one immutable value, the only thing the screen renders;
 *  - [effects] — one-shot events the screen performs and forgets (a snackbar, a platform prompt);
 *  - [onEvent] — the only way the screen talks back.
 *
 * Stores never see Compose. They take their dependencies through the constructor and are tested on
 * the JVM with fakes, which is the second half of why they exist: behaviour that lived inside a
 * composable could not be tested at all.
 *
 * [context] defaults to `Dispatchers.Main.immediate`, so state updates land on the UI thread without
 * an extra frame of latency. Tests pass a test dispatcher instead.
 */
abstract class Store<State : Any, Event : Any, Effect : Any>(
    initialState: State,
    context: CoroutineContext = Dispatchers.Main.immediate
) : InstanceKeeper.Instance {

    /**
     * Work owned by this screen rather than by its composition: a save the user started keeps going
     * if they rotate the phone, and is cancelled when they leave the screen for good.
     */
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + context)

    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<State> = mutableState.asStateFlow()

    /**
     * Buffered so an effect produced while no screen is collecting — mid-rotation, between the old
     * composition leaving and the new one arriving — is delivered when it comes back rather than
     * dropped. One collector at a time: an effect is performed once, not once per observer.
     */
    private val effectChannel = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = effectChannel.receiveAsFlow()

    abstract fun onEvent(event: Event)

    protected val currentState: State get() = mutableState.value

    protected fun updateState(transform: (State) -> State) {
        mutableState.update(transform)
    }

    protected fun emit(effect: Effect) {
        effectChannel.trySend(effect)
    }

    override fun onDestroy() {
        scope.cancel()
        effectChannel.close()
    }
}
