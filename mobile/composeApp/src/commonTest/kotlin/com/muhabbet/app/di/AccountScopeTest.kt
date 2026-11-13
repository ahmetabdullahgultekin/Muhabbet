package com.muhabbet.app.di

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.local.PrivacySettingsController
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.session.SessionManager
import com.muhabbet.app.session.testSessionManager
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Per-account holders really are per account (#829), through the app's own module definitions — so
 * moving one back to a process-wide `single` fails here.
 */
class AccountScopeTest {

    private val storage = FakeTokenStorage()

    private val app = koinApplication {
        modules(
            appModule(),
            // The two platform pieces the session needs, replaced with fakes. Everything else is the
            // production definition.
            module {
                single<TokenStorage> { storage }
                single<SessionManager> { testSessionManager(get(), accountScope = get<KoinAccountScope>()) }
            }
        )
    }
    private val koin = app.koin

    @AfterTest
    fun tearDown() = app.close()

    @Test
    fun should_give_the_next_account_a_fresh_privacy_holder_after_logout() {
        storage.saveTokens("t", "r", "a", "d")
        val session = koin.get<SessionManager>()
        val forA = koin.get<PrivacySettingsController>()
        assertSame(forA, koin.get<PrivacySettingsController>(), "one instance per account, shared by both screens")

        session.logout()
        session.signIn("t2", "r2", "b", "d2", isNewUser = false)
        val forB = koin.get<PrivacySettingsController>()

        assertNotSame(forA, forB, "account B must not be handed account A's privacy settings")
    }

    @Test
    fun should_refuse_per_account_state_while_nobody_is_signed_in() {
        koin.get<SessionManager>()

        val failure = assertFailsWith<Exception> { koin.get<PrivacySettingsController>() }
        // Koin wraps what the definition threw; the cause is the scope's own refusal.
        val chain = generateSequence<Throwable>(failure) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(chain.any { "NO_ACCOUNT_SCOPE" in it }, "unexpected failure: $chain")
    }

    @Test
    fun should_drop_the_holder_when_the_session_expires() {
        storage.saveTokens("t", "r", "a", "d")
        val session = koin.get<SessionManager>()
        val before = koin.get<PrivacySettingsController>()

        session.onRefreshRejected()
        session.signIn("t2", "r2", "a", "d", isNewUser = false)

        assertNotSame(before, koin.get<PrivacySettingsController>())
    }
}
