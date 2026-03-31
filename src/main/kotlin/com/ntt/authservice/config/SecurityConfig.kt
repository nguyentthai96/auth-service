package com.ntt.authservice.config

import com.ntt.authservice.config.filter.JwtRequestFilter
import com.ntt.authservice.utils.jwt.JwtUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter


/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  20/03/2025, Thursday
 **/
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(private val jwtUtil: JwtUtil) {

    // @Bean PasswordEncoder
    // @Bean UserDetailsService


    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { csrf -> csrf.disable() }
            .authorizeHttpRequests { c ->
                // Public path
                c.requestMatchers(
                    "/",
                    "/home",
                    "/register",
                    "/css/**.css",
                    "/js/**"
                ).permitAll()
                    // Auth require role
                    .anyRequest()
                    .authenticated()
            }
            .formLogin { c: FormLoginConfigurer<HttpSecurity?> ->
                c.loginPage(
                    "/login"
                ).permitAll()
            }
            .sessionManagement { c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authenticationProvider()
            .addFilterBefore(JwtRequestFilter(jwtUtil), UsernamePasswordAuthenticationFilter::class.java)
            .logout { obj: LogoutConfigurer<HttpSecurity?> -> obj.permitAll() }
            .build()
    }

    @Bean
    fun passwordEncoder(): org.springframework.security.crypto.password.PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun authenticationProvider(@Autowired userDetails: UserDetails,
                               @Autowired passwordEncoder: org.springframework.security.crypto.password.PasswordEncoder): AuthenticationProvider {
        val authenticationProvider = DaoAuthenticationProvider()
        authenticationProvider.setUserDetailsService(userDetails)
        authenticationProvider.setPasswordEncoder(passwordEncoder)
        return authenticationProvider
    }

    @Bean
    @Throws(Exception::class)
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager {
        return config.authenticationManager
    }

}