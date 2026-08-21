# Research Brief: JWT Token Validation

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | JWT Token Validation |
| **Ngày tạo** | 2026-08-26 |
| **Input source** | name + enriched description |
| **Input content** | Build production-grade JWT token validation within an Event Sourcing-based Authentication Service — covering RS256/HMAC signing, token introspection (RFC 7662), JWKS endpoint, blacklist checking, two-tier caching (Caffeine L1 + Redis L2), and refresh token rotation. |
| **Người yêu cầu** | System (pipeline) |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

The auth-service already implements JWT token generation and basic validation via `JwtService` and `JwtAuthFilter`. However, the current validation pipeline needs comprehensive research to ensure production-grade hardening: proper claim validation (iss, aud, exp, nbf, jti), blacklist performance at scale (currently DB-backed `TokenBlacklistRepository`), JWKS key rotation strategy, caching of hot validation data (public keys, blacklist lookups), and alignment with RFC 7519 / RFC 7662 / JWT BCP (RFC 8725) best practices.

### 2.2 Mục tiêu (Objectives)
- [x] Objective 1: Evaluate current JWT validation implementation against industry best practices (RFC 7519, RFC 8725)
- [x] Objective 2: Research optimal token blacklist/revocation strategies for high-throughput scenarios
- [x] Objective 3: Identify caching strategies for JWT public keys and blacklist lookups (Caffeine L1 + Redis L2)
- [x] Objective 4: Research JWKS key rotation patterns and kid-based key selection
- [x] Objective 5: Evaluate open source JWT libraries and validation frameworks for Kotlin/Spring Boot
- [x] Objective 6: Design token introspection endpoint aligned with RFC 7662

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| JWT access token validation (RS256 + HMAC fallback) | OAuth2 authorization server implementation |
| Token blacklist checking (JTI-based) | Full OIDC Discovery protocol |
| JWKS endpoint and key rotation | User registration/login flows |
| Token introspection (RFC 7662) | MFA verification logic |
| Refresh token rotation validation | SSO/OAuth2 provider integration |
| Two-tier cache for validation hot data | Frontend token storage strategy |
| Claim validation (iss, aud, exp, nbf, jti) | Password policy management |
| Anonymous token type validation | Session management UI |
| Service-to-service token validation | Account lifecycle management |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `JWT token validation`
- `JWT best practices production`
- `token blacklist revocation`
- `JWKS key rotation`
- `RFC 7519 JWT`
- `RFC 8725 JWT BCP`
- `token introspection RFC 7662`

### 3.2 Secondary Keywords
- `RS256 asymmetric JWT signing`
- `JWT claim validation`
- `token revocation at scale`
- `JWT caching strategy`
- `Spring Security JWT filter`
- `JJWT library Kotlin`

