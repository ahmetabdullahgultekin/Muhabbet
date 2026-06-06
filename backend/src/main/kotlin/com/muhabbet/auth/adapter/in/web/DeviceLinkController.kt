package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DeviceLinkSession
import com.muhabbet.auth.domain.port.`in`.LinkDeviceUseCase
import com.muhabbet.shared.config.MultiDeviceProperties
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.DeviceLinkBeginResponse
import com.muhabbet.shared.dto.DeviceLinkCompleteRequest
import com.muhabbet.shared.dto.LinkedDeviceResponse
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Linked-device management (Tier 2 multi-device, NON-CRYPTO slice S1).
 *
 * Every endpoint is gated by the `muhabbet.multi-device.enabled` flag (default OFF). With the flag
 * OFF the whole controller short-circuits to 403 `DEVICE_LINKING_DISABLED` and nothing in the
 * device registry / link tables is touched — the single-device path is unchanged. The QR link
 * payload travels via [DeviceLinkBeginResponse]; the companion completes via
 * [DeviceLinkCompleteRequest]. No private key material crosses any of these endpoints.
 */
@RestController
@RequestMapping("/api/v1/devices/link")
class DeviceLinkController(
    private val linkDeviceUseCase: LinkDeviceUseCase,
    private val multiDeviceProperties: MultiDeviceProperties
) {

    /** Primary opens a link session and gets the QR token to render. */
    @PostMapping("/begin")
    fun begin(): ResponseEntity<ApiResponse<DeviceLinkBeginResponse>> {
        requireEnabled()
        val userId = AuthenticatedUser.currentUserId()
        val primaryDeviceId = AuthenticatedUser.currentDeviceId()
        val session = linkDeviceUseCase.beginLink(userId, primaryDeviceId)
        return ApiResponseBuilder.created(session.toBeginResponse())
    }

    /** Companion presents the scanned token + its platform/name to finish the handshake. */
    @PostMapping("/complete")
    fun complete(
        @RequestBody request: DeviceLinkCompleteRequest
    ): ResponseEntity<ApiResponse<LinkedDeviceResponse>> {
        requireEnabled()
        val device = linkDeviceUseCase.completeLink(
            linkToken = request.linkToken,
            companionPlatform = request.platform,
            companionDeviceName = request.deviceName,
            publicBundle = request.publicBundle
        )
        return ApiResponseBuilder.created(device.toLinkedResponse())
    }

    /** List the user's active (non-revoked) devices for the management screen. */
    @GetMapping
    fun list(): ResponseEntity<ApiResponse<List<LinkedDeviceResponse>>> {
        requireEnabled()
        val userId = AuthenticatedUser.currentUserId()
        val devices = linkDeviceUseCase.listDevices(userId)
        return ApiResponseBuilder.ok(devices.map { it.toLinkedResponse() })
    }

    /** Revoke a companion device the user owns. */
    @PostMapping("/{deviceId}/revoke")
    fun revoke(@PathVariable deviceId: UUID): ResponseEntity<ApiResponse<LinkedDeviceResponse>> {
        requireEnabled()
        val userId = AuthenticatedUser.currentUserId()
        val device = linkDeviceUseCase.revokeDevice(userId, deviceId)
        return ApiResponseBuilder.ok(device.toLinkedResponse())
    }

    private fun requireEnabled() {
        if (!multiDeviceProperties.enabled) {
            throw BusinessException(ErrorCode.DEVICE_LINKING_DISABLED)
        }
    }
}

private fun DeviceLinkSession.toBeginResponse() = DeviceLinkBeginResponse(
    sessionId = id.toString(),
    linkToken = linkToken,
    expiresAt = expiresAt.toString()
)

private fun Device.toLinkedResponse() = LinkedDeviceResponse(
    id = id.toString(),
    platform = platform,
    displayName = displayName ?: deviceName,
    isPrimary = isPrimary,
    isCompanion = isCompanion,
    linkedByDeviceId = linkedByDeviceId?.toString(),
    lastActiveAt = lastActiveAt?.toString(),
    createdAt = createdAt.toString(),
    revoked = !isActive
)
