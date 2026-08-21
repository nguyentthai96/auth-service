# Pre-OpenSpec: jwt-token-validation

> **Type**: EXTEND
> **Flow**: Query
> **Source**: User Idea (no URD) + Research Analysis
> **Classification Evidence**: keyword `JwtService` → module `auth.application` → file `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`; keyword `JwtAuthFilter` → module `shared.security` → file `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt`; keyword `TokenController` → module `auth.adapter.in.web` → file `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`; keyword `TokenBlacklistRepository` → module `rbac.adapter.out.persistence.repository` → file `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt`; keyword `TokenBlacklistEntity` → module `rbac.adapter.out.persistence.entity` → file `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/PermissionEntities.kt`; keyword `SecurityProperties.JwtProperties` → module `shared.config` → file `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt`
> **Archive**: N/A (related: `archive/2026-08-25-jwt_token_issuance/`)
> **Quality Score**: 82/100

## 📋 Feature Summary

Nâng cấp hệ thống JWT token validation trong auth-service lên production-grade. Hiện tại, `JwtAuthFilter` (96 LOC) thực hiện validation cơ bản: parse token RS256/HMAC, check blacklist qua `TokenBlacklistRepository.existsByTokenJti()` — **query DB trực tiếp trên mỗi request** gây bottleneck tại throughput cao. Feature này bao gồm: (1) migrate blacklist check từ DB sang two-tier cache (Caffeine L1 → Redis L2 → DB fallback), (2) thêm comprehensive claim validation pipeline (iss, aud, exp, nbf, type) theo RFC 8725 BCP, (3) hỗ trợ JWKS key rotation với dual-key overlap strategy, (4) nâng cấp introspection endpoint theo RFC 7662, (5) configurable clock skew tolerance, (6) validation event recording cho audit trail. Architecture theo Clean Architecture (hexagonal ports/adapters) đã thiết lập.

| Metric | Giá trị |
|--------|---------|
| Số FR | 16 (Idea: 12, Enriched: 4) |
| Issues | 5 (🔴: 0, 🟡: 3, 🟢: 2) |
| Open Questions | 4 |
| **Quality Score** | **82/100** |

---

## 1. Actors

- **Client Application (End User)**: Gửi HTTP request với Bearer token → trigger validation trên mỗi request
- **Hệ thống (auth-service)**: Xử lý token validation, blacklist check, claim validation, set SecurityContext
- **Internal Microservice**: Gọi introspection endpoint hoặc JWKS endpoint để verify token
- **Service Administrator**: Trigger key rotation, monitor validation metrics
- **Infrastructure (Redis, Caffeine, PostgreSQL)**: L1/L2 cache cho blacklist, DB fallback

## 2. Functional Requirements

### FR-001: Two-tier blacklist cache service [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tạo `TokenBlacklistCacheService` trong package `auth.application` với method `isBlacklisted(jti: String): Boolean` sử dụng three-level lookup: L1 Caffeine → L2 Redis (SISMEMBER) → DB (`TokenBlacklistRepository.existsByTokenJti()`). Nếu found ở L2/DB → populate L1 cache.
- **Validation**: L1 TTL = 15-30 seconds (configurable). L2 TTL = remaining token lifetime. Write-through khi token bị blacklist.
- **Existing**: `TokenBlacklistRepository.existsByTokenJti()` — DB query per request (bottleneck).
- **Impact**: [ADD] `auth/application/TokenBlacklistCacheService.kt`

### FR-002: Blacklist cache write-through trên revocation [IDEA]
- **Actor**: Hệ thống
- **Action**: Khi token bị blacklist (qua `TokenStore.blacklistToken()`), hệ thống phải write đồng thời vào L1 Caffeine + L2 Redis + DB. Redis key format: `token:blacklist:{jti}` với TTL = remaining token lifetime.
- **Validation**: Consistency: tất cả 3 tiers phải nhận entry. Nếu Redis unavailable → log warning, continue (fail-safe).
- **Existing**: `TokenStorePersistenceAdapter.blacklistToken()` — chỉ write DB. `TokenStore` port interface.
- **Impact**: [MODIFY] `auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt`, [MODIFY] `auth/application/port/out/TokenStore.kt`

