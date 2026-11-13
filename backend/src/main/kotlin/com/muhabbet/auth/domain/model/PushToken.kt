package com.muhabbet.auth.domain.model

/**
 * Longest push token accepted (#908, #159).
 *
 * `devices.push_token` carries a unique B-tree index (V29), and a B-tree entry over ~2.7 kB is
 * rejected by Postgres, so an unbounded value would turn a registration into a 500 from the index
 * rather than a rejected field. FCM registration tokens are ~160 characters and APNs tokens 64, so
 * 1 024 leaves the provider room to grow without leaving room for abuse.
 */
const val MAX_PUSH_TOKEN_LENGTH = 1024

private val PUSH_TOKEN_SHAPE = Regex("[\\x21-\\x7E]+")

/**
 * The token to store, or null when [raw] is not one.
 *
 * Both providers issue printable ASCII with no whitespace, so that is the whole rule. It is
 * deliberately not FCM's current alphabet: the format is not documented as stable, and a rule that
 * rejects a token the provider really issued would silently stop that phone's notifications.
 *
 * Surrounding whitespace is trimmed rather than rejected — it is a transport artefact, and storing
 * it would make the same token two different values to the unique index.
 */
fun normalizePushToken(raw: String): String? =
    raw.trim().takeIf { it.length <= MAX_PUSH_TOKEN_LENGTH && PUSH_TOKEN_SHAPE.matches(it) }
