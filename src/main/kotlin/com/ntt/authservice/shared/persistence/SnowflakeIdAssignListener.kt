package com.ntt.authservice.shared.persistence

import com.ntt.basecore.model.id.SnowflakeIdGenerator
import jakarta.persistence.PrePersist
import org.slf4j.LoggerFactory

/**
 * JPA entity listener that assigns Snowflake IDs before persist.
 *
 * Workaround for Hibernate 7.4.x compatibility issue where @IdGeneratorType
 * on inherited @SnowflakeId annotation is not properly resolved from parent
 * MappedSuperclass fields.
 *
 * This listener intercepts @PrePersist and assigns a Snowflake ID if the entity's
 * `id` field is null — ensuring all Snowflake-based entities get proper IDs.
 */
class SnowflakeIdAssignListener {

    private val log = LoggerFactory.getLogger(SnowflakeIdAssignListener::class.java)

    @PrePersist
    fun assignId(entity: Any) {
        try {
            // Find the 'id' field via reflection (walk up hierarchy)
            var clazz: Class<*>? = entity.javaClass
            var idField: java.lang.reflect.Field? = null
            while (clazz != null && idField == null) {
                try {
                    idField = clazz.getDeclaredField("id")
                } catch (_: NoSuchFieldException) {
                    clazz = clazz.superclass
                }
            }

            if (idField != null) {
                idField.isAccessible = true
                val currentId = idField.get(entity)
                if (currentId == null) {
                    val newId = SnowflakeIdGenerator.nextId()
                    idField.set(entity, newId)
                    log.trace("SNOWFLAKE_ID_ASSIGNED entity={}, id={}", entity.javaClass.simpleName, newId)
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to assign Snowflake ID to {}: {}", entity.javaClass.simpleName, e.message)
        }
    }
}