### FR-003: JwtAuthFilter chuyển sang cache-based blacklist check [IDEA]
- **Actor**: Hệ thống
- **Action**: Modify `JwtAuthFilter` để inject `TokenBlacklistCacheService` thay vì `TokenBlacklistRepository` trực tiếp. Gọi `tokenBlacklistCacheService.isBlacklisted(jti)` thay cho `tokenBlacklistRepository.existsByTokenJti(jti)`.
- **Validation**: Backward compatible — behavior không thay đổi, chỉ performance improvement.
- **Existing**: `JwtAuthFilter` (L48-51) — hiện gọi `tokenBlacklistRepository.existsByTokenJti(jti)`.
- **Impact**: [MODIFY] `shared/security/JwtAuthFilter.kt`

### FR-004: Claim validation pipeline (chain of responsibility) [IDEA]
- **Actor**: Hệ thống
- **Action**: Tạo `ClaimValidator` interface trong `auth.application` với method `validate(claims: Claims): ClaimValidationResult`. Tạo các implementations: `IssuerClaimValidator` (iss == SecurityProperties.jwt.issuer), `AudienceClaimValidator` (aud contains service identifier — configurable, disabled by default), `TokenTypeClaimValidator` (type ∈ {null, "access", "anonymous"} — reject "mfa", "refresh"). Tạo `ClaimValidatorChain` @Component tập hợp all validators.
- **Validation**: Validators chạy sequentially, fail-fast khi validator đầu tiên reject. Result chứa validator name + reason.
- **Existing**: Issuer validation hiện implicit trong JJWT parser. Audience validation không có. Type validation inline tại JwtAuthFilter L57-77.
- **Impact**: [ADD] `auth/application/ClaimValidator.kt`, [ADD] `auth/application/IssuerClaimValidator.kt`, [ADD] `auth/application/AudienceClaimValidator.kt`, [ADD] `auth/application/TokenTypeClaimValidator.kt`, [ADD] `auth/application/ClaimValidatorChain.kt`

### FR-005: Clock skew tolerance cấu hình [IDEA]
- **Actor**: Hệ thống
- **Action**: Thêm property `clockSkewSeconds: Long = 60` vào `SecurityProperties.JwtProperties`. Áp dụng trong `JwtService.parseToken()` via JJWT `.clock(Clock)` hoặc `allowedClockSkewSeconds()`.
- **Validation**: Default 60s theo recommendation RFC 8725. Configurable qua application.yml.
- **Existing**: `JwtService.parseToken()` (L193-221) — hiện KHÔNG configure clock skew.
- **Impact**: [MODIFY] `shared/config/SecurityProperties.kt`, [MODIFY] `auth/application/JwtService.kt`

### FR-006: JWKS key rotation — dual-key overlap [IDEA]
- **Actor**: Service Administrator
- **Action**: Modify `JwtService.getJwks()` để support multiple RSA key pairs. Thêm property `previousKeyPaths` (optional) vào `SecurityProperties.JwtProperties` cho old key. Khi configured, JWKS endpoint trả 2 keys (both kid values). Token signing dùng current key. Validation thử current key trước, fallback previous key (thay vì HMAC).
- **Validation**: During overlap period: JWKS chứa 2 keys, validation accept cả 2. Post-overlap: remove old key config.
- **Existing**: `JwtService.getJwks()` (L249-261) — chỉ trả 1 key. `keyPair` — singleton lazy load.
- **Impact**: [MODIFY] `auth/application/JwtService.kt`, [MODIFY] `shared/config/SecurityProperties.kt`

### FR-007: Introspection endpoint nâng cấp RFC 7662 [IDEA]
- **Actor**: Internal Microservice
- **Action**: Modify `TokenController.introspect()` để: (1) sử dụng `TokenBlacklistCacheService` thay `TokenBlacklistRepository`, (2) thêm `token_type`, `scope`, `client_id` fields vào `IntrospectionResponse` theo RFC 7662, (3) validate claims qua `ClaimValidatorChain` trước khi return active=true.
- **Validation**: Luôn return 200 OK (RFC 7662). active=false cho invalid/expired/revoked/claim-failed.
- **Existing**: `TokenController.introspect()` (L27-48) — basic implementation. `IntrospectionResponse` (L13-23) — thiếu RFC 7662 fields.
- **Impact**: [MODIFY] `auth/adapter/in/web/TokenController.kt`, [MODIFY] `auth/adapter/in/web/dto/TokenDtos.kt`

