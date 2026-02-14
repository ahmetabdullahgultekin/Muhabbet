package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.port.`in`.ContactSyncUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.dto.MatchedContact
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class ContactControllerTest {

    private lateinit var contactSyncUseCase: ContactSyncUseCase
    private lateinit var controller: ContactController

    private val userId = TestData.USER_ID_1

    @BeforeEach
    fun setUp() {
        contactSyncUseCase = mockk()
        controller = ContactController(contactSyncUseCase)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class SyncContacts {

        @Test
        fun `should return matched contacts for valid hashes`() {
            val hashes = listOf("hash1", "hash2", "hash3")
            val matched = listOf(
                MatchedContact(phoneHash = "hash1", userId = TestData.USER_ID_2.toString(), displayName = "User 2", avatarUrl = null),
                MatchedContact(phoneHash = "hash3", userId = TestData.USER_ID_3.toString(), displayName = "User 3", avatarUrl = null)
            )

            every { contactSyncUseCase.syncContacts(userId, hashes) } returns matched

            val response = controller.syncContacts(
                com.muhabbet.shared.dto.ContactSyncRequest(phoneHashes = hashes)
            )

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.matchedContacts?.size == 2)
        }

        @Test
        fun `should return empty list when no contacts match`() {
            every { contactSyncUseCase.syncContacts(userId, listOf("unknown-hash")) } returns emptyList()

            val response = controller.syncContacts(
                com.muhabbet.shared.dto.ContactSyncRequest(phoneHashes = listOf("unknown-hash"))
            )

            assert(response.body?.data?.matchedContacts?.isEmpty() == true)
        }

        @Test
        fun `should throw CONTACT_SYNC_LIMIT_EXCEEDED for too many hashes`() {
            val tooManyHashes = (1..1001).map { "hash-$it" }

            every {
                contactSyncUseCase.syncContacts(userId, tooManyHashes)
            } throws BusinessException(ErrorCode.CONTACT_SYNC_LIMIT_EXCEEDED)

            try {
                controller.syncContacts(
                    com.muhabbet.shared.dto.ContactSyncRequest(phoneHashes = tooManyHashes)
                )
                assert(false)
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.CONTACT_SYNC_LIMIT_EXCEEDED)
            }
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
