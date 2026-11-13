package com.muhabbet.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the build-type sink policy of [Log] (#354): a release build writes nothing to stdout, and
 * what it forwards to the crash reporter carries no ids, numbers, tokens or query strings.
 */
class LogPolicyTest {

    private class Recorder {
        val stdout = mutableListOf<String>()
        val crumbs = mutableListOf<Pair<String, String>>()

        fun sinks(debug: Boolean) = Log.sinksFor(
            debugBuild = debug,
            breadcrumb = { category, message -> crumbs += category to message },
            stdout = { stdout += it },
        )
    }

    private fun List<LogSink>.log(level: LogLevel, message: String, t: Throwable? = null) =
        forEach { it.write(level, "Tag", message, t) }

    @Test
    fun should_write_nothing_to_stdout_when_the_build_is_release() {
        val r = Recorder()
        val sinks = r.sinks(debug = false)

        sinks.log(LogLevel.DEBUG, "GET /api/v1/search/messages?q=secret")
        sinks.log(LogLevel.WARN, "slow")
        sinks.log(LogLevel.ERROR, "failed", IllegalStateException("boom"))

        assertTrue(r.stdout.isEmpty(), "release stdout must stay empty, got ${r.stdout}")
    }

    @Test
    fun should_drop_debug_lines_and_keep_warn_and_error_as_breadcrumbs_when_the_build_is_release() {
        val r = Recorder()
        val sinks = r.sinks(debug = false)

        sinks.log(LogLevel.DEBUG, "GET /api/v1/conversations")
        sinks.log(LogLevel.WARN, "socket closed")
        sinks.log(LogLevel.ERROR, "send failed", IllegalStateException("contains +905000000001"))

        assertEquals(
            listOf("Tag" to "WARN socket closed", "Tag" to "ERROR send failed [IllegalStateException]"),
            r.crumbs
        )
    }

    @Test
    fun should_redact_ids_numbers_tokens_and_queries_when_forwarding_a_breadcrumb() {
        val r = Recorder()
        val sinks = r.sinks(debug = false)

        sinks.log(
            LogLevel.ERROR,
            "Failed to block 3f2b8c1e-9a4d-4e5f-8b6a-1c2d3e4f5a6b for +90 500 000 00 01 at " +
                "/api/v1/search/messages?q=meet%20me%20at%205 with Bearer abc.def eyJhbGciOi.eyJzdWIi.sig"
        )

        val message = r.crumbs.single().second
        assertFalse("3f2b8c1e" in message, message)
        assertFalse("500 000" in message, message)
        assertFalse("meet" in message || "at%205" in message, message)
        assertFalse("eyJ" in message, message)
        assertFalse("abc.def" in message, message)
        assertTrue("<id>" in message && "<number>" in message && "?<redacted>" in message, message)
    }

    @Test
    fun should_write_every_level_verbatim_to_stdout_and_nothing_to_the_crash_reporter_when_the_build_is_debug() {
        val r = Recorder()
        val sinks = r.sinks(debug = true)
        val id = "3f2b8c1e-9a4d-4e5f-8b6a-1c2d3e4f5a6b"

        sinks.log(LogLevel.DEBUG, "GET /api/v1/users/$id")
        sinks.log(LogLevel.WARN, "w")
        sinks.log(LogLevel.ERROR, "e")

        assertEquals(listOf("Tag: GET /api/v1/users/$id", "WARN Tag: w", "ERROR Tag: e"), r.stdout)
        assertTrue(r.crumbs.isEmpty())
    }
}
