package com.muhabbet.auth.domain.port.out

import java.util.UUID

/**
 * Bounds how many *distinct* phone hashes one account may look up per UTC day (#748, T-DIR-01).
 *
 * Unsalted SHA-256 of a Turkish mobile number is no protection — there are about 10^9 candidates —
 * so the only thing standing between contact sync and enumeration of the whole numbering plan is a
 * per-account budget. It counts hashes **new to this account today**, not requests: a phone that
 * re-syncs the same address book on every launch spends nothing after the first sync, while a
 * script walking the number space spends one unit per guess.
 *
 * Counters live outside the JVM (Redis), so a deploy does not refill anyone's budget.
 */
interface ContactSyncQuotaPort {

    /**
     * Records [phoneHashes] against [userId]'s budget for the current UTC day and reports whether
     * they fit. All or nothing: when the hashes not already counted today would take the account
     * over its limit, nothing is recorded and the answer is false.
     */
    fun tryConsume(userId: UUID, phoneHashes: Set<String>): Boolean
}
