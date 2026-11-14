package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ManageJoinRequestUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.PostMapping

/**
 * #788 review: `POST /api/v1/conversations/{id}/join-requests` let any signed-in user file a request
 * for any group with a `linkId` nobody validated — a spam channel into a group's admin queue, and a
 * way to bump another group's link counter on approval. Requests are now created only through
 * `InviteLinkService.joinViaLink`, which validates the token, expiry and use count first.
 *
 * These pin that no second, unvalidated way in comes back: the only POSTs on the controller are the
 * two review actions, and the use case has no creation method at all.
 */
class JoinRequestControllerTest {

    @Test
    fun `should expose no endpoint that creates a join request without an invite link`() {
        val postPaths = JoinRequestController::class.java.declaredMethods
            .mapNotNull { it.getAnnotation(PostMapping::class.java) }
            .map { (it.value.toList() + it.path.toList()).joinToString() }
            .sorted()

        assertEquals(listOf("/{requestId}/approve", "/{requestId}/reject"), postPaths)
    }

    @Test
    fun `should offer admins no way to create a request through the review use case`() {
        val names = ManageJoinRequestUseCase::class.java.declaredMethods.map { it.name }.toSet()

        assertTrue(names.none { it.startsWith("request") || it.startsWith("create") }, "unexpected: $names")
    }
}
