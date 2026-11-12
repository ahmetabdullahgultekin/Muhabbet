package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.AgeEvidence
import com.muhabbet.auth.domain.model.AgeVerdict

/**
 * Decides whether presented evidence shows a person is old enough to hold an account (#751,
 * ADR 0029 rule 1).
 *
 * A port so the way age is established can change — self-declaration today, e-Devlet or NFC-ID if
 * counsel requires it (Q-L3) — without touching the registration path that asks the question.
 */
interface AgeAssurancePort {
    fun assess(evidence: AgeEvidence): AgeVerdict
}
