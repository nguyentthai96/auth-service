# Pre-OpenSpec: jwt-token-validation

> **Type**: MAINTENANCE
> **Flow**: Query
> **Source**: Research Analysis (openspec/research/jwt-token-validation/)
> **Classification Evidence**: keyword `JwtService` → module `auth.application` → file `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` (353 LOC, EXISTS); keyword `TokenBlacklistCacheService` → module `auth.application` → file `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt` (157 LOC, EXISTS); keyword `ClaimValidatorChain` → module `auth.application` → file `src/main/kotlin/com/ntt/authservice/auth/application/ClaimValidatorChain.kt` (42 LOC, EXISTS); keyword `JwtAuthFilter` → module `shared.security` → file `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` (190 LOC, EXISTS); keyword `TokenController` → module `auth.adapter.in.web` → file `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt` (114 LOC, EXISTS); keyword `TokenValidationFailedEvent` → module `auth.domain.event` → file `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenValidationFailedEvent.kt` (24 LOC, EXISTS)
> **Archive**: `openspec/changes/archive/2025-08-26-jwt_token_validation/` (previous iteration)
> **Previous Version**: `openspec/changes/archive/2025-08-26-jwt_token_validation/pre_openspec.md` (classification: EXTEND → now reclassified to MAINTENANCE)
> **Quality Score**: 90/100

## 📋 Feature Summary

Hệ thống JWT token validation trong auth-service đã được implementation đầy đủ production-grade. Tất cả 16 FRs từ iteration trước (EXTEND) đã được code hoàn chỉnh: (1) TokenBlacklistCacheService two-tier cache (Caffeine L1 + Redis L2 + DB fallback) với circuit breaker, (2) ClaimValidatorChain pattern (IssuerClaimValidator, AudienceClaimValidator, TokenTypeClaimValidator) theo Chain of Responsibility, (3) JWKS key rotation dual-key overlap, (4) RFC 7662 introspection với token_type/scope/client_id, (5) clock skew tolerance 60s configurable, (6) TokenValidationFailedEvent + TokenEventRecorder cho audit trail. Scope hiện tại là MAINTENANCE — verify correctness, harden edge cases, ensure test coverage.

| Metric | Giá trị |
|--------|---------|
| Số FR | 16 (Idea: 12, Enriched: 4) |
| Issues | 3 (🔴: 0, 🟡: 2, 🟢: 1) |
| Open Questions | 3 |
| **Quality Score** | **90/100** |

---

## 1. Actors

- **Client Application (End User)**: Gửi HTTP request với Bearer token → trigger validation trên mỗi request
- **Hệ thống (auth-service)**: Xử lý token validation, blacklist check, claim validation, set SecurityContext
- **Internal Microservice**: Gọi introspection endpoint hoặc JWKS endpoint để verify token
- **Service Administrator**: Trigger key rotation, monitor validation metrics
- **Infrastructure (Redis, Caffeine, PostgreSQL)**: L1/L2 cache cho blacklist, DB fallback

## 2. Functional Requirements

### FR-001: Two-tier blacklist cache service [IDEA] — ✅ IMPLEMENTED
- **Actor**: Hệ thống
- **Action**: Hệ thống phải cung cấp `TokenBlacklistCacheService` trong package `auth.application` với method `isBlacklisted(jti: String): Boolean` sử dụng three-level lookup: L1 Caffeine → L2 Redis → DB (`TokenBlacklistRepository.existsByTokenJti()`). Nếu found ở L2/DB → populate L1 cache.
- **Validation**: L1 TTL = 30 seconds (configurable via `SecurityProperties.BlacklistCacheProperties.caffeineTtlSeconds`). Write-through khi token bị blacklist.
- **Implementation**: `TokenBlacklistCacheService.kt` (157 LOC) — Caffeine programmatic cache, `StringRedisTemplate` hasKey, circuit breaker pattern.
- **Status**: [REUSE] — đã hoạt động production

