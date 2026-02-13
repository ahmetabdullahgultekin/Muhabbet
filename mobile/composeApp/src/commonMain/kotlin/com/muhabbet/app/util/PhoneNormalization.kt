package com.muhabbet.app.util

/**
 * Normalizes a Turkish phone number to E.164 format (+90XXXXXXXXXX).
 * Handles common input formats: 05XX, 5XX, 905XX, +905XX, with/without spaces/dashes.
 * Returns null if the number cannot be normalized.
 */
fun normalizeToE164(phone: String): String? {
    val cleaned = phone.replace("[\\s\\-()]".toRegex(), "")
    val digits = cleaned.removePrefix("+")
    return when {
        cleaned.startsWith("+90") && digits.length == 12 -> cleaned
        digits.startsWith("90") && digits.length == 12 -> "+$digits"
        digits.startsWith("0") && digits.length == 11 -> "+90${digits.drop(1)}"
        digits.startsWith("5") && digits.length == 10 -> "+90$digits"
        cleaned.startsWith("+") && digits.length >= 10 -> cleaned
        else -> null
    }
}
