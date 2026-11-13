package com.muhabbet.app.session

import com.muhabbet.app.data.local.TokenStorage

/** Counts wipes instead of touching a database. */
class RecordingAccountData : AccountDataStore {
    var wipes = 0
        private set

    override fun wipe() {
        wipes++
    }
}

/** Records which account scope is open, the way `KoinAccountScope` would hold it. */
class RecordingAccountScope : AccountScope {
    var openFor: String? = null
        private set
    val history = mutableListOf<String>()

    override fun open(userId: String) {
        openFor = userId
        history += "open:$userId"
    }

    override fun close() {
        if (openFor != null) history += "close:$openFor"
        openFor = null
    }
}

/**
 * A [SessionManager] over [tokenStorage] with recording collaborators — for tests that need a real
 * session behind a repository or a client. Built from the storage as it is *now*, exactly as Koin
 * builds the real one at first injection.
 */
fun testSessionManager(
    tokenStorage: TokenStorage,
    accountData: AccountDataStore = RecordingAccountData(),
    accountScope: AccountScope = RecordingAccountScope()
): SessionManager = SessionManager(tokenStorage, accountData, accountScope)
