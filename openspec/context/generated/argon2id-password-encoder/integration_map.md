# Integration Map

_Generated: 2026-09-08_

## PostgreSQL (Database)

- Client: Spring Data JPA (UserRepository, PasswordHistoryRepository)
- Protocol: JDBC → PostgreSQL
- Request: JPA Entity operations (findByUsername, save)
- Response: UserEntity, PasswordHistoryEntity

## Redis (Cache)

- Client: StringRedisTemplate — `auth/application/TokenBlacklistCacheService.kt`
- Protocol: Redis protocol (Lettuce driver)
- Purpose: Token blacklist, rate limiting, MFA OTP storage
- Note: NOT directly used by PasswordEncoder — but concurrent hash operations may compete for JVM memory

## BouncyCastle (Cryptographic Library)

- Client: Spring Security Argon2PasswordEncoder (wraps BouncyCastle Argon2BytesGenerator)
- Protocol: In-process (JNI-free, pure Java)
- Dependency: `org.bouncycastle:bcprov-jdk18on:1.80` (currently testImplementation → promote to implementation)

## NOT DETECTED

- REST/HTTP client integrations (password hashing is purely internal)
- Message queue integrations
- External authentication providers (SSO is separate concern)
