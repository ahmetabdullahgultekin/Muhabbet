package com.muhabbet.auth.domain.port.`in`

interface FirebaseVerifyUseCase {
    suspend fun verifyFirebaseToken(
        idToken: String,
        deviceName: String,
        platform: String
    ): AuthResult
}
