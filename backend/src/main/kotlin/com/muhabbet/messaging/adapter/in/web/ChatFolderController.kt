package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.ChatFolder
import com.muhabbet.messaging.domain.port.`in`.ManageChatFolderUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CreateChatFolderRequest(val name: String)
data class RenameChatFolderRequest(val name: String)
data class AddFolderConversationsRequest(val conversationIds: List<String>)
data class ChatFolderResponse(val id: String, val name: String, val position: Int, val createdAt: String)

@RestController
@RequestMapping("/api/v1/chat-folders")
class ChatFolderController(
    private val manageChatFolderUseCase: ManageChatFolderUseCase
) {

    @PostMapping
    fun create(@RequestBody request: CreateChatFolderRequest): ResponseEntity<ApiResponse<ChatFolderResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val folder = manageChatFolderUseCase.create(userId, request.name)
        return ApiResponseBuilder.created(folder.toResponse())
    }

    @GetMapping
    fun getMyFolders(): ResponseEntity<ApiResponse<List<ChatFolderResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        return ApiResponseBuilder.ok(manageChatFolderUseCase.getByOwner(userId).map { it.toResponse() })
    }

    @PatchMapping("/{folderId}")
    fun rename(
        @PathVariable folderId: UUID,
        @RequestBody request: RenameChatFolderRequest
    ): ResponseEntity<ApiResponse<ChatFolderResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val folder = manageChatFolderUseCase.rename(folderId, userId, request.name)
        return ApiResponseBuilder.ok(folder.toResponse())
    }

    @DeleteMapping("/{folderId}")
    fun delete(@PathVariable folderId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageChatFolderUseCase.delete(folderId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    @GetMapping("/{folderId}/conversations")
    fun getConversations(@PathVariable folderId: UUID): ResponseEntity<ApiResponse<List<String>>> {
        val userId = AuthenticatedUser.currentUserId()
        val ids = manageChatFolderUseCase.getConversationIds(folderId, userId).map { it.toString() }
        return ApiResponseBuilder.ok(ids)
    }

    @PostMapping("/{folderId}/conversations")
    fun addConversations(
        @PathVariable folderId: UUID,
        @RequestBody request: AddFolderConversationsRequest
    ): ResponseEntity<ApiResponse<List<String>>> {
        val userId = AuthenticatedUser.currentUserId()
        val conversationIds = request.conversationIds.map { UUID.fromString(it) }
        val added = manageChatFolderUseCase.addConversations(folderId, userId, conversationIds)
        return ApiResponseBuilder.ok(added.map { it.conversationId.toString() })
    }

    @DeleteMapping("/{folderId}/conversations/{conversationId}")
    fun removeConversation(
        @PathVariable folderId: UUID,
        @PathVariable conversationId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageChatFolderUseCase.removeConversation(folderId, userId, conversationId)
        return ApiResponseBuilder.ok(Unit)
    }
}

private fun ChatFolder.toResponse() = ChatFolderResponse(
    id = id.toString(),
    name = name,
    position = position,
    createdAt = createdAt.toString()
)