### FR-002: Blacklist cache write-through trên revocation [IDEA] — ✅ IMPLEMENTED
- **Actor**: Hệ thống
- **Action**: Khi token bị blacklist, hệ thống write đồng thời vào L1 Caffeine + L2 Redis + DB. Redis key format: `token:blacklist:{jti}` với TTL = remaining token lifetime.
- **Validation**: Consistency: all tiers nhận entry. Redis unavailable → log warning, continue (fail-safe).
- **Implementation**: `TokenBlacklistCacheService.addToBlacklist(jti, remainingSeconds)` — write L1 + L2 with retry.
- **Status**: [REUSE] — `addToBlacklist()` method trong `TokenBlacklistCacheService.kt`

### FR-003: JwtAuthFilter dùng cache-based blacklist check [IDEA] — ✅ IMPLEMENTED
- **Actor**: Hệ thống
- **Action**: `JwtAuthFilter` inject `TokenBlacklistCacheService` thay vì `TokenBlacklistRepository` trực tiếp. Gọi `tokenBlacklistCacheService.isBlacklisted(jti)`.
- **Validation**: Backward compatible — behavior không thay đổi, chỉ performance improvement.
- **Implementation**: `JwtAuthFilter.kt` L39 constructor inject `TokenBlacklistCacheService`, L80 gọi `isBlacklisted()`.
- **Status**: [REUSE]

### FR-004: Claim validation pipeline (chain of responsibility) [IDEA] — ✅ IMPLEMENTED
- **Actor**: Hệ thống
- **Action**: `ClaimValidator` interface + `ClaimValidatorChain` @Component orchestrates `IssuerClaimValidator`, `AudienceClaimValidator`, `TokenTypeClaimValidator`. Two modes: `validateOrThrow()` (fail-fast) và `validateAll()` (collect-all).
- **Validation**: Validators chạy sequentially. Result chứa `ClaimValidationResult(validatorName, status, reason)`.
- **Implementation**: 6 files trong `auth.application`: `ClaimValidator.kt`, `ClaimValidatorChain.kt`, `IssuerClaimValidator.kt`, `AudienceClaimValidator.kt`, `TokenTypeClaimValidator.kt`, `ClaimValidationResult.kt` + `ClaimValidationException.kt`.
- **Status**: [REUSE]

### FR-005: Clock skew tolerance cấu hình [IDEA] — ✅ IMPLEMENTED
- **Actor**: Hệ thống
- **Action**: Property `clockSkewSeconds: Long = 60` trong `SecurityProperties.JwtProperties`. Applied trong `JwtService.parseToken()` via `.clockSkewSeconds()`.
- **Validation**: Default 60s theo RFC 8725. Configurable qua application.yml.
- **Implementation**: `SecurityProperties.kt` L64 `clockSkewSeconds`, `JwtService.kt` L253/L265/L278 `clockSkewSeconds(clockSkew)` trên mỗi parser.
- **Status**: [REUSE]

### FR-006: JWKS key rotation — dual-key overlap [IDEA] — ✅ IMPLEMENTED
- **Actor**: Service Administrator
- **Action**: `JwtService.getJwks()` supports multiple RSA key pairs. `previousPublicKeyPath` (optional) property trong `SecurityProperties.JwtProperties`. JWKS endpoint trả 2 keys. Validation thử current key → previous key → HMAC fallback.
- **Validation**: During overlap: JWKS 2 keys, validation accept cả 2.
- **Implementation**: `JwtService.kt` L49-61 `previousKeyPair` lazy load, L260-272 fallback parsing, L325-340 `getJwks()` dual-key.
- **Status**: [REUSE]

