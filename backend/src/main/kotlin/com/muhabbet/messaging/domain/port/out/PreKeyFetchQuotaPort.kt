package com.muhabbet.messaging.domain.port.out

import java.util.UUID

/**
 * Bounds how fast one account may claim other accounts' one-time prekeys (#749, ADR 0018 §3 rule 2).
 *
 * Every bundle fetch consumes one of the target's one-time prekeys. Without a budget, any account
 * can exhaust a victim's stock and force every later session onto the signed prekey alone, which
 * weakens forward secrecy for the victim's first messages. The relationship check narrows *who*
 * may fetch; this narrows *how often*, and the per-pair ceiling is the primary control, because a
 * relationship is something an attacker can sometimes manufacture.
 */
interface PreKeyFetchQuotaPort {

    /**
     * Records one bundle fetch by [requesterId] for [targetUserId] and reports whether it is within
     * both the per-requester and the per-(requester, target) budget.
     */
    fun tryConsume(requesterId: UUID, targetUserId: UUID): Boolean
}
