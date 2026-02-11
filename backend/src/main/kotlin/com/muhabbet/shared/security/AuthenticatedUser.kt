package com.muhabbet.shared.security

import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

object AuthenticatedUser {

    fun currentUserId(): UUID {
        val claims = currentClaims()
        return claims.userId
    }

    fun currentDeviceId(): UUID {
        val claims = currentClaims()
        return claims.deviceId
    }

    private fun currentClaims(): JwtClaims {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw BusinessException(ErrorCode.AUTH_UNAUTHORIZED)
        val principal = auth.principal
        if (principal is JwtClaims) {
            return principal
        }
        throw BusinessException(ErrorCode.AUTH_UNAUTHORIZED)
    }
}
