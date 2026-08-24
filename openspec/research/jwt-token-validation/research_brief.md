# Research Brief: JWT Token Validation

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | JWT Token Validation |
| **Ngày tạo** | 2025-01-20 |
| **Input source** | name |
| **Input content** | `jwt-token-validation` — Feature name from pipeline |
| **Người yêu cầu** | Pipeline (auth-service) |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

Auth-service cần một hệ thống JWT token validation mạnh mẽ, bao gồm: signature verification (RS256 + HMAC fallback), claim validation chain (issuer, audience, token type), token blacklist checking (L1 Caffeine + L2 Redis + DB), key rotation support, JWKS endpoint, và RFC 7662 token introspection. Hệ thống hiện tại đã có implementation cơ bản nhưng cần nghiên cứu best practices, so sánh với các giải pháp open source, và đánh giá mức độ hoàn thiện so với chuẩn công nghiệp (RFC 7519, RFC 8725 JWT BCP).

### 2.2 Mục tiêu (Objectives)

- [x] Objective 1: Đánh giá implementation hiện tại so với JWT best practices (RFC 8725)
- [x] Objective 2: So sánh với các thư viện/framework JWT validation phổ biến
- [x] Objective 3: Xác định gaps trong security, performance, và maintainability
- [x] Objective 4: Đề xuất cải tiến hoặc xác nhận hướng đi hiện tại

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| JWT signature verification (RS256, HMAC) | OAuth2 authorization server implementation |
| Claim validation chain (iss, aud, type) | User management / registration flows |
| Token blacklist / revocation | Token generation logic (covered separately) |
| Key rotation / JWKS endpoint | MFA token specifics |
| RFC 7662 token introspection | SSO integration details |
| Clock skew tolerance | Password policy |
| Validation failure event recording | Anonymous session management |
| Service-to-service token validation | Captcha verification |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `JWT token validation`
- `JWT claim validation`
- `RS256 signature verification`
- `token blacklist revocation`
- `JWKS endpoint key rotation`
- `RFC 7519 JWT`
- `RFC 8725 JWT best current practices`

### 3.2 Secondary Keywords
- `JWT security vulnerabilities`
- `token introspection RFC 7662`
- `claim validation chain of responsibility`
- `clock skew tolerance`
- `dual key rotation overlap`
- `Caffeine Redis cache tiered`