### FR-008: JWKS endpoint caching header [IDEA]
- **Actor**: Internal Microservice
- **Action**: Đảm bảo `TokenController.jwks()` return `Cache-Control: max-age=86400, public` header. Thêm ETag support dựa trên kid(s) hash cho conditional requests (304 Not Modified).
- **Validation**: Cache-Control header present. ETag changes khi key rotation xảy ra.
- **Existing**: `TokenController.jwks()` (L50-54) — đã có `CacheControl.maxAge(Duration.ofHours(24)).cachePublic()`.
- **Impact**: [MODIFY] `auth/adapter/in/web/TokenController.kt`

### FR-009: JwtAuthFilter integrate ClaimValidatorChain [IDEA]
- **Actor**: Hệ thống
- **Action**: Modify `JwtAuthFilter` để gọi `claimValidatorChain.validate(claims)` sau khi parse token thành công. Nếu validation fails → log debug, continue without authentication (consistent with hiện tại).
- **Validation**: Existing behavior preserved. Claim validation adds defense-in-depth.
- **Existing**: `JwtAuthFilter.doFilterInternal()` (L30-92) — type check inline, no formal claim validation.
- **Impact**: [MODIFY] `shared/security/JwtAuthFilter.kt`

### FR-010: Audience claim configuration [IDEA]
- **Actor**: Hệ thống
- **Action**: Thêm property `audience: String = ""` vào `SecurityProperties.JwtProperties`. Khi non-empty, `AudienceClaimValidator` kiểm tra aud claim contains audience value. Khi empty → skip audience validation (backward compatible).
- **Validation**: Feature flag pattern — disabled by default. Enable bằng cấu hình `app.security.jwt.audience=auth-service`.
- **Existing**: `SecurityProperties.JwtProperties` — không có audience property.
- **Impact**: [MODIFY] `shared/config/SecurityProperties.kt`

### FR-011: TokenController dùng cache-based blacklist [IDEA]
- **Actor**: Hệ thống
- **Action**: Modify `TokenController` inject `TokenBlacklistCacheService` thay `TokenBlacklistRepository`. Dùng `tokenBlacklistCacheService.isBlacklisted(jti)` trong introspect().
- **Validation**: Consistent abstraction — controller không trực tiếp gọi JPA repository.
- **Existing**: `TokenController` (L22) — inject `TokenBlacklistRepository` trực tiếp.
- **Impact**: [MODIFY] `auth/adapter/in/web/TokenController.kt`

### FR-012: Validation failure event recording [IDEA]
- **Actor**: Hệ thống
- **Action**: Tạo `TokenValidationFailedEvent` trong `auth.domain.event` với: `tokenJti` (String?), `failureReason` (String — "expired", "blacklisted", "signature_invalid", "claim_invalid"), `ipAddress` (String?), `failedAt` (Instant). Record qua `EventService.record()` với topic `iam.token.validation_failed`.
- **Validation**: Event recording KHÔNG block request flow — catch and log nếu fail.
- **Existing**: Không có validation event. Pattern: `TokenEventRecorder` trong `auth.application.event`.
- **Impact**: [ADD] `auth/domain/event/TokenValidationFailedEvent.kt`, [MODIFY] `shared/security/JwtAuthFilter.kt`

### FR-013: Idempotent token issuance thêm aud claim [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Khi `SecurityProperties.jwt.audience` non-empty, `JwtService.generateAccessToken()` phải thêm `aud` claim vào JWT.
- **Validation**: aud claim phải match configured audience. Chỉ add khi configured — backward compatible.
- **Impact**: [MODIFY] `auth/application/JwtService.kt`

### FR-014: Full transaction logging cho validation failures [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải log structured validation failure events tại DEBUG level trong `JwtAuthFilter` với fields: jti, failureType, timestamp, remoteAddr.
- **Validation**: Log format consistent với existing `log.debug("JWT validation failed: {}", e.message)` pattern.
- **Existing**: `JwtAuthFilter` (L89) — basic debug log.
- **Impact**: [MODIFY] `shared/security/JwtAuthFilter.kt`

