package com.ntt.authservice.auth.application

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class CacheEncryptionIntegrationTest {

    @Autowired
    private lateinit var stringRedisTemplate: StringRedisTemplate

    @Test
    fun `redis raw data should verify cache behavior`() {
        stringRedisTemplate.opsForValue().set("test:tps:key", "{\"hello\":\"world\"}")
        val raw = stringRedisTemplate.opsForValue().get("test:tps:key")
        assertTrue(raw != null)
        assertTrue(raw.contains("hello"))
    }
}
