package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.EncryptionKeyBundle
import com.muhabbet.messaging.domain.model.OneTimePreKey
import com.muhabbet.messaging.domain.port.`in`.ManageEncryptionUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.PreKeyBundleResponse
import com.muhabbet.shared.dto.RegisterKeyBundleRequest
import com.muhabbet.shared.dto.UploadPreKeysRequest
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/encryption")
class EncryptionController(
    private val manageEncryptionUseCase: ManageEncryptionUseCase
) {

    @PutMapping("/keys")
    fun registerKeyBundle(
        @RequestBody request: RegisterKeyBundleRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        val bundle = EncryptionKeyBundle(
            userId = userId,
            identityKey = request.identityKey,
            signedPreKey = request.signedPreKey,
            signedPreKeyId = request.signedPreKeyId,
            registrationId = request.registrationId
        )
        manageEncryptionUseCase.registerKeyBundle(userId, bundle)
        return ApiResponseBuilder.ok(Unit)
    }

    @PostMapping("/prekeys")
    fun uploadPreKeys(
        @RequestBody request: UploadPreKeysRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        val keys = request.preKeys.map { preKey ->
            OneTimePreKey(
                userId = userId,
                keyId = preKey.keyId,
                publicKey = preKey.publicKey
            )
        }
        manageEncryptionUseCase.uploadPreKeys(userId, keys)
        return ApiResponseBuilder.ok(Unit)
    }

    @GetMapping("/bundle/{userId}")
    fun fetchPreKeyBundle(
        @PathVariable userId: UUID
    ): ResponseEntity<ApiResponse<PreKeyBundleResponse>> {
        val bundle = manageEncryptionUseCase.fetchPreKeyBundle(userId)
            ?: throw BusinessException(ErrorCode.ENCRYPTION_KEY_BUNDLE_NOT_FOUND)

        val response = PreKeyBundleResponse(
            identityKey = bundle.identityKey,
            signedPreKey = bundle.signedPreKey,
            signedPreKeyId = bundle.signedPreKeyId,
            registrationId = bundle.registrationId,
            oneTimePreKey = bundle.oneTimePreKey,
            oneTimePreKeyId = bundle.oneTimePreKeyId
        )
        return ApiResponseBuilder.ok(response)
    }
}
