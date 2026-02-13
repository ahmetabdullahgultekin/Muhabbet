package com.muhabbet.app.platform

import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.Breadcrumb
import io.sentry.protocol.User

actual object CrashReporter {
    actual fun init() {
        // Sentry auto-initializes via AndroidManifest meta-data.
        // This method is for manual init if needed.
    }

    actual fun setUser(userId: String) {
        Sentry.setUser(User().apply { id = userId })
    }

    actual fun captureException(throwable: Throwable) {
        Sentry.captureException(throwable)
    }

    actual fun addBreadcrumb(category: String, message: String) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = category
            this.message = message
            this.level = SentryLevel.INFO
        })
    }
}
