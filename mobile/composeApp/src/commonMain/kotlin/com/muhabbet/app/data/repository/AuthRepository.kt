package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.AuthTokenResponse
import com.muhabbet.shared.dto.FirebaseVerifyRequest
import com.muhabbet.shared.dto.RegisterPushTokenRequest
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
        return response.data ?: throw Exception(response.error?.message ?: "OTP_REQUEST_FAILED")
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
        val data = response.data ?: throw Exception(response.error?.message ?: "VERIFY_OTP_FAILED")

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
        val data = response.data ?: throw Exception(response.error?.message ?: "FIREBASE_VERIFY_FAILED")

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
        return response.data ?: throw Exception(response.error?.message ?: "PROFILE_LOAD_FAILED")
    }

    suspend fun updateProfile(displayName: String? = null, about: String? = null, avatarUrl: String? = null) {
        apiClient.patch<UserProfile>("/api/v1/users/me", UpdateProfileRequest(displayName = displayName, about = about, avatarUrl = avatarUrl))
    }

    suspend fun registerPushToken(token: String) {
        apiClient.put<Unit>("/api/v1/devices/push-token", RegisterPushTokenRequest(pushToken = token))
    }

    suspend fun exportData() {
        apiClient.get<Unit>("/api/v1/users/data/export")
    }

    suspend fun deleteAccount() {
        apiClient.delete<Unit>("/api/v1/users/data/account")
        tokenStorage.clear()
    }

    fun logout() {
        tokenStorage.clear()
    }

    fun isLoggedIn(): Boolean = tokenStorage.isLoggedIn()
}
