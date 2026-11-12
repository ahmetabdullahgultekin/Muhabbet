package com.muhabbet.app.util

import com.muhabbet.app.BuildInfo

/**
 * Simple KMP-compatible logger. Wraps println with tag prefix.
 *
 * Off in release builds. On Android `println` goes to logcat, which is readable over `adb` and,
 * on older devices, by other apps, so a release build writes nothing through here. Calls that go
 * straight to `android.util.Log` are handled separately: `proguard-rules.pro` strips their
 * debug/verbose/info levels from minified builds.
 */
object Log {
    var enabled: Boolean = BuildInfo.DEBUG

    fun d(tag: String, message: String) {
        if (enabled) println("$tag: $message")
    }

    fun w(tag: String, message: String) {
        if (enabled) println("WARN $tag: $message")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) {
            println("ERROR $tag: $message")
            throwable?.let { println("  ${it.stackTraceToString().take(500)}") }
        }
    }
}
