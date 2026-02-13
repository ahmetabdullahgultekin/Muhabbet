package com.muhabbet.app.platform

actual object CrashReporter {
    actual fun init() {
        // TODO: Initialize Sentry iOS SDK when deploying to App Store
        // SentrySDK.start { options in options.dsn = "..." }
    }

    actual fun setUser(userId: String) {
        // Stubbed for iOS — implement when Sentry iOS SDK is added
    }

    actual fun captureException(throwable: Throwable) {
        // Stubbed for iOS
        println("CrashReporter: ${throwable.message}")
    }

    actual fun addBreadcrumb(category: String, message: String) {
        // Stubbed for iOS
    }
}
