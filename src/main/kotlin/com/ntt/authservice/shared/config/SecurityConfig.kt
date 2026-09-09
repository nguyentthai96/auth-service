package com.ntt.authservice.shared.config

import com.ntt.authservice.auth.adapter.`in`.web.filter.LoginRateLimitFilter
import com.ntt.authservice.auth.adapter.`in`.web.filter.ServiceAuthFilter
import com.ntt.authservice.shared.security.DynamicAuthorizationManager
import com.ntt.authservice.shared.security.JwtAuthFilter
import com.ntt.basecore.autoconfigure.openapi.OpenApiAutoConfiguration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties::class, OutboxProperties::class)
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val loginRateLimitFilter: LoginRateLimitFilter,
    private val serviceAuthFilter: ServiceAuthFilter,
    private val securityProperties: SecurityProperties,
    private val dynamicAuthorizationManager: DynamicAuthorizationManager,
    private val environment: Environment,
    @Value("\${springdoc.swagger-ui.enabled:true}")
    private val swaggerUiEnabled: Boolean,
    @Value("\${app.cors.allowed-origins:http://localhost:3000}")
    private val allowedOrigins: String
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val isSwaggerPermitted = swaggerUiEnabled && !environment.acceptsProfiles(Profiles.of("prod", "production"))

        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers { headers ->
                headers
                    .httpStrictTransportSecurity { hsts ->
                        hsts.maxAgeInSeconds(31536000).includeSubDomains(true)
                    }
                    .contentTypeOptions { }
                    .frameOptions { it.deny() }
            }
            .authorizeHttpRequests { auth ->
                if (isSwaggerPermitted) {
                    auth.requestMatchers(*OpenApiAutoConfiguration.SWAGGER_WHITELIST_PATHS).permitAll()
                }

                auth
                    // Public endpoints — no authentication required
                    .requestMatchers("/auth/login", "/auth/register", "/auth/refresh").permitAll()
                    .requestMatchers("/auth/key-exchange").permitAll()
                    .requestMatchers("/auth/mfa/verify", "/auth/mfa/resend").permitAll()
                    .requestMatchers("/auth/sso/callback", "/auth/sso/providers").permitAll()
                    .requestMatchers("/auth/forgot-password").permitAll()
                    .requestMatchers("/captcha/challenge").permitAll()
                    .requestMatchers("/.well-known/jwks.json").permitAll()
                    // Anonymous session — public create, ROLE_ANONYMOUS for operations
                    .requestMatchers("/auth/anonymous").permitAll()
                    .requestMatchers("/auth/anonymous/**").hasRole("ANONYMOUS")
                    // Internal service-to-service — requires service JWT (FR-021)
                    .requestMatchers("/internal/**").hasRole("SERVICE")
                    // Actuator & CORS preflight
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // All other endpoints — delegate to DynamicAuthorizationManager
                    .anyRequest().access(dynamicAuthorizationManager)
            }
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(serviceAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = this@SecurityConfig.allowedOrigins.split(",").map { it.trim() }
            allowCredentials = true
            allowedHeaders = listOf("*")
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            exposedHeaders = listOf("Retry-After")
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    @org.springframework.context.annotation.Primary
    fun twoLevelCacheManager(
        properties: com.ntt.basecore.autoconfigure.cache.CacheProperties,
        invalidationPublisher: org.springframework.beans.factory.ObjectProvider<com.ntt.basecore.autoconfigure.cache.CacheInvalidationPublisher>
    ): com.ntt.basecore.autoconfigure.cache.TwoLevelCacheManager {
        val inMemoryL1 = org.springframework.cache.concurrent.ConcurrentMapCacheManager()
        return com.ntt.basecore.autoconfigure.cache.TwoLevelCacheManager(
            l1CacheManager = inMemoryL1,
            l2CacheManager = null,
            invalidationPublisher = invalidationPublisher.ifAvailable,
            properties = properties
        )
    }
}

