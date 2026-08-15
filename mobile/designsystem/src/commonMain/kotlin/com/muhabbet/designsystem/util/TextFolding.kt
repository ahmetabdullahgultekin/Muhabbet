package com.muhabbet.designsystem.util

/**
 * Folds text into a form two strings can be compared in, for search.
 *
 * Kotlin's no-arg [String.lowercase] is locale-*independent* — it always applies root-locale rules,
 * which is the problem rather than the fix. `"İsmail".lowercase()` is `"i̇smail"`: a plain `i`
 * followed by U+0307 COMBINING DOT ABOVE. No user types that, so searching a Turkish contact list
 * for `ismail` matched nothing at all. That is a real, live bug in name search, not a theoretical
 * one, and it hits the most common initial letter in Turkish given names.
 *
 * **Diacritics are folded away entirely**: `ğ ş ç ö ü ı â î û` become `g s c o u i a i u`, and the
 * four i-shaped characters `I ı İ i` all become `i`. This is deliberate and is specific to *search*.
 * Turkish is routinely typed without diacritics — on a non-Turkish keyboard layout, in a hurry, or
 * because the diacritic is genuinely hard to reach — so a contact list that only matches `şarkı`
 * when you type `şarkı` is a contact list you cannot search. The same argument applies with more
 * force to the dotted/dotless `i`: nobody should have to know which of the four a name was stored
 * with.
 *
 * The cost is a wider net — `Öz` matches a query of `oz` — which for finding a person among a few
 * hundred contacts is a feature, not a false positive.
 *
 * This is therefore **not a lowercase implementation and must never be used for display.** For a
 * displayed initial see `firstGrapheme`, which preserves the `i`/`İ` distinction precisely because
 * there it is the whole point.
 */
fun foldForSearch(text: String): String = buildString(text.length) {
    for (ch in text) {
        when (ch) {
            'I', 'ı', 'İ', 'i', 'Î', 'î' -> append('i')
            'Ğ', 'ğ' -> append('g')
            'Ş', 'ş' -> append('s')
            'Ç', 'ç' -> append('c')
            'Ö', 'ö' -> append('o')
            'Ü', 'ü', 'Û', 'û' -> append('u')
            'Â', 'â' -> append('a')
            // Dropped rather than lowered: it is the artefact root-locale lowering leaves behind on
            // a dotted capital I, and keeping it would defeat the fold above.
            CombiningDotAbove -> Unit
            else -> append(ch.lowercaseChar())
        }
    }
}

private const val CombiningDotAbove = '̇'
