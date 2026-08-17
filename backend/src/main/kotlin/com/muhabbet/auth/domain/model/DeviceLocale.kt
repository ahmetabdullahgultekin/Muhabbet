package com.muhabbet.auth.domain.model

import java.util.Locale

/** Width of `devices.locale` (V22). A tag longer than this is not a tag. */
const val MAX_DEVICE_LOCALE_LENGTH = 16

/**
 * Cleans a client-supplied language tag into something safe to store in `devices.locale`.
 *
 * The value arrives in a request body, so it is whatever the caller typed — the column is
 * `VARCHAR(16)` and an unbounded string would turn a push-token registration into a 500 from the
 * database rather than a rejected field. Everything that is not a language tag becomes null, and
 * null is the value that already means "unknown, use the fallback", so bad input degrades to
 * today's behaviour instead of to an error.
 *
 * Deliberately **not** a whitelist of the two languages the bundles currently ship. A device that
 * says "de" is telling the truth about itself; `ResourceBundleMessageSource` already answers it
 * with the default bundle, and the day a German bundle lands, the rows are already right. Narrowing
 * here would silently discard that.
 *
 * @return the canonical tag (`"en-GB"` for `"EN-gb"`), or null when [tag] carries no language.
 */
fun normalizeDeviceLocale(tag: String?): String? {
    val trimmed = tag?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed.length > MAX_DEVICE_LOCALE_LENGTH) return null

    // forLanguageTag never throws: an unparseable tag yields ROOT, whose language is empty.
    val canonical = Locale.forLanguageTag(trimmed).toLanguageTag()
    return canonical.takeIf { it != Locale.ROOT.toLanguageTag() && it.length <= MAX_DEVICE_LOCALE_LENGTH }
}