### FR-007: Introspection endpoint nâng cấp RFC 7662 [IDEA] — ✅ IMPLEMENTED
- **Actor**: Internal Microservice
- **Action**: `TokenController.introspect()` sử dụng `TokenBlacklistCacheService` + `ClaimValidatorChain.validateAll()`. Response chứa `token_type`, `scope`, `client_id` theo RFC 7662.
- **Validation**: Luôn return 200 OK (RFC 7662). active=false cho invalid/expired/revoked/claim-failed.
- **Implementation**: `TokenController.kt` L39-71, `IntrospectionResponse` data class với `@JsonProperty("token_type")`, `scope`, `@JsonProperty("client_id")`.
- **Status**: [REUSE]

### FR-008: JWKS endpoint caching + ETag [IDEA] — ✅ IMPLEMENTED
- **Actor**: Internal Microservice
- **Action**: `TokenController.jwks()` return `Cache-Control: max-age=86400, public`. ETag support dựa trên kid(s) hash cho conditional requests (304 Not Modified).
- **Validation**: Cache-Control header present. ETag changes khi key rotation.
- **Implementation**: `TokenController.kt` L79-108, ETag computed via `TokenHasher.hash(kidString)`.
- **Status**: [REUSE]

### FR-009: JwtAuthFilter integrate ClaimValidatorChain [IDEA] — ✅ IMPLEMENTED
- **Actor**: Hệ thống
- **Action**: `JwtAuthFilter` gọi `claimValidatorChain.validateOrThrow(claims)` sau khi parse token. Nếu fails → log + continue without authentication.
- **Validation**: Existing behavior preserved. Claim validation adds defense-in-depth.
- **Implementation**: `JwtAuthFilter.kt` L92-106, catch `ClaimValidationException`.
- **Status**: [REUSE]

### FR-010: Audience claim configuration [IDEA] — ✅ IMPLEMENTED
- **Actor**: Hệ thống
- **Action**: Property `audience: String = ""` trong `SecurityProperties.JwtProperties`. Khi non-empty, `AudienceClaimValidator` check aud claim. Khi empty → skip (backward compatible).
- **Validation**: Feature flag pattern — disabled by default.
- **Implementation**: `SecurityProperties.kt` L66, `AudienceClaimValidator.kt` L28-30 feature flag check.
- **Status**: [REUSE]

### FR-011: TokenController dùng cache-based blacklist [IDEA] — ✅ IMPLEMENTED
- **Actor**: Hệ thống
- **Action**: `TokenController` inject `TokenBlacklistCacheService`. Dùng `isBlacklisted(jti)` trong introspect().
- **Validation**: Consistent abstraction — controller không trực tiếp gọi JPA repository.
- **Implementation**: `TokenController.kt` L33 inject `TokenBlacklistCacheService`, L48 `isBlacklisted(jti)`.
- **Status**: [REUSE]

### FR-012: Validation failure event recording [IDEA] — ✅ IMPLEMENTED
- **Actor**: Hệ thống
- **Action**: `TokenValidationFailedEvent` domain event với `reason`, `tokenJti`, `ipAddress`, `userAgent`, `validatorName`. Record qua `TokenEventRecorder.recordValidationFailure()`.
- **Validation**: Event recording KHÔNG block request flow — catch and log nếu fail.
- **Implementation**: `TokenValidationFailedEvent.kt` (24 LOC), `ValidationFailureReason.kt` (18 LOC), `TokenEventRecorder.recordValidationFailure()` (L86-105), `JwtAuthFilter.recordValidationFailure()` (L161-179).
- **Status**: [REUSE]

### FR-013: Token generation thêm aud claim [ENRICHED] — ✅ IMPLEMENTED
- **Actor**: Hệ thống
- **Action**: Khi `SecurityProperties.jwt.audience` non-empty, `JwtService.generateAccessToken()` thêm `aud` claim.
- **Validation**: aud claim match configured audience. Chỉ add khi configured — backward compatible.
- **Implementation**: `JwtService.kt` L141-143 `builder.claim("aud", ...)`.
- **Status**: [REUSE]

