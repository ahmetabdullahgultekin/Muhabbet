package com.muhabbet.auth.domain.port.`in`

import java.util.UUID

interface RegisterPushTokenUseCase {
    /**
     * @param locale BCP-47 tag for the language this device wants push text in, or null to leave
     *   whatever it registered last untouched. Null rather than "unset" because the two call sites
     *   on the client know different amounts: the app, which is rendering strings and therefore
     *   knows exactly which language the user is reading, and `onNewToken`, a system callback in a
     *   service with no UI, which may fire before the app has ever run in the process. A rotated
     *   token must not silently downgrade the language to the fallback.
     *
     * No default value on this parameter on purpose — every caller states its answer, so a new one
     * cannot inherit "unknown" by omission.
     */
    fun registerPushToken(userId: UUID, deviceId: UUID, pushToken: String, locale: String?)
}
