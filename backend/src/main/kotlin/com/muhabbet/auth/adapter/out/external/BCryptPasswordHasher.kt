package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.port.out.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

/**
 * [PasswordHasher] on BCrypt, the only place the auth domain's hashing meets Spring Security.
 *
 * The cost factor is BCrypt's default (10), exactly what the `BCryptPasswordEncoder()` bean this
 * replaces used, so every OTP and PIN hash already stored still verifies.
 */
@Component
class BCryptPasswordHasher : PasswordHasher {

    private val encoder = BCryptPasswordEncoder()

    override fun hash(raw: String): String =
        checkNotNull(encoder.encode(raw)) { "BCrypt produced no hash" }

    override fun matches(raw: String, hashed: String): Boolean = encoder.matches(raw, hashed)
}
