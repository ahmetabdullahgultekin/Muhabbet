package com.muhabbet.app.util

import java.security.MessageDigest

actual fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }
}
