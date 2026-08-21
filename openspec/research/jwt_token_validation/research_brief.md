# Research Brief: JWT Token Validation

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | JWT Token Validation |
| **Ngày tạo** | 2025-07-11 |
| **Input source** | Name + enriched description |
| **Input content** | Build production-grade JWT token validation subsystem within the auth-service, covering signature verification (RS256 primary, HMAC fallback), claims validation, token introspection (RFC 7662), blacklist checking, JWKS endpoint, and inter-service token validation. |
| **Người yêu cầu** | Pipeline (auto) |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

The auth-service already has a `JwtService` class that handles JWT generation and parsing using the `jjwt` (io.jsonwebtoken) library. It supports RS256 asymmetric signing (primary) with HMAC-SHA256 fallback for legacy migration. However, the current validation layer needs formalization and hardening:

1. **Token validation is spread across multiple components** — `JwtAuthFilter`, `ServiceAuthFilter`, `TokenController` (introspection), and individual service methods all perform token parsing/validation independently.
2. **Blacklist checking** is done in `JwtAuthFilter` via direct `TokenBlacklistRepository` calls, but not consistently in all validation paths.
3. **Claims validation** (issuer, audience, expiration, type) is partially implemented — type checking exists for MFA and anonymous tokens but not systematically enforced.
4. **JWKS endpoint** exists (`/.well-known/jwks.json`) but key rotation strategy is not fully documented or automated.
5. **Inter-service token validation** uses a separate `ServiceTokenService` with HMAC-based signing, creating a dual-key management concern.

The goal is to research best practices for JWT token validation, evaluate existing open-source solutions, and formalize the validation architecture within the Event Sourcing context of this auth-service.

### 2.2 Mục tiêu (Objectives)

- [x] Objective 1: Research JWT validation best practices (RFC 7519, RFC 7662, OWASP guidelines)
- [x] Objective 2: Evaluate open-source JWT libraries and their validation capabilities against project needs
- [x] Objective 3: Define a unified validation pipeline (signature → claims → blacklist → context-specific checks)
- [x] Objective 4: Research token revocation strategies (blacklist, short-lived tokens, token binding)
- [x] Objective 5: Analyze JWKS key rotation patterns for zero-downtime key changes
- [x] Objective 6: Evaluate caching strategies for validation metadata (public keys, blacklist, permissions)

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| JWT signature verification (RS256, HMAC fallback) | OAuth2 authorization code flow implementation |
| Claims validation pipeline (iss, exp, iat, jti, type) | User registration flow |
| Token introspection endpoint (RFC 7662) | Password management |
| Token blacklist checking (JTI-based) | MFA flow implementation |
| JWKS endpoint and key rotation | SSO provider integration |
| Inter-service token validation | E2EE key exchange |
| Token type discrimination (access, refresh, mfa, anonymous, service) | Anonymous session lifecycle |
| Cache optimization for validation (Caffeine L1 + Redis L2) | Event sourcing core infrastructure |
| Event sourcing for token lifecycle events | RBAC/PBAC policy engine |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `JWT token validation`
- `JSON Web Token verification`
- `RS256 signature verification`
- `JWKS endpoint`
- `token introspection RFC 7662`
- `token blacklist`
- `token revocation`

### 3.2 Secondary Keywords
- `JWT security best practices`
- `asymmetric JWT signing`
- `key rotation strategy`
- `token binding`
- `claim validation`
- `jjwt library`
- `nimbus-jose-jwt`
- `Spring Security JWT`

