package com.muhabbet.auth.domain.port.`in`

interface RefreshTokenUseCase {
    fun refresh(refreshToken: String): TokenResult
}

data class TokenResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    /** The owner of the rotated token — the caller re-states it, so it must not be blank. */
    val userId: String,
    val deviceId: String
)
