package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.OneTimePreKeyJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SpringDataOneTimePreKeyRepository : JpaRepository<OneTimePreKeyJpaEntity, UUID> {

    /**
     * Claims the oldest unused one-time prekey of [userId] and marks it used, in one statement
     * (#749, ADR 0018 §3 rule 1). Returns null when the user has none left.
     *
     * Read-then-save — what this replaced — let two concurrent fetches read the same row and both
     * hand it out, which breaks the one-time assumption X3DH/PQXDH is built on. Here the inner
     * `FOR UPDATE SKIP LOCKED` makes each concurrent claimer lock a *different* row and pass over
     * rows another transaction already holds, so no two claims can return the same key and none
     * waits on another.
     *
     * Not `@Modifying`: the statement returns rows, so it is executed as a query and the claimed
     * row comes back mapped as the entity.
     */
    @Query(
        value = """
        UPDATE one_time_pre_keys
           SET used = true
         WHERE id = (
               SELECT id FROM one_time_pre_keys
                WHERE user_id = :userId AND used = false
                ORDER BY created_at, id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
         )
        RETURNING *
        """,
        nativeQuery = true
    )
    fun claimOldestUnused(@Param("userId") userId: UUID): OneTimePreKeyJpaEntity?

    fun countByUserIdAndUsedFalse(userId: UUID): Int
}
