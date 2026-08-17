package com.muhabbet.app

object BuildInfo {
    /**
     * Must match `versionName` / `versionCode` in `mobile/composeApp/build.gradle.kts`. The Gradle
     * task `verifyBuildInfoVersion` fails the build when they drift — they did once, with Gradle on
     * 0.1.0 while the settings screen told users 1.0.0.
     */
    const val VERSION = "0.3.5"
    const val VERSION_CODE = 10

    /**
     * True only in a debuggable build. Verbose diagnostics — including HTTP logging — hang off
     * this, and request headers carry the Authorization bearer token, so a release build must never
     * report true. This used to be a hardcoded `true`, which meant release builds logged every
     * request method, URL and status.
     */
    val DEBUG: Boolean get() = isDebugBuild()
}

/** Resolved from the platform's own build type rather than a constant someone has to remember. */
internal expect fun isDebugBuild(): Boolean
