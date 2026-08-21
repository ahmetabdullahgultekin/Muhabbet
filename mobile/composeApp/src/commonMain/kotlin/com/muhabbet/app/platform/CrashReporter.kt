package com.muhabbet.app.platform

/**
 * Cross-platform crash reporting interface.
 * Android: Sentry SDK, iOS: stubbed (implement when deploying to App Store).
 */
expect object CrashReporter {
    fun init()
    fun setUser(userId: String)

    /**
     * Forget whoever [setUser] last named.
     *
     * The counterpart [setUser] never had. Sentry's user is global mutable state that outlives the
     * session — and on iOS it is written to `NSUserDefaults`, so it outlives the process too — which
     * meant every crash report sent after a logout still carried the id of the account that logged
     * out. Called from `SessionWiring.onSessionEnded()`; see that class for why the pair lives
     * there rather than in a Compose effect.
     */
    fun clearUser()
    fun captureException(throwable: Throwable)
    fun addBreadcrumb(category: String, message: String)
}
