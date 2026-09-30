package com.ntt.authservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.basecore.autoconfigure.cache.CacheProperties
import com.ntt.basecore.autoconfigure.data.DataProperties
import com.ntt.basecore.autoconfigure.security.cipher.CipherProperties

@SpringBootApplication
@EntityScan(basePackages = [
    "com.ntt.authservice",
    "com.ntt.basecore.autoconfigure.security.entity"
])
@EnableJpaRepositories(basePackages = [
    "com.ntt.authservice",
    "com.ntt.basecore.autoconfigure.security.repository"
])
@EnableConfigurationProperties(
    DataProperties::class,
    CipherProperties::class,
    CacheProperties::class,
    SecurityProperties::class
)
class AuthServiceApplication

fun main(args: Array<String>) {
    runApplication<AuthServiceApplication>(*args)
}
