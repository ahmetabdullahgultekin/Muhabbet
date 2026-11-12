package com.muhabbet.shared.security

import com.muhabbet.auth.adapter.`in`.web.UserController
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.adapter.`in`.web.GroupController
import com.muhabbet.messaging.domain.port.`in`.ManageGroupUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.config.MediaProperties
import com.muhabbet.shared.dto.UpdateGroupRequest
import com.muhabbet.shared.dto.UpdateProfileRequest
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Profile and group avatars are fetched by every viewer's phone without the viewer asking, so the
 * person setting one may not choose the server those phones talk to — the #679 rule, for avatars.
 */
class AvatarOriginTest {

    private val guard = MediaOriginGuard(
        MediaProperties(
            minio = MediaProperties.MinioProperties(endpoint = "http://minio:9000", publicEndpoint = "https://cdn.example")
        )
    )
    private val ours = "https://cdn.example/muhabbet-media/images/a.jpg?X-Amz-Signature=abc"
    private val theirs = "https://tracker.example/pixel.png"

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            JwtClaims(userId = TestData.USER_ID_1, deviceId = TestData.DEVICE_ID_1), null, emptyList()
        )
    }

    @Test
    fun `should accept an avatar when it is on our own media host`() {
        assertTrue(guard.isAllowed(ours))
    }

    @Test
    fun `should refuse an avatar when it is on any other host`() {
        assertFalse(guard.isAllowed(theirs))
        assertFalse(guard.isAllowed("https://cdn.example.attacker.test/x.png"))
    }

    @Test
    fun `should refuse a profile avatar when it points at a foreign host`() {
        val users = mockk<UserRepository>(relaxed = true)
        every { users.findById(TestData.USER_ID_1) } returns TestData.user()
        val controller = UserController(users, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), guard)

        val ex = assertThrows<BusinessException> { controller.updateMe(UpdateProfileRequest(avatarUrl = theirs)) }

        assertEquals(ErrorCode.MSG_MEDIA_NOT_ACCESSIBLE, ex.errorCode)
        verify(exactly = 0) { users.save(any()) }
    }

    @Test
    fun `should refuse a group avatar when it points at a foreign host`() {
        val groups = mockk<ManageGroupUseCase>(relaxed = true)
        val controller = GroupController(groups, mockk(relaxed = true), mockk(relaxed = true), guard)

        val ex = assertThrows<BusinessException> {
            controller.updateGroupInfo(TestData.GROUP_ID, UpdateGroupRequest(avatarUrl = theirs))
        }

        assertEquals(ErrorCode.MSG_MEDIA_NOT_ACCESSIBLE, ex.errorCode)
        verify(exactly = 0) { groups.updateGroupInfo(any(), any(), any(), any(), any()) }
    }
}