### FR-014: Structured logging cho validation failures [ENRICHED] — ✅ IMPLEMENTED
- **Actor**: Hệ thống
- **Action**: Log structured validation failure events tại WARN/DEBUG level trong `JwtAuthFilter` với fields: jti, failureType, validator, remoteAddr.
- **Validation**: Log format consistent với service patterns.
- **Implementation**: `JwtAuthFilter.kt` L68 (signature), L82 (blacklist), L97-100 (claim), L155 (generic).
- **Status**: [REUSE]

### FR-015: Timeout/resilience cho Redis blacklist lookup [ENRICHED] — ✅ IMPLEMENTED
- **Actor**: Hệ thống
- **Action**: `TokenBlacklistCacheService` Redis lookup có circuit breaker. Nếu consecutive failures ≥ threshold → skip Redis, fallback to DB. Auto-reset after cooldown.
- **Validation**: Configurable threshold (default 5) và reset time (default 30s) via `SecurityProperties.BlacklistCacheProperties`.
- **Implementation**: `TokenBlacklistCacheService.kt` L120-137 circuit breaker, `SecurityProperties.kt` L237-238 config.
- **Status**: [REUSE]

### FR-016: Retry mechanism cho cache write failures [ENRICHED] — ✅ IMPLEMENTED
- **Actor**: Hệ thống
- **Action**: Write-through operation retry Redis 1 lần khi transient failure. Nếu vẫn fail → log warning, continue (DB already written).
- **Validation**: Retry delay: 100ms. Không retry DB operations.
- **Implementation**: `TokenBlacklistCacheService.addToBlacklist()` L97-104, retry logic with `Thread.sleep(100)`.
- **Status**: [REUSE]

## 3. Non-functional Requirements

| NFR-ID | Loại | Yêu cầu | Target | Status |
|--------|------|---------|--------|--------|
| NFR-001 | Performance | Token validation total time | < 5ms P95 | ⚠️ Cần verify |
| NFR-002 | Performance | Blacklist check time (L1 hit) | < 0.1ms P95 | ✅ Caffeine in-process |
| NFR-003 | Performance | Blacklist check time (L2 Redis hit) | < 1ms P95 | ⚠️ Depends on Redis latency |
| NFR-004 | Performance | Introspection response time | < 50ms P95 | ⚠️ Cần verify |
| NFR-005 | Availability | Key rotation without downtime | 0 seconds | ✅ Dual-key overlap |
| NFR-006 | Security | Revoked token max acceptance window | < 30 seconds (L1 TTL) | ✅ Configurable via caffeineTtlSeconds |
| NFR-007 | Scalability | Concurrent validation throughput | > 10,000 req/s | ⚠️ Cần load test |

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. Tất cả 16 FRs đã implemented với scope rõ ràng — không overlap.

## 5. Enriched Domain Requirements

4 enriched FRs đã được implemented:

### Enriched FRs

- **FR-013** [ENRICHED]: Audience claim trong token generation — ✅ IMPLEMENTED
- **FR-014** [ENRICHED]: Structured logging cho validation failures — ✅ IMPLEMENTED
- **FR-015** [ENRICHED]: Circuit breaker cho Redis blacklist lookup — ✅ IMPLEMENTED
- **FR-016** [ENRICHED]: Retry mechanism cho cache write failures — ✅ IMPLEMENTED

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Redis | L2 distributed blacklist cache | `StringRedisTemplate.hasKey()`, key: `token:blacklist:{jti}` |
| Caffeine | L1 local blacklist cache | Programmatic `Caffeine.newBuilder()`, TTL 30s, max 10K entries |
| PostgreSQL | Blacklist persistence (DB fallback) | `TokenBlacklistRepository.existsByTokenJti()` |
| Kafka | Event publishing (validation failures) | Topic: `iam.token.validation-failed` via `EventService.record()` |

## 6. Assumptions