### FR-015: Timeout handling cho Redis blacklist lookup [ENRICHED]
- **Actor**: Hệ thống
- **Action**: `TokenBlacklistCacheService` Redis lookup phải có configurable timeout (default 500ms). Nếu timeout → fallback to DB. Circuit breaker pattern cho Redis unavailability.
- **Validation**: Timeout configurable. Circuit breaker auto-reset after cooldown.
- **Impact**: [ADD] `auth/application/TokenBlacklistCacheService.kt`

### FR-016: Retry mechanism cho failed cache operations [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Write-through operation (FR-002) phải retry lên Redis tối đa 2 lần khi transient failure. Nếu vẫn fail → log warning, continue (DB already written).
- **Validation**: Retry delay: exponential backoff (100ms, 200ms). Không retry DB operations.
- **Impact**: [ADD] `auth/application/TokenBlacklistCacheService.kt`

## 3. Non-functional Requirements

| NFR-ID | Loại | Yêu cầu | Target |
|--------|------|---------|--------|
| NFR-001 | Performance | Token validation total time | < 2ms P95 |
| NFR-002 | Performance | Blacklist check time (L1 hit) | < 0.1ms P95 |
| NFR-003 | Performance | Blacklist check time (L2 Redis hit) | < 1ms P95 |
| NFR-004 | Performance | Introspection response time | < 50ms P95 |
| NFR-005 | Availability | Key rotation without downtime | 0 seconds |
| NFR-006 | Security | Revoked token max acceptance window | < 30 seconds (L1 TTL) |
| NFR-007 | Scalability | Concurrent validation throughput | > 10,000 req/s |

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. FR-003, FR-009, FR-011 liên quan đến `JwtAuthFilter` và `TokenController` modification nhưng có scope khác nhau:
- FR-003: thay đổi dependency injection (blacklist service)
- FR-009: thêm claim validation pipeline
- FR-011: thay đổi dependency injection trong controller

## 5. Enriched Domain Requirements

Đã bổ sung 4 enriched FRs:

### Enriched FRs

- **FR-013** [ENRICHED]: Thêm aud claim vào token generation khi audience configured — đảm bảo consistency giữa issuance và validation
- **FR-014** [ENRICHED]: Structured logging cho validation failures — cần cho audit trail và monitoring
- **FR-015** [ENRICHED]: Timeout handling cho Redis lookup — critical path performance protection
- **FR-016** [ENRICHED]: Retry mechanism cho cache write failures — resilience cho distributed cache

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Redis | L2 distributed blacklist cache | SISMEMBER operation, key: `token:blacklist:{jti}` |
| Caffeine | L1 local blacklist cache | In-process, TTL 15-30s |
| PostgreSQL | Blacklist persistence (DB fallback) | `token_blacklist` table via `TokenBlacklistRepository` |
| Kafka | Event publishing (validation failures) | Topic: `iam.token.validation_failed` |

## 6. Assumptions

