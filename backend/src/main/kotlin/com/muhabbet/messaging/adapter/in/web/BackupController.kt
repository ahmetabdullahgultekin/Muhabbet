package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ManageBackupUseCase
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/backups")
class BackupController(
    private val manageBackupUseCase: ManageBackupUseCase
) {

    @PostMapping
    fun createBackup(
        @AuthenticationPrincipal user: AuthenticatedUser
    ): ResponseEntity<*> {
        val backup = manageBackupUseCase.createBackup(user.userId)
        return ApiResponseBuilder.ok(backup)
    }

    @GetMapping("/latest")
    fun getLatestBackup(
        @AuthenticationPrincipal user: AuthenticatedUser
    ): ResponseEntity<*> {
        val backup = manageBackupUseCase.getLatestBackup(user.userId)
        return ApiResponseBuilder.ok(backup)
    }

    @GetMapping
    fun listBackups(
        @AuthenticationPrincipal user: AuthenticatedUser
    ): ResponseEntity<*> {
        val backups = manageBackupUseCase.listBackups(user.userId)
        return ApiResponseBuilder.ok(backups)
    }
}