### 3.3 Domain-Specific Terms
- `JTI (JWT ID)`: Unique identifier for each token, used for blacklist/revocation tracking
- `JWKS (JSON Web Key Set)`: Standard format for publishing public keys used for JWT signature verification
- `Token Introspection (RFC 7662)`: Protocol for querying the authorization server about the state of a token
- `Token Rotation`: Strategy where each refresh cycle invalidates the previous token and issues a new one
- `Claims`: Assertions made about a subject in a JWT payload (registered, public, private)
- `kid (Key ID)`: Header parameter identifying which key was used to sign the JWT

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"JWT token validation best practices 2024"` | General | High |
| 2 | `"jjwt vs nimbus-jose-jwt Java JWT library"` | Library comparison | High |
| 3 | `"JWKS key rotation strategy zero downtime"` | Architecture | High |
| 4 | `"JWT blacklist Redis token revocation microservices"` | Revocation strategy | High |
| 5 | `"RFC 7662 token introspection Spring Boot"` | Standard compliance | Medium |
| 6 | `"Spring Security resource server JWT validation"` | Framework integration | Medium |
| 7 | `"JWT validation pipeline architecture"` | Design pattern | Medium |
| 8 | `"OWASP JWT security cheat sheet"` | Security guidelines | High |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| JwtService | `auth.application.JwtService` | High | Core JWT generation & parsing — RS256 + HMAC fallback |
| JwtAuthFilter | `shared.security.JwtAuthFilter` | High | Stage 1 PEP — validates JWT, checks blacklist, sets SecurityContext |
| ServiceAuthFilter | `auth.adapter.in.web.filter.ServiceAuthFilter` | High | Validates service JWT for /api/internal/ endpoints |
| ServiceTokenService | `auth.application.ServiceTokenService` | High | Issues/validates JWTs for inter-service auth (FR-021) |
| TokenController | `auth.adapter.in.web.TokenController` | High | Introspection (RFC 7662), JWKS, session revocation |
| TokenStorePersistenceAdapter | `auth.adapter.out.persistence.TokenStorePersistenceAdapter` | Medium | JPA adapter for refresh token + blacklist storage |
| RefreshTokenHandler | `auth.application.command.RefreshTokenHandler` | Medium | Token rotation with revocation events |
| SecurityProperties | `shared.config.SecurityProperties` | High | JWT config: key paths, TTLs, issuer, keyId |
| SecurityConfig | `shared.config.SecurityConfig` | High | Spring Security filter chain configuration |
| TokenIssuedEvent | `auth.domain.event.TokenIssuedEvent` | Medium | Domain event for audit trail |
| TokenBlacklistEntity | `rbac.adapter.out.persistence.entity` | Medium | JPA entity for blacklisted JTIs |

### 4.2 Existing Code Patterns

**Architecture**: Clean Architecture (Hexagonal) with CQRS + Event Sourcing
- **Adapters (in)**: Controllers, Kafka listeners, Filters (web layer)
- **Adapters (out)**: Persistence adapters, HTTP clients, Event publishers
- **Application**: Services, Command/Query handlers, Ports
- **Domain**: Events, Models, Value objects

**JWT Validation Pattern** (current):
1. `JwtAuthFilter` extracts Bearer token from Authorization header
2. Calls `JwtService.parseToken()` — tries RS256 first, HMAC fallback
3. Checks blacklist via `TokenBlacklistRepository.existsByTokenJti(jti)`
4. Discriminates token type (anonymous vs authenticated) via `type` claim
5. Sets `SecurityContextHolder` with authorities

**Token Types** in system:
- `access` (implicit, no type claim) — user access token with roles/permissions
- `refresh` (type="refresh") — minimal claims, used for rotation
- `mfa` (type="mfa") — short-lived challenge token (5 min)
- `anonymous` (type="anonymous") — ephemeral session token (1 hour)
- `service` (type="service") — inter-service communication token (1 hour)

**Signing**:
- Primary: RS256 (RSA 2048-bit, PKCS8 private key, X509 public key)
- Fallback: HMAC-SHA256 (7-day migration window)
- Service tokens: HMAC-SHA256 only (derived from main secret + ":service")

### 4.3 Tech Stack Constraints

- Language: Kotlin 2.x
- Framework: Spring Boot 3.x + Spring Security 6.x
- Database: PostgreSQL (primary), H2 (testing)
- Cache: Caffeine (L1 in-process) + Redis (L2 distributed) via `base-cache-starter`
- JWT Library: io.jsonwebtoken:jjwt (0.12.x API)
- Build tool: Gradle (Kotlin DSL) with version catalogs
- Base module: `base-core` (auto-config for cache, security, data, observability)
- Event Store: PostgreSQL JSONB via `eventsourcing-utils`
- Message Broker: Apache Kafka (outbox pattern)

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `JwtService` | Application Service | `auth.application.JwtService` | Central JWT operations — all token types |
| `JwtAuthFilter` | Web Filter | `shared.security.JwtAuthFilter` | Spring Security filter chain — OncePerRequestFilter |
| `ServiceAuthFilter` | Web Filter | `auth.adapter.in.web.filter.ServiceAuthFilter` | Internal API auth |
| `TokenBlacklistRepository` | JPA Repository | `rbac.adapter.out.persistence.repository` | JTI-based blacklist queries |
| `SecurityProperties` | Config | `shared.config.SecurityProperties` | JWT, password, MFA, SSO, session configs |
| `TokenStore` | Port (out) | `auth.application.port.out.TokenStore` | Refresh token + blacklist operations |
| `EventStorePort` | Port (out) | `auth.application.port.out.EventStorePort` | Event sourcing — persist domain events |
| `base-security-starter` | Base Module | `com.ntt:base-security-starter` | Auto-configured security beans |
| `base-cache-starter` | Base Module | `com.ntt:base-cache-starter` | Two-level cache (Caffeine + Redis) |
| `TwoLevelCacheManager` | Cache | `shared.config.SecurityConfig` | Custom cache manager bean |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời

- [x] Q1: What is the optimal JWT validation pipeline order (signature → claims → blacklist → context)?
- [x] Q2: Should we use jjwt alone or consider nimbus-jose-jwt for validation?
- [x] Q3: How to implement JWKS key rotation with zero downtime?
- [x] Q4: What caching strategy is optimal for token blacklist checks (Redis vs in-memory bloom filter)?
- [x] Q5: How to unify token validation across different token types (access, refresh, mfa, anonymous, service)?
- [x] Q6: Is RFC 7662 introspection sufficient for microservice token validation, or should services validate locally?
- [x] Q7: How to implement token binding to prevent token theft/relay attacks?
- [x] Q8: What Event Sourcing events should be emitted for validation failures (security monitoring)?

### 5.2 Assumptions cần verify

- [x] A1: RS256 is sufficient for production — no need for RS384/RS512 or EdDSA
- [x] A2: jjwt library handles all required validation scenarios (exp, nbf, iss, aud checks)
- [x] A3: PostgreSQL-based blacklist with Redis cache is adequate for < 10K concurrent sessions
- [x] A4: HMAC fallback can be safely removed after 7-day migration window

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Comprehensive analysis of JWT validation patterns | ≥ 5 unique sources |
| Open source options | Evaluated JWT libraries and auth frameworks | ≥ 3 repos evaluated |
| Gap analysis | Identify gaps between current implementation and best practices | All critical gaps identified |
| Business analysis | All validation use cases documented with flows | All UCs documented |
| Technical spec | Validation pipeline design ready for implementation | Agent-ready for implementation |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
