package com.muhabbet.app.util

import com.muhabbet.app.BuildInfo
import com.muhabbet.app.platform.CrashReporter

/** Severity of a [Log] line. Ordered: a sink that accepts WARN also accepts ERROR. */
enum class LogLevel { DEBUG, WARN, ERROR }

/** Where a [Log] line goes. A sink applies its own level floor and redaction. */
fun interface LogSink {
    fun write(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}

/**
 * The app's logging facade, with a sink policy chosen by build type (#354).
 *
 * It used to be `println` behind a `var enabled = true` that nothing ever set to false, so every
 * release build wrote every request path — conversation ids, user ids, the raw text of a message
 * search — to logcat, where `adb`, an OEM log collector or any app holding READ_LOGS could read
 * who the user blocked, reported, or added to a group. And it went nowhere useful: production
 * crashes like #633 left no trail, because nothing reached the crash reporter.
 *
 * The policy now:
 * - **debug build** — everything to stdout (Logcat / Xcode console), verbatim. That is what the
 *   developer at the cable needs.
 * - **release build** — **nothing to stdout**, at any level. WARN and ERROR become crash-reporter
 *   breadcrumbs, so the lines leading up to a crash travel with it — after [LogRedactor] has
 *   removed ids, phone numbers, tokens and query strings, and with an exception reduced to its type
 *   (an exception *message* is free text and may quote any of those). DEBUG is dropped outright.
 *
 * The call sites did not change and should not: `Log.d/w/e(TAG, ...)` stays the API. What a line
 * may contain is decided here, once, rather than at 176 call sites.
 */
object Log {
    internal var sinks: List<LogSink> = sinksFor(debugBuild = BuildInfo.DEBUG)

    fun d(tag: String, message: String) = dispatch(LogLevel.DEBUG, tag, message, null)

    fun w(tag: String, message: String) = dispatch(LogLevel.WARN, tag, message, null)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        dispatch(LogLevel.ERROR, tag, message, throwable)

    private fun dispatch(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        sinks.forEach { it.write(level, tag, message, throwable) }
    }

    /** The policy itself, separate from the platform so it can be tested on the JVM. */
    internal fun sinksFor(
        debugBuild: Boolean,
        breadcrumb: (category: String, message: String) -> Unit = CrashReporter::addBreadcrumb,
        stdout: (String) -> Unit = ::println,
    ): List<LogSink> =
        if (debugBuild) listOf(StdoutSink(stdout)) else listOf(BreadcrumbSink(breadcrumb))
}

/** Debug only: the full line, as before. */
internal class StdoutSink(private val out: (String) -> Unit) : LogSink {
    override fun write(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val prefix = when (level) {
            LogLevel.DEBUG -> ""
            LogLevel.WARN -> "WARN "
            LogLevel.ERROR -> "ERROR "
        }
        out("$prefix$tag: $message")
        throwable?.let { out("  ${it.stackTraceToString().take(MAX_STACK_CHARS)}") }
    }

    private companion object {
        const val MAX_STACK_CHARS = 500
    }
}

/** Release only: WARN and above, redacted, as breadcrumbs. Never stdout. */
internal class BreadcrumbSink(
    private val breadcrumb: (category: String, message: String) -> Unit
) : LogSink {
    override fun write(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (level < LogLevel.WARN) return
        val cause = throwable?.let { " [${it::class.simpleName ?: "Throwable"}]" }.orEmpty()
        breadcrumb(tag, "${level.name} ${LogRedactor.scrub(message)}$cause")
    }
}

/**
 * Removes what must not leave the device in a log line: entity ids (UUIDs), phone numbers and other
 * long digit runs, JWTs and bearer tokens, and everything after a `?` in a path — which is where the
 * search query and paging cursors sit. Deliberately over-eager: a breadcrumb that says `<id>` loses
 * nothing a crash investigation needs, and one that says the real id is a KVKK problem.
 */
internal object LogRedactor {
    private val uuid = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
    private val jwt = Regex("""eyJ[\w-]+\.[\w-]+\.[\w-]*""")
    private val bearer = Regex("""(?i)bearer\s+\S+""")
    private val query = Regex("""\?\S*""")
    private val digits = Regex("""\+?\d[\d\s-]{6,}\d""")

    fun scrub(message: String): String = message
        .replace(jwt, "<token>")
        .replace(bearer, "Bearer <token>")
        .replace(uuid, "<id>")
        .replace(query, "?<redacted>")
        .replace(digits, "<number>")
}
