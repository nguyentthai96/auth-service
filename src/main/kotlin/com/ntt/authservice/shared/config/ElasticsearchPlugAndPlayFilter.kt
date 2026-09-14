package com.ntt.authservice.shared.config

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata
import org.springframework.context.EnvironmentAware
import org.springframework.core.env.Environment

/**
 * Dynamic Plug-and-Play Filter for Elasticsearch.
 *
 * Plugs in (enables) Elasticsearch auto-configurations only when `app.elasticsearch.enabled=true`.
 * Unplugs (excludes) Elasticsearch auto-configurations by default (when false or unset),
 * preventing unwanted connections to localhost:9200 and failed health checks.
 */
class ElasticsearchPlugAndPlayFilter : AutoConfigurationImportFilter, EnvironmentAware {

    private var environment: Environment? = null

    override fun setEnvironment(environment: Environment) {
        this.environment = environment
    }

    override fun match(
        autoConfigurationClasses: Array<out String?>,
        autoConfigurationMetadata: AutoConfigurationMetadata
    ): BooleanArray {
        val enabled = environment?.getProperty("app.elasticsearch.enabled", Boolean::class.java, false) ?: false
        val result = BooleanArray(autoConfigurationClasses.size)

        for (i in autoConfigurationClasses.indices) {
            val className = autoConfigurationClasses[i]
            if (className != null && isElasticsearchAutoConfig(className)) {
                // If plugged in (enabled), allow auto-configuration to load; otherwise filter it out (unplug)
                result[i] = enabled
            } else {
                result[i] = true
            }
        }
        return result
    }

    private fun isElasticsearchAutoConfig(className: String): Boolean {
        return className.startsWith("org.springframework.boot.autoconfigure.elasticsearch.") ||
               className.startsWith("org.springframework.boot.autoconfigure.data.elasticsearch.") ||
               className.startsWith("org.springframework.boot.actuate.autoconfigure.elasticsearch.")
    }
}