- ⚠️ Assumption: DB-backed blacklist is a bottleneck at >500 req/s — reason: PostgreSQL indexed query ~1-5ms vs Redis SISMEMBER ~0.1ms
- ⚠️ Assumption: Clock skew 60 seconds là acceptable — reason: follows Spring Security default, NTP-synced servers typically <1s skew
- ⚠️ Assumption: `base-cache-starter` TwoLevelCacheManager available nhưng API cụ thể chưa verify — có thể cần custom implementation cho blacklist-specific use case
- ⚠️ Assumption: Audience claim validation disabled by default là safe — reason: current system single-service, multi-service sẽ enable later

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 22/25 | FR-006: key rotation flow chưa detail overlap duration configuration |
| Đầy đủ (Completeness) | 20/25 | FR-004: claim validation chain chưa specify ClaimValidationResult data class |
| Nhất quán (Consistency) | 22/25 | FR-012: validation event type naming ("iam.token.validation_failed") cần align với existing event naming conventions |
| Kiểm thử được (Testability) | 18/25 | FR-001, FR-015: cache fallback scenarios khó test unitarily without Redis mock; FR-006: dual-key rotation cần integration test |
| **Tổng** | **82/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -3 | FR-006 | "dual-key overlap strategy" — URD mô tả chung, thiếu cụ thể overlap duration config property name | Thêm property `keyRotation.overlapDays: Int = 8` |
| 2 | Completeness | -5 | FR-004 | Claim validation pipeline thiếu data class `ClaimValidationResult` definition, thiếu enum `ClaimValidationStatus` | Định nghĩa result/status classes trong design |
| 3 | Consistency | -3 | FR-012 | Event type "iam.token.validation_failed" — pattern khác với existing "iam.token.issued" / "iam.token.revoked" (dùng underscore vs dot) | Đổi thành "iam.token.validation-failed" hoặc keep consistent |
| 4 | Testability | -7 | FR-001, FR-015, FR-006 | Cache fallback logic, Redis timeout, key rotation cần integration tests với Redis container | Thêm Testcontainers setup cho Redis; mock Caffeine cache |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | Redis unavailability trên critical path (JwtAuthFilter chạy mỗi request) | FR-001, FR-003 | Circuit breaker + L1 cache warmup + DB fallback |
| 2 | Risk | 🟡 | L1 cache inconsistency window 15-30s — revoked token có thể accepted trong window này | FR-001, FR-006 (NFR-006) | Acceptable tradeoff — document SLA |
| 3 | Risk | 🟡 | Key rotation complexity — nếu overlap period quá ngắn, tokens signed by old key bị reject | FR-006 | Overlap period > max token lifetime (7d for refresh) |
| 4 | Info | 🟢 | `base-cache-starter` TwoLevelCacheManager API chưa verify — có thể cần custom implementation | FR-001 | Verify API trước implementation, fallback to manual Caffeine+Redis |
| 5 | Info | 🟢 | Audience validation disabled by default — không có immediate risk nhưng cần enable khi multi-service | FR-010 | Feature flag approach là safe |

## 9. Open Questions

- OQ-001: L1 cache TTL nên 15s hay 30s? Tradeoff: consistency vs performance (shorter TTL = more Redis calls, lower inconsistency window)
- OQ-002: Audience claim validation nên mandatory immediately hay phased in with feature flag?
- OQ-003: `base-cache-starter` TwoLevelCacheManager có hỗ trợ SET-based operation (SISMEMBER) cho blacklist hay chỉ key-value? Nếu không → custom implementation
- OQ-004: Validation failure events nên record ở level nào? Tất cả failures (including expired tokens) hay chỉ suspicious patterns (blacklisted, signature invalid)?

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Authentication & Token Management — JWT validation pipeline, token blacklist, JWKS key management

### 10.2 Flow Type
Query — token validation là read-only check trên mỗi request; introspection là query endpoint

### 10.3 Candidate Services
- **auth-service (auth module)**: Core JWT validation logic — `JwtService`, `JwtAuthFilter`, `TokenController`, `TokenStore`, `TokenEventRecorder`
  - Evidence: keyword `JWT`, `Token`, `parseToken`, `blacklist` → multiple files trong `auth.application`, `auth.adapter.in.web`, `shared.security`
- **auth-service (rbac module)**: Token blacklist persistence — `TokenBlacklistEntity`, `TokenBlacklistRepository`
  - Evidence: keyword `TokenBlacklist` → `rbac.adapter.out.persistence.entity.PermissionEntities.kt`, `rbac.adapter.out.persistence.repository.Repositories.kt`
- **auth-service (shared module)**: Security configuration, filter chain, exception handling
  - Evidence: keyword `SecurityConfig`, `SecurityProperties`, `GlobalExceptionHandler` → `shared.config`, `shared.security`, `shared.exception`

### Detection Evidence
- Keyword: `JwtService` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`
- Keyword: `JwtAuthFilter` → Module: `shared.security` → File: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt`
- Keyword: `TokenController` → Module: `auth.adapter.in.web` → File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`
- Keyword: `TokenBlacklistRepository` → Module: `rbac.adapter.out.persistence.repository` → File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt`
- Keyword: `TokenBlacklistEntity` → Module: `rbac.adapter.out.persistence.entity` → File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/PermissionEntities.kt`
- Keyword: `SecurityProperties` → Module: `shared.config` → File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt`
- Keyword: `TokenEventRecorder` → Module: `auth.application.event` → File: `src/main/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorder.kt`
- Keyword: `EventService` → Module: `auth.application.event` → File: `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`

