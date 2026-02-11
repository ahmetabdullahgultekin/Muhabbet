package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.AuthTokenResponse
import com.muhabbet.shared.dto.FirebaseVerifyRequest
import com.muhabbet.shared.dto.RequestOtpRequest
import com.muhabbet.shared.dto.RequestOtpResponse
import com.muhabbet.shared.dto.UpdateProfileRequest
import com.muhabbet.shared.dto.VerifyOtpRequest
import com.muhabbet.shared.model.UserProfile

class AuthRepository(
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage
) {

    suspend fun requestOtp(phoneNumber: String): RequestOtpResponse {
        val response = apiClient.post<RequestOtpResponse>(
            "/api/v1/auth/otp/request",
            RequestOtpRequest(phoneNumber)
        )
        return response.data ?: throw Exception(response.error?.message ?: "OTP isteği başarısız")
    }

    suspend fun verifyOtp(
        phoneNumber: String,
        otp: String,
        deviceName: String,
        platform: String
    ): AuthTokenResponse {
        val response = apiClient.post<AuthTokenResponse>(
            "/api/v1/auth/otp/verify",
            VerifyOtpRequest(phoneNumber, otp, deviceName, platform)
        )
        val data = response.data ?: throw Exception(response.error?.message ?: "Doğrulama başarısız")

        tokenStorage.saveTokens(
            accessToken = data.accessToken,
            refreshToken = data.refreshToken,
            userId = data.userId,
            deviceId = data.deviceId
        )

        return data
    }

    suspend fun verifyFirebaseToken(
        idToken: String,
        deviceName: String,
        platform: String
    ): AuthTokenResponse {
        val response = apiClient.post<AuthTokenResponse>(
            "/api/v1/auth/firebase-verify",
            FirebaseVerifyRequest(idToken, deviceName, platform)
        )
        val data = response.data ?: throw Exception(response.error?.message ?: "Firebase doğrulama başarısız")

        tokenStorage.saveTokens(
            accessToken = data.accessToken,
            refreshToken = data.refreshToken,
            userId = data.userId,
            deviceId = data.deviceId
        )

        return data
    }

    suspend fun getProfile(): UserProfile {
        val response = apiClient.get<UserProfile>("/api/v1/users/me")
        return response.data ?: throw Exception("Profil yüklenemedi")
    }

    suspend fun updateProfile(displayName: String? = null, about: String? = null) {
        apiClient.patch<UserProfile>("/api/v1/users/me", UpdateProfileRequest(displayName = displayName, about = about))
    }

    fun logout() {
        tokenStorage.clear()
    }

    fun isLoggedIn(): Boolean = tokenStorage.isLoggedIn()
}
