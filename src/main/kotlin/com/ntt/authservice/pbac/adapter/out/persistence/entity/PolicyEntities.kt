package com.ntt.authservice.pbac.adapter.out.persistence.entity

import com.ntt.basecore.model.id.SnowflakeBaseEntity
import com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity
import jakarta.persistence.*

/**
 * PBAC Policy entity.
 * Inherits: id (Snowflake), audit fields, active from base-core.
 */
@Entity
@Table(name = "policies")
class PolicyEntity : SnowflakePersistentAuditableEntity() {

    @Column(name = "domain_id", nullable = false)
    var domainId: Long = 0

    @Column(nullable = false, length = 200)
    lateinit var name: String

    var description: String? = null

    @Column(name = "resource_id")
    var resourceId: Long? = null

    @Column(name = "action_id")
    var actionId: Long? = null

    @Column(nullable = false, length = 10)
    var effect: String = "ALLOW"

    @Column(nullable = false)
    var priority: Int = 100

    @Column(nullable = false, length = 20)
    var status: String = "DRAFT"

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "policy_id")
    var conditions: MutableList<PolicyConditionEntity> = mutableListOf()
}

/**
 * Policy condition with JSONB value.
 * Simple entity — no audit needed.
 */
@Entity
@Table(name = "policy_conditions")
class PolicyConditionEntity : SnowflakeBaseEntity() {

    @Column(name = "policy_id", nullable = false)
    var policyId: Long = 0

    @Column(name = "attribute_path", nullable = false, length = 200)
    lateinit var attributePath: String

    @Column(nullable = false, length = 20)
    lateinit var operator: String

    @Column(name = "\"value\"", columnDefinition = "text", nullable = false)
    lateinit var value: String

    @Column(name = "value_type", nullable = false, length = 20)
    var valueType: String = "STATIC"

    @Column(name = "condition_order", nullable = false)
    var conditionOrder: Int = 0
}
