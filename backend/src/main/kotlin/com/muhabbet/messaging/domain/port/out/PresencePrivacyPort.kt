package com.muhabbet.messaging.domain.port.out

import java.util.UUID

/**
 * A user's own choice about who may see that they are online. Messaging does not own user
 * settings, so it asks through this port; the adapter on the other side reads the auth module.
 */
interface PresencePrivacyPort {

    /** True when [userId] has set their last-seen and online status to be shown to nobody. */
    fun hidesPresenceFromEveryone(userId: UUID): Boolean
}
