package com.muhabbet.auth.domain.port.`in`

interface InvalidatePushTokenUseCase {
    /**
     * Forgets [pushToken] on whichever device holds it, because the push provider has said it will
     * never deliver to it again (uninstalled app, token from another sender, malformed token).
     *
     * Keyed on the token rather than a device id: the provider's answer names the token, and by the
     * time it arrives the token may already have moved to another device row — clearing it there is
     * still right, since it is the same dead token. Idempotent: a token nobody holds is a no-op.
     */
    fun invalidatePushToken(pushToken: String)
}
