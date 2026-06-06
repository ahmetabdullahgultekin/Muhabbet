package com.muhabbet.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Feature flag for Tier-2 multi-device linked sessions.
 *
 * DEFAULT = **false** → the linked-device endpoints are not reachable and the single-device path is
 * byte-identical to today. This is a CORE-PATH capability, shipped OFF and flipped deliberately
 * (dark → staging → canary one account → broad) per the project's reversible-rollout posture.
 * Kill-switch is the flag (`muhabbet.multi-device.enabled`), not a redeploy.
 */
@ConfigurationProperties("muhabbet.multi-device")
data class MultiDeviceProperties(
    val enabled: Boolean = false
)
