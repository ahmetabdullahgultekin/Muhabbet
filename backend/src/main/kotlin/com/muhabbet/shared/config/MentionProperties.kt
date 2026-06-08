package com.muhabbet.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Feature flag + bounds for Tier-2 group @mentions (see `docs/design/T2-group-mentions.md`, ADR-0008).
 *
 * DEFAULT = **false** → the server treats `mentions` / `mentionsEveryone` on inbound messages as
 * empty, persists nothing, and the send/fan-out path is byte-identical to today. Flipped
 * deliberately (dark → staging → canary one group → broad) per the project's reversible-rollout
 * posture; kill-switch is this flag (`muhabbet.mentions.enabled`), not a redeploy.
 *
 * [everyoneMaxMembers] caps `@everyone` expansion to avoid notification storms in large groups
 * (matches `ValidationRules.MAX_GROUP_MEMBERS`).
 */
@ConfigurationProperties("muhabbet.mentions")
data class MentionProperties(
    val enabled: Boolean = false,
    val everyoneMaxMembers: Int = 256
)
