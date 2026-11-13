package com.muhabbet.auth.domain.port.out

/**
 * One-way hashing for the secrets this module checks but must never store: the local OTP and the
 * two-step PIN.
 *
 * A port rather than Spring Security's `PasswordEncoder` (#893): the domain names what it needs —
 * "hash this", "does this match" — and the adapter decides it is BCrypt. Two things the contract
 * guarantees that `PasswordEncoder` leaves to the caller:
 * - [hash] never returns `null`. Spring 7 marks `encode` nullable; the call sites used to answer
 *   that with `?: ""`, which would have stored an empty hash had it ever happened.
 * - [matches] is `false` for anything [hash] could not have produced, including the
 *   externally-verified sentinel the OTP record stores when a provider owns the code.
 */
interface PasswordHasher {
    fun hash(raw: String): String
    fun matches(raw: String, hashed: String): Boolean
}
