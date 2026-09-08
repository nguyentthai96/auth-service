# Research Brief — Argon2id Verify Performance Optimization

## 1. Feature Scope

| Field | Value |
|-------|-------|
| Feature | Tối ưu hiệu năng `argon2id_verify` |
| Input Mode | Benchmark report + codebase analysis |
| Current Metric | ~3.93 ops/s (OWASP: m=65536, t=3, p=1) |
| Target | Cải thiện throughput mà không giảm security |

## 2. Research Keywords

- argon2id verify performance optimization
- argon2 parameter tuning OWASP 2024
- Spring Security Argon2PasswordEncoder BouncyCastle alternative
- password4j argon2 java benchmark
- argon2-jvm native JNA binding performance
- Java FFM API argon2 native call (Project Panama)
- argon2id caching session token avoid repeated verification
- memory-hard password hashing concurrency optimization

## 3. Research Questions

1. Có cách nào tune parameters (m/t/p) mà vẫn đạt OWASP minimum?
2. Có thể thay BouncyCastle bằng native binding (argon2-jvm, Password4j)?
3. Application-level optimization (caching, async, reduce verify calls)?
4. Java 25 FFM API có thể thay JNI/JNA cho native Argon2?
5. DelegatingPasswordEncoder overhead có đáng kể?

## 4. Current System Analysis

### 4.1 Related Features

| Feature | Module | File | Relevance |
|---------|--------|------|-----------|
| Login password verify | `LoginHandler` | `auth/application/command/LoginHandler.kt:105` | ⭐ Primary — gọi `matchesPassword()` |
| Password change verify | `PasswordPolicyService` | `auth/application/PasswordPolicyService.kt:69` | ⭐ Verify old password |
| Password history check | `PasswordPolicyService` | `auth/application/PasswordPolicyService.kt:57` | 🔴 N verify operations per history entries |
| Password upgrade | `PasswordUpgradeService` | `auth/application/PasswordUpgradeService.kt:49` | Encode (not verify) |

### 4.2 Existing Code Patterns

- **Architecture**: Clean Architecture (Hexagonal) — Adapter → Application → Domain
- **Password Encoder**: `DelegatingPasswordEncoder` → `Argon2PasswordEncoder` (BouncyCastle)
- **Concurrency**: `ConcurrencyLimitedPasswordEncoder` wrapping với Semaphore(20)
- **Config**: `SecurityProperties.PasswordProperties.Argon2Properties` — configurable via YAML
- **Parameters hiện tại**: saltLength=16, hashLength=32, parallelism=1, memoryCost=65536, iterations=3

### 4.3 Tech Stack Constraints

- Language: Kotlin + Java 25.0.4 (Temurin LTS)
- Framework: Spring Boot 4.x + Spring Security
- Argon2 impl: `org.springframework.security.crypto.argon2.Argon2PasswordEncoder` → BouncyCastle `bcprov-jdk18on:1.80`
- Build: Gradle 9.6.1 + JMH 1.37
- Database: PostgreSQL
- Cache: Redis + Caffeine L1

### 4.4 Verify Call Points (Critical Path)

```
LoginHandler.handle()
  └── tokenGenerator.matchesPassword(command.password, user.passwordHash.value)
        └── passwordEncoder.matches(rawPassword, encodedPassword)
              └── ConcurrencyLimitedPasswordEncoder.matches()
                    └── semaphore.acquire() + delegate.matches() + semaphore.release()
                          └── DelegatingPasswordEncoder.matches()
                                └── Argon2PasswordEncoder.matches()
                                      └── BouncyCastle Argon2BytesGenerator
```

**Observation**: Mỗi login chỉ gọi verify **1 lần**. Bottleneck thực sự nằm ở algorithm, không phải application logic.

**Exception**: `PasswordPolicyService.checkPasswordHistory()` gọi verify **N lần** (N = historyCount, thường 3-5). Đây là hot path cho change password.
