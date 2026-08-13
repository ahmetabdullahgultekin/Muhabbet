package com.muhabbet.auth.domain.port.`in`

interface FirebaseVerifyUseCase {
    fun verifyFirebaseToken(
        idToken: String,
        deviceName: String,
        platform: String
    ): AuthResult
}
