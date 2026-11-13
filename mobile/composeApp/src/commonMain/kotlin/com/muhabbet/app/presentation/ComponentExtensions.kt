package com.muhabbet.app.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.statekeeper.StateKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.KSerializer
import kotlin.coroutines.CoroutineContext

/**
 * The Store of this navigation entry, created on first use and handed back unchanged after a
 * configuration change (ADR 0010). Destroyed — and its scope cancelled — when the entry leaves the
 * stack.
 */
inline fun <reified T : Store<*, *, *>> ComponentContext.retainedStore(noinline factory: () -> T): T =
    instanceKeeper.getOrCreate(factory)

/**
 * A scope for this component *instance*: cancelled when its lifecycle is destroyed, which includes a
 * configuration change. For collecting a retained Store's effects that the component itself acts
 * on — navigation, chiefly — because a navigation callback captured inside a retained Store would
 * outlive the stack it belongs to.
 */
fun ComponentContext.componentScope(
    context: CoroutineContext = Dispatchers.Main.immediate
): CoroutineScope {
    val scope = CoroutineScope(SupervisorJob() + context)
    lifecycle.doOnDestroy { scope.cancel() }
    return scope
}

/**
 * Restores a value saved before process death and registers [supplier] to save it again.
 *
 * `InstanceKeeper` covers a configuration change; it does not cover the process being killed in the
 * background, which on Android is routine. Anything the user would be annoyed to lose — a draft, a
 * half-confirmed action — goes through here as well. Keep it small: the saved state of every screen
 * on the stack shares one Bundle.
 *
 * @return the restored value, or null on a fresh start.
 */
fun <T : Any> StateKeeper.saved(key: String, serializer: KSerializer<T>, supplier: () -> T?): T? {
    val restored = consume(key, serializer)
    register(key, serializer, supplier)
    return restored
}
