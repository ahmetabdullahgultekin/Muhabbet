package com.muhabbet.app.di

import com.muhabbet.app.session.AccountScope
import org.koin.core.Koin
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope

/**
 * Qualifier of the per-account Koin scope (#829). Definitions declared under
 * `scope(ACCOUNT_SCOPE) { scoped { ... } }` live exactly as long as one account's session.
 */
val ACCOUNT_SCOPE = named("account")

/**
 * [AccountScope] on Koin: one scope instance per signed-in account.
 *
 * Every holder of per-account state used to be a process-wide `single`, so logging out and signing
 * in as someone else handed the second account the first one's instances — its privacy settings,
 * among others (#829). Closing the scope drops those instances; the next account gets new ones.
 *
 * Callers do not see the scope. Each scoped type is also exposed at the root as a `factory` that
 * delegates to [current], so `koinInject<PrivacySettingsController>()` keeps working unchanged and
 * simply resolves the instance that belongs to whoever is signed in now.
 */
class KoinAccountScope(private val koin: Koin) : AccountScope {

    private var scope: Scope? = null

    override fun open(userId: String) {
        val existing = scope
        if (existing != null && !existing.closed && existing.id == scopeId(userId)) return
        close()
        scope = koin.createScope(scopeId(userId), ACCOUNT_SCOPE)
    }

    override fun close() {
        scope?.close()
        scope = null
    }

    /**
     * The open account scope.
     *
     * Throws when none is open: per-account state requested with nobody signed in is a wiring
     * defect, and handing out a fresh orphan instance would hide it until it leaked into the next
     * account — the exact failure this scope exists to prevent.
     */
    fun current(): Scope = scope?.takeUnless { it.closed }
        ?: throw IllegalStateException("NO_ACCOUNT_SCOPE")

    private fun scopeId(userId: String) = "account:$userId"
}
