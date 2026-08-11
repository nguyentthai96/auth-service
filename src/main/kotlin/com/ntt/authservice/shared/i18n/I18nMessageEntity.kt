package com.ntt.authservice.shared.i18n

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * JPA entity for i18n_messages table.
 * Stores dynamic i18n messages that can be managed at runtime without redeployment.
 * Used by DatabaseMessageSource to resolve message keys to locale-specific text.
 */
@Entity
@Table(name = "i18n_messages")
class I18nMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(name = "code", nullable = false, length = 128)
    lateinit var code: String

    @Column(name = "locale", nullable = false, length = 10)
    lateinit var locale: String

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    lateinit var message: String

    @Column(name = "module", nullable = false, length = 64)
    var module: String = "common"

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_at", nullable = false)
    var createdAt: Long = System.currentTimeMillis()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Long = System.currentTimeMillis()
}
