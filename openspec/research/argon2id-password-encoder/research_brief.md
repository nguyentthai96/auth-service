# Research Brief — BCrypt → Argon2id Password Encoder Migration

## 1. Feature Overview

| Field | Value |
|-------|-------|
| **Feature Name** | Argon2id Password Encoder with Bean Auto-Configuration |
| **Input Mode** | Name + Description |
| **Requester** | Performance Team |
| **Priority** | HIGH — CPU-bound bottleneck chiếm 95.2% login latency |

### Mô tả
Thay đổi password hashing algorithm từ **BCrypt (strength=12)** sang **Argon2id** với kiến trúc **Bean auto-configuration** linh hoạt — cho phép cấu hình tùy chỉnh algorithm qua `application.yml` mà không cần thay đổi code.

### Yêu cầu từ user
- Chuyển đổi từ BCrypt → Argon2id
- Tổ chức theo hướng **Bean auto-config** (`@ConditionalOnProperty`)
- Dễ dàng cấu hình tùy chỉnh khi cần (switch algorithm, tune params via YAML)
- Backward compatible: Existing BCrypt hashes vẫn verify được
- Rehash-on-login: Tự động re-encode password khi user login thành công

---

## 2. Research Keywords

1. Argon2id Spring Security PasswordEncoder
2. DelegatingPasswordEncoder BCrypt migration
3. Argon2id OWASP recommended parameters 2024
4. Spring Boot auto-configuration conditional bean PasswordEncoder
5. BouncyCastle Argon2 Java JVM performance
6. Rehash-on-login UserDetailsPasswordService
7. Memory-hard password hashing benchmark Java
8. Argon2PasswordEncoder Spring Security 6.x configuration
9. BCrypt to Argon2id migration strategy production
10. Password encoder auto-configuration pattern Kotlin

---

## 3. Research Queries

| # | Query | Purpose |
|---|-------|---------|
| Q1 | Spring Security Argon2id auto-configuration bean pattern | Architecture approach |
| Q2 | Argon2id vs BCrypt performance JMH benchmark Java | Performance comparison |
| Q3 | OWASP Argon2id recommended parameters 2024 | Security standards |
| Q4 | Spring DelegatingPasswordEncoder migration BCrypt Argon2 | Migration path |
| Q5 | BouncyCastle Argon2PasswordEncoder constructor params | Implementation details |

---

## 4. Current System Analysis

### 4.1 Related Features (Existing)

| Component | File | BCrypt Usage |
|-----------|------|-------------|
| **PasswordEncoder Bean** | [`SecurityConfig.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt#L110-L113) | `BCryptPasswordEncoder(securityProperties.password.bcryptStrength)` — bean definition |
| **Password Config** | [`SecurityProperties.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt#L78-L82) | `PasswordProperties(bcryptStrength=12)` — config-driven |
| **Login/Register** | [`TokenGenerator.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt#L129-L135) | `passwordEncoder.matches()` / `passwordEncoder.encode()` |
| **Password Change** | [`PasswordPolicyService.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt#L57-L86) | `passwordEncoder.matches()` / `passwordEncoder.encode()` |
| **Security YAML** | [`application-security.yml`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/application-security.yml#L58-L61) | `bcrypt-strength: 12` |

### 4.2 Existing Patterns

- **Architecture**: Clean Architecture (CQRS) — `shared/config/` for cross-cutting beans
- **DI Pattern**: Constructor injection via `@Bean` + `@ConfigurationProperties`
- **Config Pattern**: `SecurityProperties` data class → `application-security.yml`
- **All consumers** inject `PasswordEncoder` interface (not BCrypt directly) → **dễ swap**

### 4.3 Tech Stack Constraints

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.x |
| Framework | Spring Boot 3.x + Spring Security 6.x |
| Build | Gradle Kotlin DSL |
| JVM | JDK 25.0.4 (OpenJDK Temurin) |
| BouncyCastle | `bcprov-jdk18on:1.80` (**testImplementation only** — cần promote lên `implementation`) |
| Database | PostgreSQL (password hash column: `password_hash VARCHAR`) |

### 4.4 JMH Benchmark Context (Current BCrypt baseline)

| Metric | Value |
|--------|-------|
| BCrypt hash | 252ms (3.963 ops/s) |
| BCrypt verify | 252ms (3.971 ops/s) |
| BCrypt round-trip | 503ms (1.990 ops/s) |
| **% Login latency** | **95.2%** |
| JWT verify | 0.054ms (negligible) |
| AES encrypt | 3.15µs (negligible) |

### 4.5 Integration Points

- **PasswordEncoder bean** — single definition point (SecurityConfig.kt line 110-113)
- **3 consumers** inject `PasswordEncoder` interface:
  1. `TokenGenerator` (login/register)
  2. `PasswordPolicyService` (change password, history check)
  3. Spring Security authentication manager (internal)
- **Database**: `password_hash` column — currently stores raw BCrypt hash (no `{bcrypt}` prefix)
- **Password History**: `PasswordHistoryEntity.passwordHash` — stores BCrypt hashes for history check

---

## 5. Validation Phase 1

- [x] Directory created: `openspec/research/argon2id-password-encoder/`
- [x] research_brief.md generated
- [x] Keywords identified (10 keywords)
- [x] Current system scanned — sections 4.1-4.5 populated
