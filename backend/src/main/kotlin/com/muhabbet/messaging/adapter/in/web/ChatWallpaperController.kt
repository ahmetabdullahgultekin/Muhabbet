package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.ChatWallpaper
import com.muhabbet.messaging.domain.model.WallpaperType
import com.muhabbet.messaging.domain.port.`in`.ManageChatWallpaperUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.SetWallpaperRequest
import com.muhabbet.shared.dto.WallpaperResponse
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Deliberately unused by the mobile client (#380), and now safe to use.
 *
 * The wallpaper picker persists and reads its selection through device-local storage
 * (`WallpaperRepository`/`TokenStorage` on mobile) rather than this endpoint. The reasoning, and what
 * it costs the user, is written down on `WallpaperRepository`; the short version is that the two
 * things a round trip would buy — a second device and a new phone — are respectively gated on the
 * paused multi-device work and only half-solvable while a CUSTOM wallpaper's bytes are device-local.
 *
 * What changed here is that "unused" no longer means "untested and wrong". This file used to declare
 * its own `SetWallpaperRequest`/`WallpaperResponse`, named `type`/`value`, shadowing the shared DTOs
 * of the same names that any mobile client would serialise (`wallpaperType`/`wallpaperValue`). The
 * first real call would have deserialised into `type = "DEFAULT"` and wiped the wallpaper it was sent
 * to set — the #377 shape, where a private copy of a shared DTO quietly disagrees with it. It now
 * uses the shared classes, so the contract this vertical advertises is the one it implements.
 */
@RestController
@RequestMapping("/api/v1/wallpapers")
class ChatWallpaperController(
    private val manageChatWallpaperUseCase: ManageChatWallpaperUseCase
) {

    @GetMapping
    fun getWallpapers(): ResponseEntity<ApiResponse<List<WallpaperResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val wallpapers = manageChatWallpaperUseCase.getWallpapers(userId)
        return ApiResponseBuilder.ok(wallpapers.map { it.toResponse() })
    }

    @PutMapping
    fun setGlobalWallpaper(@RequestBody request: SetWallpaperRequest): ResponseEntity<ApiResponse<WallpaperResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val type = request.toWallpaperType()
        val wallpaper = manageChatWallpaperUseCase.setGlobalWallpaper(userId, type, request.wallpaperValue, request.darkModeValue)
        return ApiResponseBuilder.ok(wallpaper.toResponse())
    }

    @PutMapping("/conversations/{conversationId}")
    fun setConversationWallpaper(
        @PathVariable conversationId: UUID,
        @RequestBody request: SetWallpaperRequest
    ): ResponseEntity<ApiResponse<WallpaperResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val type = request.toWallpaperType()
        val wallpaper = manageChatWallpaperUseCase.setConversationWallpaper(userId, conversationId, type, request.wallpaperValue, request.darkModeValue)
        return ApiResponseBuilder.ok(wallpaper.toResponse())
    }

    @DeleteMapping("/{wallpaperId}")
    fun removeWallpaper(@PathVariable wallpaperId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageChatWallpaperUseCase.removeWallpaper(wallpaperId, userId)
        return ApiResponseBuilder.ok(Unit)
    }
}

private fun ChatWallpaper.toResponse() = WallpaperResponse(
    id = id.toString(),
    conversationId = conversationId?.toString(),
    wallpaperType = wallpaperType.name,
    wallpaperValue = wallpaperValue,
    darkModeValue = darkModeValue,
    createdAt = createdAt.toString()
)

/**
 * `uppercase()` with no locale is Kotlin's root-locale conversion, so "solid" does not become
 * "SOLİD" on a Turkish device — the trap CLAUDE.md warns about applies to the JDK's locale-sensitive
 * overload, not this one.
 *
 * An unknown type is a client error, not a server one: without this, `valueOf` throws
 * `IllegalArgumentException` and Spring answers 500 for what is a malformed request body.
 */
private fun SetWallpaperRequest.toWallpaperType(): WallpaperType =
    WallpaperType.entries.firstOrNull { it.name == wallpaperType.uppercase() }
        ?: throw BusinessException(ErrorCode.WALLPAPER_INVALID_TYPE)
