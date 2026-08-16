package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.BroadcastListResponse
import com.muhabbet.shared.dto.BroadcastMemberResponse
import com.muhabbet.shared.dto.CreateBroadcastListRequest

/**
 * The broadcast-list endpoints, in one place.
 *
 * They used to be string literals inside the two screens, and both spelled the path
 * `/api/v1/broadcasts` while the controller is mapped to `/api/v1/broadcast-lists` — so every
 * request 404'd from the day the feature shipped (#392). Before #374 that 404 decoded to
 * `data = null` and rendered as "Henüz yayın listesi yok", which is why nobody noticed.
 *
 * A repository does not make a typo impossible, but it makes there be exactly one of it, and it is
 * the seam a test can drive: `BroadcastListRepositoryTest` asserts the request path itself.
 */
class BroadcastListRepository(
    private val apiClient: ApiClient
) {

    companion object {
        /** Must match `@RequestMapping` on the backend's `BroadcastListController`. */
        internal const val BASE_PATH = "/api/v1/broadcast-lists"
    }

    suspend fun getBroadcastLists(): List<BroadcastListResponse> {
        val response = apiClient.get<List<BroadcastListResponse>>(BASE_PATH)
        return response.data ?: emptyList()
    }

    suspend fun createBroadcastList(name: String, memberIds: List<String> = emptyList()): BroadcastListResponse {
        val response = apiClient.post<BroadcastListResponse>(
            BASE_PATH,
            CreateBroadcastListRequest(name = name, memberIds = memberIds)
        )
        return response.data ?: throw Exception("BROADCAST_LIST_CREATE_FAILED")
    }

    /**
     * Owner-only server-side. A caller who does not own the list gets an [ApiException], not an
     * empty list — a list rendered with nobody in it is indistinguishable from one that was
     * deleted, and the screen has to be able to tell the difference.
     */
    suspend fun getBroadcastListMembers(broadcastListId: String): List<BroadcastMemberResponse> {
        val response = apiClient.get<List<BroadcastMemberResponse>>("$BASE_PATH/$broadcastListId/members")
        return response.data ?: emptyList()
    }
}
