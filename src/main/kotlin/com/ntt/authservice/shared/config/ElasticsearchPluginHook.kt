package com.ntt.authservice.shared.config

import com.ntt.basecore.hook.PluginLifecycleHook
import com.ntt.basecore.hook.PluginMetadata
import com.ntt.basecore.hook.PluginStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Lifecycle hook for the optional Elasticsearch plugin.
 *
 * Integrates with base-core PluginManager to reflect whether
 * Elasticsearch is dynamically plugged in or unplugged.
 */
@Component
class ElasticsearchPluginHook(
    @param:Value("\${app.elasticsearch.enabled:false}")
    private val enabled: Boolean,
    @param:Value("\${spring.elasticsearch.uris:http://localhost:9200}")
    private val uris: String
) : PluginLifecycleHook {

    private val log = LoggerFactory.getLogger(ElasticsearchPluginHook::class.java)

    override fun getPluginName(): String = "Elasticsearch"

    override fun getPluginVersion(): String = "8.x"

    override fun onReady() {
        if (enabled) {
            log.info("🔌 [PLUGIN] Elasticsearch is PLUGGED IN (uris: {})", uris)
        } else {
            log.info("🔌 [PLUGIN] Elasticsearch is UNPLUGGED (disabled by configuration)")
        }
    }

    override fun metadata(): PluginMetadata = PluginMetadata(
        name = getPluginName(),
        version = getPluginVersion(),
        status = if (enabled) PluginStatus.HEALTHY else PluginStatus.DISABLED,
        details = mapOf(
            "enabled" to enabled,
            "uris" to if (enabled) uris else "N/A"
        )
    )
}
