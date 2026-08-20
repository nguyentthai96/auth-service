package com.ntt.authservice.shared.config

import com.ntt.authservice.auth.adapter.`in`.web.filter.LoginRateLimitFilter
import com.ntt.authservice.auth.adapter.`in`.web.filter.ServiceAuthFilter
import com.ntt.authservice.shared.security.JwtAuthFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties::class)
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val loginRateLimitFilter: LoginRateLimitFilter,
    private val serviceAuthFilter: ServiceAuthFilter,
    private val securityProperties: SecurityProperties,
    @Value("\${app.cors.allowed-origins:http://localhost:3000}")
    private val allowedOrigins: String
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
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
                auth
                    // Public endpoints
                    .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
                    // Auth Core Features — public endpoints
                    .requestMatchers("/api/auth/mfa/verify", "/api/auth/mfa/resend").permitAll()
                    .requestMatchers("/api/auth/sso/callback", "/api/auth/sso/providers").permitAll()
                    .requestMatchers("/api/auth/forgot-password").permitAll()
                    .requestMatchers("/api/captcha/challenge").permitAll()
                    .requestMatchers("/.well-known/jwks.json").permitAll()
                    // Anonymous session — public endpoint (create session, no auth)
                    .requestMatchers("/api/v1/auth/anonymous").permitAll()
                    // Anonymous session — authenticated with ROLE_ANONYMOUS
                    .requestMatchers("/api/v1/auth/anonymous/**").hasRole("ANONYMOUS")
                    // Internal service endpoints — requires service JWT (FR-021)
                    .requestMatchers("/api/internal/**").hasRole("SERVICE")
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // Authenticated endpoints
                    .requestMatchers("/api/auth/logout").authenticated()
                    .requestMatchers("/api/auth/sessions/**").authenticated()
                    // Admin-only endpoints
                    .requestMatchers("/api/auth/sessions/*/revoke-all").hasRole("ADMIN")
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
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
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder(securityProperties.password.bcryptStrength)
    }
}