- ⚠️ Assumption: All 16 FRs verified as implemented via code scan — reason: grep + file read confirmed all classes exist with expected method signatures, annotations, and patterns.
- ⚠️ Assumption: Clock skew 60 seconds là production-appropriate — reason: follows Spring Security default, NTP-synced servers typically <1s skew.
- ⚠️ Assumption: Audience claim validation disabled by default là safe cho current single-service deployment — reason: feature flag pattern, enable when multi-service.
- ⚠️ Assumption: Circuit breaker threshold 5 failures / 30s reset là appropriate — reason: follows common patterns, configurable via properties.

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 24/25 | FR-006: overlap duration not explicitly documented in code |
| Đầy đủ (Completeness) | 22/25 | FR-001: no cache metrics/monitoring exposed; NFRs cần verification |
| Nhất quán (Consistency) | 23/25 | FR-012: event type `iam.token.validation-failed` consistent with `iam.token.issued`, `iam.token.revoked` ✅ |
| Kiểm thử được (Testability) | 21/25 | FR-001, FR-015: cache + circuit breaker scenarios cần integration tests; no test files detected for TokenBlacklistCacheService |
| **Tổng** | **90/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do | Cách cải thiện |
|---|----------|----------|-----|-------|---------------|
| 1 | Clarity | -1 | FR-006 | Key rotation overlap duration chưa document rõ (bao lâu nên maintain 2 keys?) | Thêm documentation cho overlap period recommendation |
| 2 | Completeness | -3 | FR-001, NFR-001-004 | Cache hit rate metrics, validation pipeline latency metrics chưa expose cho monitoring | Thêm Micrometer metrics hoặc structured logging cho observability |
| 3 | Testability | -4 | FR-001, FR-015 | Cần integration tests cho Redis circuit breaker, cache fallback, key rotation scenarios | Thêm Testcontainers Redis integration tests |
| 4 | Consistency | -2 | FR-016 | Retry logic dùng `Thread.sleep(100)` — blocking trong request thread | Cân nhắc async retry hoặc accept tradeoff (chỉ write path, không blocking read) |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | `Thread.sleep(100)` trong `addToBlacklist()` retry — blocking I/O trên request thread | FR-016 | Acceptable vì chỉ trên revocation path (không phải hot path validation), nhưng cần monitor |
| 2 | Risk | 🟡 | No integration test coverage detected cho `TokenBlacklistCacheService` Redis interactions | FR-001, FR-015 | Thêm Testcontainers-based integration tests |
| 3 | Info | 🟢 | All 16 FRs verified implemented — MAINTENANCE chỉ cần verify + harden | ALL | Focus: test coverage, metrics, edge case hardening |

## 9. Open Questions

- OQ-001: Có cần thêm Micrometer metrics cho cache hit rate (L1/L2/L3) và circuit breaker state không?
- OQ-002: Integration test coverage cho `TokenBlacklistCacheService` — cần Testcontainers Redis setup?
- OQ-003: Key rotation overlap period recommendation — nên document rõ là overlap ≥ max token lifetime (15 min access, 7 days refresh)?

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Authentication & Token Management — JWT validation pipeline, token blacklist, JWKS key management, claim validation, event recording

### 10.2 Flow Type
Query — token validation là read-only check trên mỗi request; introspection là query endpoint

### 10.3 Candidate Services
- **auth-service (auth module)**: Core JWT validation logic — `JwtService`, `TokenBlacklistCacheService`, `ClaimValidatorChain`, `ClaimValidator` implementations, `TokenController`, `TokenEventRecorder`
  - Evidence: keyword `JWT`, `Token`, `parseToken`, `blacklist`, `ClaimValidator` → multiple files trong `auth.application`, `auth.adapter.in.web`, `auth.domain.event`, `auth.application.event`
- **auth-service (rbac module)**: Token blacklist persistence — `TokenBlacklistEntity`, `TokenBlacklistRepository`
  - Evidence: keyword `TokenBlacklist` → `rbac.adapter.out.persistence.entity.PermissionEntities.kt`, `rbac.adapter.out.persistence.repository.Repositories.kt`
