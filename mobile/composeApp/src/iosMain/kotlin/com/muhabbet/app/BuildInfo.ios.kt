package com.muhabbet.app

import kotlin.experimental.ExperimentalNativeApi

/**
 * Kotlin/Native knows whether it was compiled as a debug binary, which is the closest equivalent to
 * Android's BuildConfig.DEBUG and errs on the safe side: a release framework reports false.
 */
@OptIn(ExperimentalNativeApi::class)
internal actual fun isDebugBuild(): Boolean = Platform.isDebugBinary
