package com.muhabbet.app

object BuildInfo {
    const val VERSION = "1.0.0"
    const val VERSION_CODE = 1

    /**
     * Debug build flag. When false (release / production), verbose diagnostics — including HTTP
     * logging — must be disabled. Flip to false for production builds (or wire to a platform
     * BuildConfig.DEBUG in a follow-up). Kept conservative: request headers are NEVER logged in
     * production, because the Authorization bearer token rides in them.
     */
    const val DEBUG = true
}
