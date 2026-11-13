package com.muhabbet.app.data.repository

import com.muhabbet.shared.dto.BlockedUserResponse

/**
 * The slice of moderation the Blocked Users screen needs: read the list, take someone off it.
 *
 * A port rather than the whole [ModerationRepository] so the screen's Store depends on two calls
 * instead of five, and can be tested with a fake that suspends or fails on demand.
 */
interface BlockList {
    suspend fun getBlockedUsers(): List<BlockedUserResponse>
    suspend fun unblockUser(userId: String)
}