### 3.3 Domain-Specific Terms
- `JTI (JWT ID)`: Unique identifier per token, used for revocation/blacklist tracking
- `kid (Key ID)`: Header parameter identifying which key signed the token, critical for key rotation
- `Clock skew`: Time difference between token issuer and validator clocks, tolerated via `clockSkewSeconds`
- `Token introspection`: RFC 7662 — endpoint allowing resource servers to query token status
- `JWKS (JSON Web Key Set)`: RFC 7517 — public key discovery endpoint for RS256 verification
- `Token blacklist`: Maintaining a list of revoked token JTIs before natural expiration

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"JWT validation best practices security 2024"` | General | High |
| 2 | `"JJWT library Spring Boot RS256 validation"` | Open Source | High |
| 3 | `"JWT claim validation chain pattern"` | Architecture | Medium |
| 4 | `"token blacklist cache Redis performance"` | Architecture | Medium |
| 5 | `"JWT key rotation JWKS best practices"` | Security | High |
| 6 | `"RFC 8725 JWT best current practices"` | Standards | High |
| 7 | `"Spring Security JWT filter validation"` | Framework | Medium |
| 8 | `"JWT introspection RFC 7662 implementation"` | Standards | Medium |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| JwtService | `auth.application.JwtService` | High | Core JWT generation + parsing with RS256/HMAC dual signing |
| ClaimValidatorChain | `auth.application.ClaimValidatorChain` | High | Chain of responsibility for claim validation (issuer, audience, type) |
| JwtAuthFilter | `shared.security.JwtAuthFilter` | High | OncePerRequestFilter — PEP integrating blacklist, claim validation, SecurityContext |
| TokenBlacklistCacheService | `auth.application.TokenBlacklistCacheService` | High | 3-tier cache: Caffeine L1 → Redis L2 → DB L3 with circuit breaker |
| TokenController | `auth.adapter.in.web.TokenController` | High | Introspection (RFC 7662), JWKS, session revocation endpoints |
| TokenEventRecorder | `auth.application.event.TokenEventRecorder` | Medium | Validation failure event recording for security audit |
| ServiceTokenService | `auth.application.ServiceTokenService` | Medium | Service-to-service token generation + validation |
| ServiceAuthFilter | `auth.adapter.in.web.filter.ServiceAuthFilter` | Medium | Validates service-to-service tokens on internal API paths |

### 4.2 Existing Code Patterns

**Architecture**: Clean Architecture (Hexagonal) — `domain` → `application` → `adapter` layers.

**Claim Validation Pattern**: Chain of Responsibility via `ClaimValidator` interface.
- `IssuerClaimValidator` — validates `iss` claim against configured issuer
- `AudienceClaimValidator` — validates `aud` claim (feature-flagged, disabled when blank)
- `TokenTypeClaimValidator` — prevents refresh/mfa tokens from being used as access tokens
- `ClaimValidatorChain` — two modes: `validateOrThrow()` (fail-fast for filter) and `validateAll()` (collect-all for introspection)

**Token Parsing Strategy**: Cascading fallback (FR-006):
1. Current RS256 key pair → 2. Previous RS256 key (rotation overlap) → 3. HMAC legacy key

**Blacklist Architecture**: Three-tier cache with circuit breaker (FR-001, FR-015):
- L1: Caffeine in-process cache (~nanoseconds)
- L2: Redis distributed cache (~milliseconds) with circuit breaker
- L3: PostgreSQL DB (source of truth)

**Event Recording**: Validation failures recorded as domain events → Kafka topic `iam.token.validation-failed` with categorized reasons (BLACKLISTED, SIGNATURE_INVALID, AUDIENCE_MISMATCH, TYPE_REJECTED).

**JWKS Endpoint**: `/.well-known/jwks.json` with ETag support for conditional requests, serving both current and previous RSA public keys during key rotation.

### 4.3 Tech Stack Constraints
- Language: Kotlin (JVM)
- Framework: Spring Boot 3.x with Spring Security
- Database: PostgreSQL (via Spring Data JPA + Flyway)
- Cache: Caffeine (L1) + Redis (L2)
- JWT Library: JJWT (io.jsonwebtoken) — API + impl + jackson modules
- Build tool: Gradle (Kotlin DSL)
- Message Queue: Kafka (for domain events)
- Testing: JUnit 5 + Mockito-Kotlin + H2 in-memory DB

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| JwtAuthFilter (Spring Security filter chain) | Filter | `shared.security.JwtAuthFilter` | OncePerRequestFilter, sets SecurityContext |
| TokenBlacklistRepository | Table | `rbac.adapter.out.persistence.repository.TokenBlacklistRepository` | DB source of truth for blacklisted JTIs |
| SecurityProperties.JwtProperties | Config | `shared.config.SecurityProperties` | All JWT config: key paths, TTLs, issuer, audience, clock skew |
| TokenEventRecorder → EventService | Event | `auth.application.event.TokenEventRecorder` | Publishes validation failure events to Kafka |
| ServiceAuthFilter | Filter | `auth.adapter.in.web.filter.ServiceAuthFilter` | Validates service tokens on `/internal/**` paths |
| TokenController.introspect | API | `auth.adapter.in.web.TokenController` | RFC 7662 introspection endpoint |
| JWKS endpoint | API | `auth.adapter.in.web.TokenController` | `/.well-known/jwks.json` with ETag |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: Current implementation so với RFC 8725 (JWT BCP) đạt bao nhiêu % compliance?
- [x] Q2: Claim validation chain pattern có phải best practice không? So với các approach khác?
- [x] Q3: Three-tier blacklist cache (Caffeine + Redis + DB) có phải overkill hay industry standard?
- [x] Q4: Key rotation strategy (dual RS256 + HMAC legacy) có đủ robust?
- [x] Q5: JWKS endpoint caching strategy (ETag + 24h max-age) có hợp lý?
- [x] Q6: Có nên migrate sang Spring Security Resource Server built-in JWT validation không?

### 5.2 Assumptions cần verify
- [x] A1: JJWT library là lựa chọn tốt nhất cho Kotlin/Spring Boot — cần verify alternatives (Nimbus JOSE+JWT, Auth0 java-jwt)
- [x] A2: Chain of Responsibility pattern cho claim validation — cần verify extensibility
- [x] A3: Circuit breaker cho Redis blacklist lookup — threshold 5 failures, reset 30s — cần verify thresholds

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Đánh giá đầy đủ các khía cạnh JWT validation | ≥ 5 sources |
| Open source options | So sánh với thư viện JWT và framework | ≥ 3 repos evaluated |
| Gap analysis | Xác định gaps giữa current system và best practices | All critical gaps identified |
| Business analysis | Đặc tả use cases cho JWT validation | All UCs documented |
| Technical spec | Architecture decisions cho improvements | Agent-ready for implementation |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
