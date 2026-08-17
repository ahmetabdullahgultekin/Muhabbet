package com.muhabbet.moderation.adapter.out.external

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.moderation.domain.port.out.UserDirectoryPort
import com.muhabbet.moderation.domain.port.out.UserDisplayInfo
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Bridges moderation's [UserDirectoryPort] to the auth module's user store, mirroring messaging's
 * `AuthUserDirectoryAdapter`. Being an adapter, this is the one class in moderation allowed to know
 * that users live in auth at all.
 *
 * Named `Moderation...` rather than the `AuthUserDirectoryAdapter` the sibling pattern would suggest:
 * messaging already has a class with exactly that simple name, and Spring's default
 * `AnnotationBeanNameGenerator` derives a bean name from the short class name only, ignoring the
 * package. Two `@Component`s named `AuthUserDirectoryAdapter` in different packages would collide on
 * the bean name `authUserDirectoryAdapter` and fail the context at startup — not a hypothetical, this
 * is the textbook Spring component-scan gotcha.
 */
@Component
class ModerationAuthUserDirectoryAdapter(
    private val userRepository: UserRepository
) : UserDirectoryPort {

    override fun findDisplayInfo(userIds: Collection<UUID>): Map<UUID, UserDisplayInfo> {
        if (userIds.isEmpty()) return emptyMap()
        return userRepository.findAllByIds(userIds.distinct())
            .associate { it.id to UserDisplayInfo(it.id, it.displayName, it.avatarUrl) }
    }
}
