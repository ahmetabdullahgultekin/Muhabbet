package com.muhabbet.auth.domain.model

/**
 * What the settings screen needs to know about a user's two-step verification, in one read.
 *
 * [hasRecoveryEmail] is here rather than being inferred by the caller because the screen's reset
 * flow is only offered when an address was actually stored, and the address itself must not leave
 * the server — the client needs the fact, not the value.
 */
data class TwoStepStatus(
    val enabled: Boolean,
    val hasRecoveryEmail: Boolean
)
