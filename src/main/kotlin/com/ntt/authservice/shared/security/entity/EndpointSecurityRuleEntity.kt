package com.ntt.authservice.shared.security.entity

import jakarta.persistence.*

/**
 * JPA entity for endpoint_security_rules table.
 * Uses standard JPA annotations (not SnowflakePersistentAuditableEntity) 
 * because this table uses BIGSERIAL auto-increment, not Snowflake IDs.
 */
@Entity
@Table(name = "endpoint_security_rules")
class EndpointSecurityRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "url_pattern", nullable = false, length = 500)
    var urlPattern: String = ""

    @Column(name = "pattern_type", nullable = false, length = 10)
    var patternType: String = "ANT" // EXACT, ANT, REGEX

    @Column(name = "http_method", length = 10)
    var httpMethod: String? = null // null = any method

    @Column(name = "access_type", nullable = false, length = 20)
    var accessType: String = "AUTHENTICATED"

    @Column(name = "required_roles", columnDefinition = "TEXT[]")
    var requiredRoles: Array<String>? = null

    @Column(name = "required_perms", columnDefinition = "TEXT[]")
    var requiredPerms: Array<String>? = null

    @Column(name = "domain_scope", length = 50)
    var domainScope: String? = null

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "description", length = 500)
    var description: String? = null

    @Column(name = "created_by", length = 100)
    var createdBy: String? = null

    @Column(name = "updated_by", length = 100)
    var updatedBy: String? = null
}
