package com.muhabbet.app.util

/**
 * Simple KMP-compatible logger. Wraps println with tag prefix.
 * In release builds, R8 can strip calls via ProGuard rules if needed.
 */
object Log {
    var enabled = true

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
