package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ChatWallpaper
import com.muhabbet.messaging.domain.model.WallpaperType
import com.muhabbet.messaging.domain.port.out.ChannelAnalyticsRepository
import com.muhabbet.messaging.domain.port.out.ChatWallpaperRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID

/** A wallpaper is its owner's to remove; a channel's numbers are its admins' to read and its subscribers' to move. */
class WallpaperAndAnalyticsAuthorizationTest {

    private val alice = TestData.USER_ID_1
    private val bob = TestData.USER_ID_2

    @Test
    fun `should refuse to remove a wallpaper when it belongs to someone else`() {
        val wallpapers = mockk<ChatWallpaperRepository>(relaxed = true)
        val alicesWallpaper = ChatWallpaper(userId = alice, wallpaperType = WallpaperType.SOLID, wallpaperValue = "#000000")
        every { wallpapers.findAllByUserId(bob) } returns emptyList()

        val ex = assertThrows<BusinessException> {
            ChatWallpaperService(wallpapers).removeWallpaper(alicesWallpaper.id, bob)
        }

        assertEquals(ErrorCode.WALLPAPER_NOT_FOUND, ex.errorCode)
        verify(exactly = 0) { wallpapers.delete(any()) }
    }

    @Test
    fun `should remove a wallpaper when the caller owns it`() {
        val wallpapers = mockk<ChatWallpaperRepository>(relaxed = true)
        val own = ChatWallpaper(userId = bob, wallpaperType = WallpaperType.SOLID, wallpaperValue = "#000000")
        every { wallpapers.findAllByUserId(bob) } returns listOf(own)

        ChatWallpaperService(wallpapers).removeWallpaper(own.id, bob)

        verify { wallpapers.delete(own.id) }
    }

    @Test
    fun `should refuse channel analytics when the caller is a plain subscriber`() {
        val channelId = UUID.randomUUID()
        val conversations = mockk<ConversationRepository>()
        every { conversations.findMember(channelId, bob) } returns TestData.member(channelId, bob)

        val ex = assertThrows<BusinessException> {
            ChannelAnalyticsService(mockk(relaxed = true), conversations)
                .getAnalytics(channelId, bob, LocalDate.now().minusDays(7), LocalDate.now())
        }

        assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, ex.errorCode)
    }

    @Test
    fun `should not count a view when the viewer is not subscribed`() {
        val channelId = UUID.randomUUID()
        val analytics = mockk<ChannelAnalyticsRepository>(relaxed = true)
        val conversations = mockk<ConversationRepository>()
        every { conversations.findMember(channelId, bob) } returns null

        assertThrows<BusinessException> { ChannelAnalyticsService(analytics, conversations).recordView(channelId, bob) }

        verify(exactly = 0) { analytics.incrementViewCount(any(), any()) }
    }
}
