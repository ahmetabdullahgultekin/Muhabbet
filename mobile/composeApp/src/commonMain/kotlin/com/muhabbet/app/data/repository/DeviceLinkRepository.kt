package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.DeviceLinkBeginResponse
import com.muhabbet.shared.dto.DeviceLinkCompleteRequest
import com.muhabbet.shared.dto.LinkedDeviceResponse

/**
 * Repository for Tier-2 multi-device linking (the NON-CRYPTO slice).
 *
 * Talks to the backend `/api/v1/devices/link` endpoints (all gated by the server-side
 * `muhabbet.multi-device.enabled` flag). NO private key material is sent: begin returns the QR
 * token; complete sends the scanned token + this device's OPAQUE public bundle (null until the
 * libsignal-backed crypto slice ships). Errors surface as error codes, never hardcoded strings.
 */
class DeviceLinkRepository(private val apiClient: ApiClient) {

    /** Primary device: open a link session; render [DeviceLinkBeginResponse.linkToken] as a QR. */
    suspend fun beginLink(): DeviceLinkBeginResponse {
        val response = apiClient.post<DeviceLinkBeginResponse>("/api/v1/devices/link/begin", Unit)
        return response.data ?: throw Exception(response.error?.code ?: "DEVICE_LINK_BEGIN_FAILED")
    }

    /** Companion device: complete the handshake with the scanned token. */
    suspend fun completeLink(
        linkToken: String,
        platform: String,
        deviceName: String?,
        publicBundle: String?
    ): LinkedDeviceResponse {
        val response = apiClient.post<LinkedDeviceResponse>(
            "/api/v1/devices/link/complete",
            DeviceLinkCompleteRequest(
                linkToken = linkToken,
                platform = platform,
                deviceName = deviceName,
                publicBundle = publicBundle
            )
        )
        return response.data ?: throw Exception(response.error?.code ?: "DEVICE_LINK_COMPLETE_FAILED")
    }

    /** List this account's active (non-revoked) devices for the management screen. */
    suspend fun listDevices(): List<LinkedDeviceResponse> {
        val response = apiClient.get<List<LinkedDeviceResponse>>("/api/v1/devices/link")
        return response.data ?: emptyList()
    }

    /** Revoke a companion device the user owns. */
    suspend fun revokeDevice(deviceId: String): LinkedDeviceResponse {
        val response = apiClient.post<LinkedDeviceResponse>("/api/v1/devices/link/$deviceId/revoke", Unit)
        return response.data ?: throw Exception(response.error?.code ?: "DEVICE_LINK_REVOKE_FAILED")
    }
}
