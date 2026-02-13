package com.muhabbet.app.platform

/**
 * Cross-platform crash reporting interface.
 * Android: Sentry SDK, iOS: stubbed (implement when deploying to App Store).
 */
expect object CrashReporter {
    fun init()
    fun setUser(userId: String)
    fun captureException(throwable: Throwable)
    fun addBreadcrumb(category: String, message: String)
}
