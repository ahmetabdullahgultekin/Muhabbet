package com.muhabbet.app.util

/**
 * Human-readable byte sizes.
 *
 * Moved out of `SettingsSections` when the storage detail screen (#546) became the second reader.
 * Binary steps (1024) with the labels every desktop file manager uses, which is the inconsistency
 * the whole industry ships and is not worth being uniquely correct about here.
 *
 * Locale-independent on purpose: [formatDecimal] builds the string digit by digit rather than
 * calling a platform formatter, so it behaves identically on both targets and under test. The
 * separator is therefore always ".", including in Turkish, where "," is conventional — a known
 * property of the existing storage card, carried across unchanged rather than quietly altered
 * while moving the code.
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${formatDecimal(kb, 1)} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${formatDecimal(mb, 1)} MB"
    val gb = mb / 1024.0
    return "${formatDecimal(gb, 2)} GB"
}

private fun formatDecimal(value: Double, places: Int): String {
    var factor = 1L
    repeat(places) { factor *= 10 }
    val rounded = ((value * factor) + 0.5).toLong()
    val intPart = rounded / factor
    val fracPart = (rounded % factor).toString().padStart(places, '0')
    return "$intPart.$fracPart"
}
