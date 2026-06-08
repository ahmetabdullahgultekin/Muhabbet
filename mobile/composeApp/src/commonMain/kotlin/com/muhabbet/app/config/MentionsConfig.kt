package com.muhabbet.app.config

/**
 * Feature flag for @mentions in group chats (Tier 2 — see `docs/design/T2-group-mentions.md`).
 *
 * DEFAULT = false → preserves the exact current behaviour. When false:
 *  - The composer never detects the trailing `@` token and never shows the member autocomplete popup.
 *  - No [com.muhabbet.shared.model.MentionRef] is ever recorded;
 *    [com.muhabbet.shared.protocol.WsMessage.SendMessage.mentions] is always sent empty → the wire is
 *    byte-identical to HEAD.
 *  - [com.muhabbet.app.ui.chat.MessageBubble] renders message text plainly (no mention-span highlight).
 *
 * When true:
 *  - Typing a trailing `@<query>` in a group composer surfaces a roster popup; selecting a member
 *    inserts the `@DisplayName` token and records a structured mention (userId + offset + length).
 *  - The collected mentions ride on the outgoing `SendMessage`.
 *  - Bubbles highlight mention spans in the çini cobalt accent.
 *
 * Mirrors [com.muhabbet.app.crypto.E2EConfig] / [com.muhabbet.app.multidevice.MultiDeviceConfig]:
 * a compile-time constant (KISS), flipped deliberately after the server-side slices land and review.
 * The backend has its own independent gate (`muhabbet.mentions.enabled`).
 */
object MentionsConfig {
    /** Master switch for the mobile @mentions UI. Keep false until the feature is promoted. */
    const val ENABLED: Boolean = false
}
