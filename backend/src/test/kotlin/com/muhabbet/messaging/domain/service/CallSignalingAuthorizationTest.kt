package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.CallStatus
import com.muhabbet.messaging.domain.model.CallType
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.CallHistoryRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.messaging.domain.port.out.UserDisplayInfo
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Who may ring whom, and who may end a call. A block stops calls as it stops messages; a call id is
 * not a capability, so only the two people in a call can end it; and an unanswered ring does not
 * leave its callee "busy" forever.
 */
class CallSignalingAuthorizationTest {

    private lateinit var blockPolicy: BlockPolicyPort
    private lateinit var userDirectory: UserDirectoryPort
    private var now: Instant = Instant.parse("2026-11-12T10:00:00Z")
    private lateinit var service: CallSignalingService

    private val alice = TestData.USER_ID_1
    private val bob = TestData.USER_ID_2
    private val mallory = TestData.USER_ID_3

    @BeforeEach
    fun setUp() {
        blockPolicy = mockk()
        every { blockPolicy.hasBlocked(any(), any()) } returns false
        userDirectory = mockk()
        every { userDirectory.findDisplayInfo(any()) } answers {
            firstArg<Collection<UUID>>().associateWith { UserDisplayInfo(it, null, null) }
        }
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant() = now
        }
        service = CallSignalingService(mockk<CallHistoryRepository>(relaxed = true), blockPolicy, userDirectory, clock)
    }

    @Test
    fun `should refuse to ring when the callee has blocked the caller`() {
        every { blockPolicy.hasBlocked(bob, alice) } returns true

        val ex = assertThrows<BusinessException> { service.initiateCall("c1", alice, bob, CallType.VOICE) }

        assertEquals(ErrorCode.CALL_INVALID_TARGET, ex.errorCode)
        assertNull(service.getCall("c1"))
    }

    @Test
    fun `should refuse to ring when the caller has blocked the callee`() {
        every { blockPolicy.hasBlocked(alice, bob) } returns true

        assertThrows<BusinessException> { service.initiateCall("c1", alice, bob, CallType.VOICE) }
    }

    @Test
    fun `should refuse to ring when the callee does not exist`() {
        every { userDirectory.findDisplayInfo(any()) } returns emptyMap()

        assertThrows<BusinessException> { service.initiateCall("c1", alice, bob, CallType.VOICE) }
    }

    @Test
    fun `should refuse to ring when the call id is already in use`() {
        service.initiateCall("c1", alice, bob, CallType.VOICE)

        assertThrows<BusinessException> { service.initiateCall("c1", mallory, TestData.USER_ID_1, CallType.VOICE) }
        assertEquals(alice, service.getCall("c1")?.callerId)
    }

    @Test
    fun `should keep the first call when a second call with the same id passes its checks at the same moment`() {
        val carol = UUID.randomUUID()
        val dave = UUID.randomUUID()
        var interleaved = false
        // While the first call is being checked, a second one with the same id completes. Both
        // passed the "id not in use" check, so only the store itself can refuse the second.
        every { userDirectory.findDisplayInfo(listOf(bob)) } answers {
            if (!interleaved) {
                interleaved = true
                service.initiateCall("c1", carol, dave, CallType.VOICE)
            }
            mapOf(bob to UserDisplayInfo(bob, null, null))
        }

        assertThrows<BusinessException> { service.initiateCall("c1", alice, bob, CallType.VOICE) }
        assertEquals(carol, service.getCall("c1")?.callerId)
        assertNull(service.getActiveCallForUser(alice))
        assertNull(service.getActiveCallForUser(bob))
    }

    @Test
    fun `should report only the two parties as participants`() {
        service.initiateCall("c1", alice, bob, CallType.VOICE)

        assertTrue(service.isParticipant("c1", alice))
        assertTrue(service.isParticipant("c1", bob))
        assertFalse(service.isParticipant("c1", mallory))
    }

    @Test
    fun `should free the callee when a ring has gone unanswered past the timeout`() {
        service.initiateCall("c1", alice, bob, CallType.VOICE)
        now = now.plus(Duration.ofSeconds(CallSignalingService.RING_TIMEOUT_SECONDS + 1))

        val second = service.initiateCall("c2", mallory, bob, CallType.VOICE)

        assertNotNull(second)
        assertNull(service.getCall("c1"))
    }

    @Test
    fun `should keep the callee busy while a ring is still within the timeout`() {
        service.initiateCall("c1", alice, bob, CallType.VOICE)

        assertThrows<CallBusyException> { service.initiateCall("c2", mallory, bob, CallType.VOICE) }
        assertEquals(CallStatus.INITIATED, service.getCall("c1")?.status)
    }
}
