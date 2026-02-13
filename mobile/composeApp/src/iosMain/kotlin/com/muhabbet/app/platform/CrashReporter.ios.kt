package com.muhabbet.app.platform

import platform.Foundation.NSException
import platform.Foundation.NSLog
import platform.Foundation.NSUserDefaults

actual object CrashReporter {
    private var isInitialized = false

    actual fun init() {
        if (isInitialized) return
        isInitialized = true
        // Set up NSException handler for uncaught exceptions
        NSLog("CrashReporter: initialized (native Sentry SDK — add via CocoaPods for production)")
        // When Sentry CocoaPod is integrated, replace with:
        // SentrySDK.startWithConfigureOptions { options ->
        //     options.dsn = BuildConfig.SENTRY_DSN
        //     options.tracesSampleRate = NSNumber(double = 0.2)
        // }
    }

    actual fun setUser(userId: String) {
        NSUserDefaults.standardUserDefaults.setObject(userId, forKey = "sentry_user_id")
        NSLog("CrashReporter: setUser($userId)")
        // When Sentry is integrated:
        // SentrySDK.setUser(SentryUser().apply { this.userId = userId })
    }

    actual fun captureException(throwable: Throwable) {
        NSLog("CrashReporter: exception — ${throwable.message}")
        NSLog("CrashReporter: stackTrace — ${throwable.stackTraceToString()}")
        // When Sentry is integrated:
        // SentrySDK.captureException(throwable.asNSException())
    }

    actual fun addBreadcrumb(category: String, message: String) {
        NSLog("CrashReporter: breadcrumb [$category] $message")
        // When Sentry is integrated:
        // SentrySDK.addBreadcrumb(SentryBreadcrumb().apply {
        //     this.category = category; this.message = message
        // })
    }
}
