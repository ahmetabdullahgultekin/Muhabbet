package com.muhabbet.auth.domain.model

/** A freshly minted credential pair for one device. */
data class IssuedTokens(val accessToken: String, val refreshToken: String, val expiresIn: Long)