- **auth-service (shared module)**: Security configuration, filter chain, exception handling
  - Evidence: keyword `SecurityConfig`, `SecurityProperties`, `JwtAuthFilter`, `GlobalExceptionHandler` → `shared.config`, `shared.security`, `shared.exception`

### Detection Evidence
- Keyword: `JwtService` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` (353 LOC)
- Keyword: `TokenBlacklistCacheService` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt` (157 LOC)
- Keyword: `ClaimValidatorChain` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/ClaimValidatorChain.kt` (42 LOC)
- Keyword: `ClaimValidator` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/ClaimValidator.kt` (17 LOC)
- Keyword: `IssuerClaimValidator` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/IssuerClaimValidator.kt` (34 LOC)
- Keyword: `AudienceClaimValidator` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/AudienceClaimValidator.kt` (43 LOC)
- Keyword: `TokenTypeClaimValidator` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/TokenTypeClaimValidator.kt` (36 LOC)
- Keyword: `JwtAuthFilter` → Module: `shared.security` → File: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` (190 LOC)
- Keyword: `TokenController` → Module: `auth.adapter.in.web` → File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt` (114 LOC)
- Keyword: `TokenValidationFailedEvent` → Module: `auth.domain.event` → File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenValidationFailedEvent.kt` (24 LOC)
- Keyword: `ValidationFailureReason` → Module: `auth.domain.event` → File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/ValidationFailureReason.kt` (18 LOC)
- Keyword: `TokenEventRecorder` → Module: `auth.application.event` → File: `src/main/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorder.kt` (105 LOC)
- Keyword: `EventService` → Module: `auth.application.event` → File: `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt` (90 LOC)
- Keyword: `SecurityProperties` → Module: `shared.config` → File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt` (248 LOC)
- Keyword: `IntrospectionRequest/Response` → Module: `auth.adapter.in.web.dto` → File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt` (41 LOC)

### 10.4 External Integrations
- **Redis**: L2 distributed cache cho blacklist — `StringRedisTemplate` via `TokenBlacklistCacheService`
- **Caffeine**: L1 local cache — programmatic `Caffeine.newBuilder()`, NOT Spring Cache
- **PostgreSQL**: `token_blacklist` table — `TokenBlacklistRepository.existsByTokenJti()`
- **Kafka**: Event publishing — `EventService.record()` → outbox → `iam.token.validation-failed` topic

### 10.5 Required Modules
- `auth.application` — JwtService, TokenBlacklistCacheService, ClaimValidator chain (all EXISTING)
- `auth.domain.event` — TokenValidationFailedEvent, ValidationFailureReason (all EXISTING)
- `auth.application.event` — TokenEventRecorder, EventService (all EXISTING)
- `auth.adapter.in.web` — TokenController (EXISTING)
- `auth.adapter.in.web.dto` — IntrospectionRequest/Response (EXISTING)
- `shared.security` — JwtAuthFilter (EXISTING)
- `shared.config` — SecurityProperties (EXISTING)
- `shared.exception` — AuthException hierarchy, AuthErrorCode (EXISTING)

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Client App | Gửi HTTP request với Authorization: Bearer {token} | JwtAuthFilter receives request |
| 2 | — | — | Extract token từ header, parse via JwtService.parseToken() (RS256 current → RS256 previous → HMAC fallback, clock skew 60s) |
| 3 | — | — | Check blacklist via TokenBlacklistCacheService.isBlacklisted(jti): L1 Caffeine → L2 Redis → DB |
| 4 | — | — | Validate claims via ClaimValidatorChain.validateOrThrow(): IssuerClaimValidator → AudienceClaimValidator → TokenTypeClaimValidator |
| 5 | — | — | Extract authorities (roles → ROLE_*, permissions → PERM_*) hoặc ROLE_ANONYMOUS cho type=anonymous |
| 6 | — | — | Set SecurityContext.authentication = UsernamePasswordAuthenticationToken, continue filter chain |
| 7 | — | (On failure) | Record TokenValidationFailedEvent via TokenEventRecorder.recordValidationFailure() |
| 8 | Internal Service | POST /api/auth/introspect {token} | TokenController.introspect() — parse + validateAll() + blacklist check → RFC 7662 response (always 200 OK) |
| 9 | Internal Service | GET /.well-known/jwks.json | TokenController.jwks() — return RSA public key(s) with Cache-Control + ETag (304 support) |

## 12. Traceability Matrix

| FR-ID | Source | Spec Section | Affected Class | Status |
|-------|--------|-------------|---------------|--------|
| FR-001 | Cache Optimization | TokenBlacklistCacheService | `auth/application/TokenBlacklistCacheService.kt` | ✅ Implemented |
| FR-002 | Cache Optimization | TokenBlacklistCacheService.addToBlacklist | `auth/application/TokenBlacklistCacheService.kt` | ✅ Implemented |
| FR-003 | Cache Optimization | JwtAuthFilter dependency | `shared/security/JwtAuthFilter.kt` | ✅ Implemented |
| FR-004 | JWT Validation BCP | ClaimValidator chain | `auth/application/ClaimValidator*.kt`, `ClaimValidatorChain.kt` | ✅ Implemented |
| FR-005 | JWT Validation BCP | SecurityProperties.clockSkewSeconds | `shared/config/SecurityProperties.kt`, `auth/application/JwtService.kt` | ✅ Implemented |
| FR-006 | JWKS Key Rotation | JwtService.previousKeyPair | `auth/application/JwtService.kt`, `shared/config/SecurityProperties.kt` | ✅ Implemented |
| FR-007 | Token Introspection | TokenController.introspect | `auth/adapter/in/web/TokenController.kt`, `auth/adapter/in/web/dto/TokenDtos.kt` | ✅ Implemented |
| FR-008 | Token Introspection | TokenController.jwks ETag | `auth/adapter/in/web/TokenController.kt` | ✅ Implemented |
| FR-009 | JWT Validation BCP | JwtAuthFilter.claimValidatorChain | `shared/security/JwtAuthFilter.kt` | ✅ Implemented |
| FR-010 | JWT Validation BCP | SecurityProperties.audience | `shared/config/SecurityProperties.kt` | ✅ Implemented |
| FR-011 | Token Introspection | TokenController.tokenBlacklistCacheService | `auth/adapter/in/web/TokenController.kt` | ✅ Implemented |
| FR-012 | Event Sourcing | TokenValidationFailedEvent + TokenEventRecorder | `auth/domain/event/TokenValidationFailedEvent.kt`, `auth/application/event/TokenEventRecorder.kt`, `shared/security/JwtAuthFilter.kt` | ✅ Implemented |
| FR-013 | JWT Validation BCP | JwtService.generateAccessToken aud claim | `auth/application/JwtService.kt` | ✅ Implemented |
| FR-014 | Observability | JwtAuthFilter structured logging | `shared/security/JwtAuthFilter.kt` | ✅ Implemented |
| FR-015 | Cache Optimization | TokenBlacklistCacheService circuit breaker | `auth/application/TokenBlacklistCacheService.kt` | ✅ Implemented |
| FR-016 | Cache Optimization | TokenBlacklistCacheService retry | `auth/application/TokenBlacklistCacheService.kt` | ✅ Implemented |

### Change Impact Map (MAINTENANCE)

```
FR-001 → [REUSE] TokenBlacklistCacheService (auth/application/TokenBlacklistCacheService.kt) → verified
FR-002 → [REUSE] TokenBlacklistCacheService.addToBlacklist() → verified
FR-003 → [REUSE] JwtAuthFilter (shared/security/JwtAuthFilter.kt) → verified
FR-004 → [REUSE] ClaimValidator, ClaimValidatorChain (auth/application/) → verified
FR-005 → [REUSE] SecurityProperties.clockSkewSeconds, JwtService.parseToken() → verified
FR-006 → [REUSE] JwtService.previousKeyPair, getJwks() → verified
FR-007 → [REUSE] TokenController.introspect(), IntrospectionResponse → verified
FR-008 → [REUSE] TokenController.jwks() ETag → verified
FR-009 → [REUSE] JwtAuthFilter.claimValidatorChain.validateOrThrow() → verified
FR-010 → [REUSE] SecurityProperties.audience, AudienceClaimValidator → verified
FR-011 → [REUSE] TokenController.tokenBlacklistCacheService → verified
FR-012 → [REUSE] TokenValidationFailedEvent, TokenEventRecorder → verified
FR-013 → [REUSE] JwtService.generateAccessToken() aud claim → verified
FR-014 → [REUSE] JwtAuthFilter structured logging → verified
FR-015 → [REUSE] TokenBlacklistCacheService circuit breaker → verified
FR-016 → [REUSE] TokenBlacklistCacheService retry → verified
```

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations
- **Classification Change**: Previous iteration classified as EXTEND — all 16 FRs now verified as IMPLEMENTED. Reclassified to MAINTENANCE.
- **Code Quality**: Implementation follows Clean Architecture (hexagonal) consistently. Chain of Responsibility pattern for claim validation is well-structured and extensible.
- **Coverage Gaps**: No test files detected for `TokenBlacklistCacheService`, `ClaimValidatorChain`, or individual claim validators. Integration tests with Testcontainers recommended.
- **Blocking I/O**: `Thread.sleep(100)` in `addToBlacklist()` retry is on the revocation path (not validation hot path), acceptable tradeoff.
- **JJWT API Usage**: Correctly using JJWT 0.12.x API: `.clockSkewSeconds()`, `.verifyWith()`, `.parseSignedClaims()`.

### Related Features / Precedents
- **jwt_token_issuance** (archived: `archive/2026-08-25-jwt_token_issuance/`) — TokenIssuedEvent, TokenRevokedEvent, TokenEventRecorder pattern. Pattern reused for TokenValidationFailedEvent.
- **anonymous-login-optimization** (archived: `archive/2026-08-22-anonymous-login-optimization/`) — Redis-based session management, StringRedisTemplate usage reference.
- **auth-core-features** (archived: `archive/2026-08-21-auth-core-features/`) — MFA, SSO, CAPTCHA — SecurityConfig filter chain configuration reference.
- **archive/2025-08-26-jwt_token_validation/** — previous iteration pre_openspec.md (EXTEND classification, all FRs now implemented).

### Integration Notes
- **Redis**: `StringRedisTemplate` used consistently. `TokenBlacklistCacheService` follows same pattern. Key: `token:blacklist:{jti}`.
- **Caffeine**: Programmatic via `Caffeine.newBuilder()` (NOT Spring Cache abstraction). TTL 30s, max 10K entries configurable.
- **Kafka Event Flow**: JwtAuthFilter → TokenEventRecorder.recordValidationFailure() → EventService.record() → EventStorePort.append() + OutboxPort.insert() → OutboxPoller → KafkaEventPublisher → topic `iam.token.validation-failed`.
- **JJWT 0.12.x**: Using `.clockSkewSeconds()`, `.verifyWith()`, `.parseSignedClaims()` API — current stable API.

### Suggested Approach
Since all FRs are implemented, MAINTENANCE scope should focus on:
1. **Test hardening**: Add unit tests for ClaimValidators, integration tests with Testcontainers Redis for TokenBlacklistCacheService circuit breaker scenarios.
2. **Observability**: Add Micrometer metrics — cache_hit_ratio (L1/L2/L3), circuit_breaker_state, validation_pipeline_duration_ms.
3. **Documentation**: Document key rotation procedure (overlap period ≥ max access token lifetime = 15 min).
4. **Code review**: Verify `Thread.sleep(100)` in addToBlacklist() retry is acceptable for production load.

### Context from Confluence Images
N/A — no Confluence source.
