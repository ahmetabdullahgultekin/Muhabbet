package com.muhabbet.designsystem.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSString

@OptIn(ExperimentalForeignApi::class)
@Suppress("CAST_NEVER_SUCCEEDS") // A Kotlin String is bridged to NSString by Kotlin/Native.
internal actual fun nextGraphemeBoundary(text: String, start: Int): Int {
    val range = (text as NSString).rangeOfComposedCharacterSequenceAtIndex(start.toULong())
    return range.useContents { (location + length).toInt() }
}