### 3.3 Domain-Specific Terms
- `JTI (JWT ID)`: Unique identifier for each JWT, used for blacklist/revocation tracking
- `kid (Key ID)`: JWT header parameter identifying the key used for signing, enables key rotation
- `JWKS (JSON Web Key Set)`: Endpoint exposing public keys for JWT signature verification
- `Token introspection`: Server-side validation of token state (active/revoked) per RFC 7662
- `Token rotation`: Pattern where refresh token is replaced on each use, preventing reuse
- `Blacklist/Blocklist`: Registry of revoked JTI values, checked during validation
- `Claims`: JWT payload assertions (iss, sub, aud, exp, nbf, iat, jti)

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"JWT token validation best practices production 2024"` | General | High |
| 2 | `"JWT blacklist revocation Redis high throughput"` | Architecture | High |
| 3 | `"JWKS key rotation Spring Boot"` | Integration | High |
| 4 | `"RFC 8725 JWT best current practices"` | Standards | High |
| 5 | `"JWT validation open source Java Kotlin library"` | Open Source | Medium |
| 6 | `"token introspection endpoint RFC 7662 implementation"` | Standards | Medium |
| 7 | `"JWT caching Caffeine Redis two-tier"` | Architecture | Medium |
| 8 | `"JJWT vs Nimbus JOSE vs Auth0 JWT comparison"` | Comparison | Medium |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| JwtService | `auth.application.JwtService` | High | Core JWT generation/parsing — RS256 primary, HMAC fallback. Methods: generateAccessToken, generateRefreshToken, generateMfaToken, generateAnonymousToken, parseToken, getJwks |
| JwtAuthFilter | `shared.security.JwtAuthFilter` | High | Spring Security filter — validates Bearer token, checks blacklist via TokenBlacklistRepository, sets SecurityContext |
| TokenController | `auth.adapter.in.web.TokenController` | High | Introspection endpoint (POST /api/auth/introspect), JWKS endpoint (GET /.well-known/jwks.json), session revocation |
| TokenGenerator | `auth.application.command.TokenGenerator` | High | Shared token generation for Login/Register/Refresh — generates access + refresh tokens, stores hash, records events |
| RefreshTokenHandler | `auth.application.command.RefreshTokenHandler` | High | Token rotation — revokes old refresh token, generates new pair, records TokenRevokedEvent |
| TokenStore (port) | `auth.application.port.out.TokenStore` | High | Outbound port for refresh token persistence — save, find, revoke, blacklist operations |
| TokenBlacklistRepository | `rbac.adapter.out.persistence.repository` | High | JPA repository for blacklisted tokens — existsByTokenJti() used in filter |
| SecurityConfig | `shared.config.SecurityConfig` | High | Configures filter chain, CORS, stateless sessions, endpoint authorization |
| SecurityProperties | `shared.config.SecurityProperties` | High | JWT config: RS256 keys, token TTLs (access 15min, refresh 7d, ceiling 10h), issuer |
| TokenHasher | `auth.domain.service.TokenHasher` | Medium | SHA-256 hashing for refresh token storage |
| AbstractTwoTierCache | `shared.cache.AbstractTwoTierCache` | Medium | L1 (Caffeine) + L2 (Redis) cache base class — available for validation caching |
| PermissionCache (port) | `auth.application.port.out.PermissionCache` | Medium | Multi-tier cache for permissions — pattern reference for token cache |

### 4.2 Existing Code Patterns

**JWT Validation Pipeline (current)**:
1. `JwtAuthFilter.doFilterInternal()` extracts Bearer token from Authorization header
2. Calls `JwtService.parseToken()` — tries RS256 first, falls back to HMAC
3. Checks JTI against `TokenBlacklistRepository.existsByTokenJti()` (DB query)
4. Extracts claims (type, roles, permissions) and sets `SecurityContextHolder`
5. Anonymous tokens (type=anonymous) get `ROLE_ANONYMOUS` authority

**Token Signing Strategy**:
- Primary: RS256 asymmetric (RSA private key signing, public key verification)
- Fallback: HMAC-SHA256 for 7-day migration window
- Key ID (`kid`) header for JWKS key selection
- Key pair loaded lazily from PEM files

**Token Types Supported**:
- Access token (roles, permissions, domains, groups claims)
- Refresh token (type=refresh, minimal claims)
- MFA challenge token (type=mfa, method claim, 5min TTL)
- Anonymous session token (type=anonymous, sessionId subject)

**Error Handling**:
- `TokenExpiredException` (AUTH_003, 401) for expired/invalid tokens
- `GlobalExceptionHandler` maps to RFC 7807 ProblemDetail

**Event Sourcing Integration**:
- `TokenIssuedEvent` recorded via `TokenEventRecorder` on token generation
- `TokenRevokedEvent` recorded on rotation/revocation
- Correlation IDs link rotation chains

### 4.3 Tech Stack Constraints
- Language: Kotlin (JVM)
- Framework: Spring Boot 3.x with Spring Security
- Database: PostgreSQL (via JPA/Hibernate, Flyway migrations)
- Cache: Caffeine (L1 local) + Redis (L2 distributed) — `base-cache-starter`
- JWT Library: JJWT (io.jsonwebtoken) — jjwt-api, jjwt-impl, jjwt-jackson
- Build tool: Gradle (Kotlin DSL)
- Event Sourcing: `eventsourcing-utils` library (custom)
- Base module: `base-core` (base-web-starter, base-data-starter, base-security-starter, base-cache-starter)
- Message broker: Kafka (spring-kafka)
- Resilience: Resilience4j circuit breaker

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| JwtAuthFilter → JwtService | Internal API | `shared.security.JwtAuthFilter` → `auth.application.JwtService` | parseToken() call on every request |
| JwtAuthFilter → TokenBlacklistRepository | DB Query | Filter → JPA Repository | existsByTokenJti() — DB hit per request |
| TokenController → JwtService | Internal API | Controller → Service | introspect() and jwks() endpoints |
| TokenGenerator → JwtService | Internal API | Command → Service | generateAccessToken(), generateRefreshToken() |
| RefreshTokenHandler → TokenStore | Outbound Port | Command → Port | findValidRefreshToken(), revokeToken() |
| SecurityConfig → JwtAuthFilter | Filter Chain | Config → Filter | addFilterBefore(UsernamePasswordAuthenticationFilter) |
| base-cache-starter | Library | `com.ntt:base-cache-starter` | TwoLevelCacheManager, CacheInvalidationPublisher |
| base-security-starter | Library | `com.ntt:base-security-starter` | Base security configurations |
| PermissionChangedConsumer | Kafka | `auth.adapter.in.kafka` | Listens for permission changes to invalidate cache |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: Should token blacklist checking move from DB (JPA) to Redis for sub-millisecond lookups?
- [x] Q2: What is the optimal JWKS key rotation strategy (dual-key, kid-based selection)?
- [x] Q3: How should the two-tier cache (Caffeine + Redis) be applied to token validation hot paths?
- [x] Q4: Is the current RS256 + HMAC fallback approach aligned with RFC 8725 best practices?
- [x] Q5: Should the introspection endpoint support OAuth2 token introspection (RFC 7662) format?
- [x] Q6: How to handle clock skew in distributed JWT validation?
- [x] Q7: What is the blast radius of adding audience (aud) claim validation?

### 5.2 Assumptions cần verify
- [x] A1: Current DB-backed blacklist (TokenBlacklistRepository) is a bottleneck at >1000 req/s — needs verification via load test
- [x] A2: JJWT library handles all RFC 7519 claim validation automatically — needs verification
- [x] A3: base-cache-starter's TwoLevelCacheManager can be reused for blacklist caching — needs API check

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Comprehensive JWT validation landscape | ≥ 5 sources |
| Open source options | JWT libraries and validation frameworks evaluated | ≥ 3 repos evaluated |
| Gap analysis | Current system vs best practices gaps identified | All critical gaps identified |
| Business analysis | All validation use cases documented | All UCs documented |
| Technical spec | Agent-ready for implementation | Complete API + data model + diagrams |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
