package com.muhabbet.shared.event

/**
 * The push provider has said it will never deliver to [pushToken] again (#908).
 *
 * Published by the messaging module's provider adapter, which is the only place the answer is
 * seen, and consumed by the auth module, which owns the `devices` row the token lives on. It sits
 * in the cross-cutting package rather than in either module so that neither has to import the
 * other's types to talk about it.
 *
 * This is the backend's **first** application event (CLAUDE.md, architecture rule 3). An event
 * rather than an out-port because the direction is "something happened that another module may
 * act on": the provider adapter has no result to wait for and no use for knowing who listens.
 * Delivery is a plain synchronous `@EventListener` — no transaction phase, no async executor —
 * because the publisher is already on the push executor and holds no transaction.
 *
 * @param reason the provider's own error code, for the log only. Never interpreted downstream:
 *   deciding which answers are permanent is the provider adapter's job, and an event is only
 *   published for one that is.
 */
data class PushTokenRejectedEvent(
    val pushToken: String,
    val reason: String
)
