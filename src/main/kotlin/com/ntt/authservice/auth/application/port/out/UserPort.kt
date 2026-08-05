package com.ntt.authservice.auth.application.port.out

import com.ntt.authservice.auth.domain.model.User

/**
 * Outbound port for user persistence — pure Kotlin interface.
 * Implementations live in adapter/out/persistence/.
 */
interface UserPort {
    fun findById(userId: Long): User?
    fun findByUsernameAndActive(username: String): User?
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
    fun save(user: User): User
}