### 10.4 External Integrations
- **Redis**: L2 distributed cache cho blacklist (SISMEMBER), existing `StringRedisTemplate` usage throughout project
- **Caffeine**: L1 local cache — `base-cache-starter` available, `TwoLevelCacheManager` configured in `SecurityConfig.kt`
- **PostgreSQL**: `token_blacklist` table — existing `TokenBlacklistEntity` + `TokenBlacklistRepository`
- **Kafka**: Event publishing — existing `KafkaEventPublisher`, `OutboxPoller`, topic routing via `EventService.record()`

### 10.5 Required Modules
- `auth.application` — JwtService, TokenBlacklistCacheService (new), ClaimValidator chain (new)
- `auth.domain.event` — TokenValidationFailedEvent (new)
- `auth.adapter.in.web` — TokenController (modify)
- `auth.adapter.in.web.dto` — IntrospectionResponse (modify)
- `shared.security` — JwtAuthFilter (modify)
- `shared.config` — SecurityProperties (modify)
- `rbac.adapter.out.persistence` — TokenBlacklistEntity, TokenBlacklistRepository (read-only usage)

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Client App | Gửi HTTP request với Authorization: Bearer {token} | JwtAuthFilter receives request |
| 2 | — | — | Extract token từ header, parse via JwtService.parseToken() (RS256 → HMAC fallback) |
| 3 | — | — | Validate claims via ClaimValidatorChain (iss, aud, exp, nbf, type) |
| 4 | — | — | Check blacklist via TokenBlacklistCacheService (L1 → L2 → DB) |
| 5 | — | — | Extract authorities (roles/permissions hoặc ROLE_ANONYMOUS) |
| 6 | — | — | Set SecurityContext, continue filter chain |
| 7 | Internal Service | POST /api/auth/introspect {token} | TokenController.introspect() — parse + validate + blacklist check → RFC 7662 response |
| 8 | Internal Service | GET /.well-known/jwks.json | TokenController.jwks() — return RSA public key(s) with Cache-Control |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | Cache Optimization | TBD | TokenBlacklistCacheService (NEW) | Pending |
| FR-002 | Cache Optimization | TBD | TokenStorePersistenceAdapter, TokenStore | Pending |
| FR-003 | Cache Optimization | TBD | JwtAuthFilter | Pending |
| FR-004 | JWT Validation BCP | TBD | ClaimValidator*, ClaimValidatorChain (NEW) | Pending |
| FR-005 | JWT Validation BCP | TBD | SecurityProperties, JwtService | Pending |
| FR-006 | JWKS Key Rotation | TBD | JwtService, SecurityProperties | Pending |
| FR-007 | Token Introspection | TBD | TokenController, IntrospectionResponse | Pending |
| FR-008 | Token Introspection | TBD | TokenController | Pending |
| FR-009 | JWT Validation BCP | TBD | JwtAuthFilter | Pending |
| FR-010 | JWT Validation BCP | TBD | SecurityProperties | Pending |
| FR-011 | Token Introspection | TBD | TokenController | Pending |
| FR-012 | Event Sourcing | TBD | TokenValidationFailedEvent (NEW), JwtAuthFilter | Pending |
| FR-013 | JWT Validation BCP | TBD | JwtService | Pending |
| FR-014 | Observability | TBD | JwtAuthFilter | Pending |
| FR-015 | Cache Optimization | TBD | TokenBlacklistCacheService (NEW) | Pending |
| FR-016 | Cache Optimization | TBD | TokenBlacklistCacheService (NEW) | Pending |

### Change Impact Map (EXTEND)

