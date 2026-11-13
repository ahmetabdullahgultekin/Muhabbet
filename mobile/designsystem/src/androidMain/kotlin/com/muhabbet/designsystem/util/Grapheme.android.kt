package com.muhabbet.designsystem.util

import java.text.BreakIterator

internal actual fun nextGraphemeBoundary(text: String, start: Int): Int {
    // A new instance per call: BreakIterator is stateful and not thread-safe, and this runs once
    // per avatar per name change, so caching one would trade correctness for nothing measurable.
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val end = iterator.following(start)
    return if (end == BreakIterator.DONE) text.length else end
}
