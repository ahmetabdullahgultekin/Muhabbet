package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.User
import java.util.UUID

interface UserRepository {
    fun findByPhoneNumber(phoneNumber: String): User?
    fun findById(id: UUID): User?
    fun save(user: User): User
    fun existsByPhoneNumber(phoneNumber: String): Boolean
}
