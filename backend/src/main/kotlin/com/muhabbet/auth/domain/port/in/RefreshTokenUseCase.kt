package com.muhabbet.auth.domain.port.`in`

interface RefreshTokenUseCase {
    suspend fun refresh(refreshToken: String): TokenResult
}

data class TokenResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)