```
FR-001 → [ADD] TokenBlacklistCacheService (auth/application/TokenBlacklistCacheService.kt) → internal only
FR-002 → [MODIFY] TokenStorePersistenceAdapter (auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt) → TokenStore port
FR-003 → [MODIFY] JwtAuthFilter (shared/security/JwtAuthFilter.kt) → every request
FR-004 → [ADD] ClaimValidator, IssuerClaimValidator, AudienceClaimValidator, TokenTypeClaimValidator, ClaimValidatorChain (auth/application/) → internal only
FR-005 → [MODIFY] SecurityProperties (shared/config/SecurityProperties.kt) → configuration
FR-005 → [MODIFY] JwtService (auth/application/JwtService.kt) → parseToken()
FR-006 → [MODIFY] JwtService (auth/application/JwtService.kt) → getJwks(), key loading
FR-006 → [MODIFY] SecurityProperties (shared/config/SecurityProperties.kt) → key rotation config
FR-007 → [MODIFY] TokenController (auth/adapter/in/web/TokenController.kt) → POST /api/auth/introspect
FR-007 → [MODIFY] IntrospectionResponse (auth/adapter/in/web/dto/TokenDtos.kt) → RFC 7662 fields
FR-008 → [MODIFY] TokenController (auth/adapter/in/web/TokenController.kt) → GET /.well-known/jwks.json
FR-009 → [MODIFY] JwtAuthFilter (shared/security/JwtAuthFilter.kt) → claim validation
FR-010 → [MODIFY] SecurityProperties (shared/config/SecurityProperties.kt) → audience property
FR-011 → [MODIFY] TokenController (auth/adapter/in/web/TokenController.kt) → dependency change
FR-012 → [ADD] TokenValidationFailedEvent (auth/domain/event/TokenValidationFailedEvent.kt) → event sourcing
FR-012 → [MODIFY] JwtAuthFilter (shared/security/JwtAuthFilter.kt) → event recording
FR-013 → [MODIFY] JwtService (auth/application/JwtService.kt) → aud claim in generation
FR-014 → [MODIFY] JwtAuthFilter (shared/security/JwtAuthFilter.kt) → structured logging
FR-015 → [ADD] TokenBlacklistCacheService (auth/application/TokenBlacklistCacheService.kt) → timeout config
FR-016 → [ADD] TokenBlacklistCacheService (auth/application/TokenBlacklistCacheService.kt) → retry logic
```

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations
- Feature này có complexity **MEDIUM-HIGH** — chủ yếu do cross-cutting concern: JwtAuthFilter nằm trên critical path (every request), nên mọi thay đổi phải zero-regression.
- Codebase đã có architecture rất clean (hexagonal ports/adapters, CQRS command handlers) — extend pattern dễ follow.
- Research phase (openspec/research/jwt_token_validation/) đã hoàn thành comprehensive analysis: 4 OSS evaluated, 6 gaps identified, 6 use cases documented, 7 classes to create/modify.
- `TokenEventRecorder` pattern (from jwt_token_issuance feature) là template tốt cho validation event recording.

### Related Features / Precedents
- **jwt_token_issuance** (archived: `archive/2026-08-25-jwt_token_issuance/`) — related feature implementing TokenIssuedEvent, TokenRevokedEvent, TokenEventRecorder. Pattern reusable cho TokenValidationFailedEvent.
- **anonymous-login-optimization** (archived: `archive/2026-08-22-anonymous-login-optimization/`) — Redis-based session management pattern, StringRedisTemplate usage reference.
- **auth-core-features** (archived: `archive/2026-08-21-auth-core-features/`) — MFA, SSO, CAPTCHA features — SecurityConfig filter chain configuration reference.

### Integration Notes
- Redis integration pattern: project uses `StringRedisTemplate` extensively (>12 injection points). TokenBlacklistCacheService should follow same pattern.
- `base-cache-starter` provides `TwoLevelCacheManager` configured in `SecurityConfig.kt` — nhưng hiện tại dùng `ConcurrentMapCacheManager` cho L1 (not Caffeine). May need Caffeine dependency hoặc custom implementation.
- Kafka event publishing: existing `EventService.record()` → `EventStorePort.append()` + `OutboxPort.insert()` → `OutboxPoller` → `KafkaEventPublisher`. Pattern proven for TokenIssuedEvent/TokenRevokedEvent.

### Suggested Approach
1. **Start with FR-001 (TokenBlacklistCacheService)** — core infrastructure change, enables all downstream FRs.
2. **FR-003/FR-011 (JwtAuthFilter/TokenController migration)** — swap to cache-based blacklist.
3. **FR-004/FR-009 (Claim validation chain)** — add defense-in-depth.
4. **FR-005/FR-010/FR-013 (Clock skew + audience)** — configuration + token generation alignment.
5. **FR-006 (Key rotation)** — lower priority, can be phased.
6. **FR-007/FR-008 (Introspection upgrade)** — after core validation improved.
7. **FR-012/FR-014 (Event recording + logging)** — observability enhancement last.

### Context from Confluence Images
N/A — no Confluence source.
