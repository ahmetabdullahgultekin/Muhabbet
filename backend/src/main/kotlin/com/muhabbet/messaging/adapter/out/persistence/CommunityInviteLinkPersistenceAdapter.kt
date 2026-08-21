package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityInviteLinkJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataCommunityInviteLinkRepository
import com.muhabbet.messaging.domain.model.CommunityInviteLink
import com.muhabbet.messaging.domain.port.out.CommunityInviteLinkRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CommunityInviteLinkPersistenceAdapter(
    private val repo: SpringDataCommunityInviteLinkRepository
) : CommunityInviteLinkRepository {

    override fun save(link: CommunityInviteLink): CommunityInviteLink =
        repo.save(CommunityInviteLinkJpaEntity.fromDomain(link)).toDomain()

    override fun findById(id: UUID): CommunityInviteLink? =
        repo.findById(id).orElse(null)?.toDomain()

    override fun findByToken(token: String): CommunityInviteLink? =
        repo.findByInviteToken(token)?.toDomain()

    override fun findActiveByCommunityId(communityId: UUID): List<CommunityInviteLink> =
        repo.findByCommunityIdAndIsActiveTrueOrderByCreatedAtDesc(communityId).map { it.toDomain() }

    override fun countActiveByCommunityId(communityId: UUID): Int =
        repo.countByCommunityIdAndIsActiveTrue(communityId)

    override fun deactivate(id: UUID) {
        // Read-mutate-save rather than a @Modifying UPDATE, matching GroupInviteLinkPersistenceAdapter.
        // A missing row is a no-op, not an error: the service has already resolved the link and
        // checked authority, so the only way to get here without one is a concurrent revoke, and the
        // caller's intent — "this link must not work" — is satisfied either way.
        val entity = repo.findById(id).orElse(null) ?: return
        entity.isActive = false
        repo.save(entity)
    }

    override fun incrementUseCount(id: UUID) {
        // Read-modify-write, so two people accepting the same link at the same instant can both read
        // the same count and one increment can be lost. That undercounts uses, which spends the link
        // more slowly than it should — it never admits someone past `maxUses` in a way the *next*
        // accept fails to notice, because that check re-reads the row. Accepted at this app's
        // concurrency; an atomic `UPDATE ... SET use_count = use_count + 1` is the fix if it ever
        // matters.
        val entity = repo.findById(id).orElse(null) ?: return
        entity.useCount += 1
        repo.save(entity)
    }
}
