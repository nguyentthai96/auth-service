package com.ntt.authservice.shared.config

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter

internal abstract class ProblemDetailMixin {
    @JsonAnyGetter
    abstract fun getProperties(): Map<String, Any?>?

    @JsonAnySetter
    abstract fun setProperty(name: String, value: Any?)
}

/**
 * Jackson configuration — provides Primary ObjectMapper bean and MappingJackson2HttpMessageConverter.
 * Preserves RFC 7807 ProblemDetail flattened properties via ProblemDetailMixin.
 */
@Configuration
class JacksonConfig {

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        return jacksonObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            .addMixIn(ProblemDetail::class.java, ProblemDetailMixin::class.java)
            .findAndRegisterModules()
    }

    @Bean
    fun mappingJackson2HttpMessageConverter(objectMapper: ObjectMapper): MappingJackson2HttpMessageConverter {
        return MappingJackson2HttpMessageConverter(objectMapper)
    }
}
