package com.muhabbet.auth.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "phone_hashes")
class PhoneHashJpaEntity(
    @Id
    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "phone_hash", nullable = false, unique = true)
    val phoneHash: String
)
