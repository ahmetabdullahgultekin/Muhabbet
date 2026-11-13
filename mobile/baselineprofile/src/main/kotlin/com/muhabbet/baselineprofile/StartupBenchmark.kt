package com.muhabbet.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The app under test: `:mobile:composeApp`'s applicationId. */
internal const val TARGET_PACKAGE = "com.muhabbet.app"

/**
 * Cold-start time to initial display, with and without the baseline profile (#867). The pair is
 * the before/after the issue asks for; run both on the same device. DEVICE-PENDING.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartWithoutProfile() = coldStart(CompilationMode.None())

    @Test
    fun coldStartWithBaselineProfile() =
        coldStart(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun coldStart(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
    ) {
        pressHome()
        startActivityAndWait()
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
