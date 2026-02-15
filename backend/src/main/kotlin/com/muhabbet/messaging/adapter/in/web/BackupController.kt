package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ManageBackupUseCase
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/backups")
class BackupController(
    private val manageBackupUseCase: ManageBackupUseCase
) {

    @PostMapping
    fun createBackup(): ResponseEntity<*> {
        val userId = AuthenticatedUser.currentUserId()
        val backup = manageBackupUseCase.createBackup(userId)
        return ApiResponseBuilder.ok(backup)
    }

    @GetMapping("/latest")
    fun getLatestBackup(): ResponseEntity<*> {
        val userId = AuthenticatedUser.currentUserId()
        val backup = manageBackupUseCase.getLatestBackup(userId)
        return ApiResponseBuilder.ok(backup)
    }

    @GetMapping
    fun listBackups(): ResponseEntity<*> {
        val userId = AuthenticatedUser.currentUserId()
        val backups = manageBackupUseCase.listBackups(userId)
        return ApiResponseBuilder.ok(backups)
    }
}
