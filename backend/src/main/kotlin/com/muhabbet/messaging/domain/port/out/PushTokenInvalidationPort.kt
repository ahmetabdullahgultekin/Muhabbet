package com.muhabbet.messaging.domain.port.out

/**
 * Out-port the push-notification adapter calls when the push provider reports a
 * **terminally dead** device token (uninstalled / rotated / malformed). The token is
 * removed from the device registry so it is never re-selected for a doomed send.
 *
 * The token store lives in the `auth` module; this seam keeps `messaging`'s push adapter
 * from depending on `auth` persistence directly (DIP / hexagonal cross-module rule).
 */
interface PushTokenInvalidationPort {
    /** Remove [pushToken] from every device that holds it. No-op if none do. */
    fun invalidate(pushToken: String)
}
