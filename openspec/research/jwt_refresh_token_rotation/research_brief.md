# Research Brief: JWT Refresh Token Rotation

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | JWT Refresh Token Rotation |
| **Ngày tạo** | 2025-01-27 |
| **Input source** | Name (enriched description from pipeline) |
| **Input content** | Implement production-grade refresh token rotation: each refresh cycle invalidates the previous refresh token and issues a new one, with token family tracking, reuse detection, and automatic revocation of compromised token families |
| **Người yêu cầu** | Pipeline (auth-core-features) |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

The auth-service already implements basic refresh token rotation — `RefreshTokenHandler` revokes the old refresh token and issues a new access/refresh pair via `TokenGenerator.generateAuthResponse()`. However, the current implementation lacks critical security hardening features recommended by OAuth 2.0 Security Best Current Practice (RFC draft-ietf-oauth-security-topics):

1. **Token Family Tracking**: No concept of a "token family" — cannot trace lineage of refresh tokens across multiple rotations.
2. **Reuse Detection**: If a previously-rotated (revoked) refresh token is presented, the system simply returns `TokenExpiredException`. It does NOT detect this as a potential token theft and does NOT revoke the entire token family.
3. **Automatic Family Revocation**: No mechanism to automatically revoke all tokens in a family when reuse is detected.
4. **Token Chain Integrity**: `previousRefreshTokenHash` is recorded in `TokenIssuedEvent` for audit but not used for runtime reuse detection.
5. **Grace Period**: No grace period for concurrent requests that may legitimately use the same refresh token simultaneously (e.g., multi-tab browser scenarios).

### 2.2 Mục tiêu (Objectives)

- [x] Objective 1: Implement token family tracking — each login creates a family, each rotation extends it
- [x] Objective 2: Detect refresh token reuse (replay attack) and automatically revoke entire token family
- [x] Objective 3: Add configurable grace period for concurrent refresh requests
- [x] Objective 4: Record security events for token reuse detection (audit trail)
- [x] Objective 5: Maintain backward compatibility with existing `RefreshTokenHandler` / `TokenStore` interfaces

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| Token family ID generation and storage | OAuth2 authorization server implementation |
| Reuse detection logic in RefreshTokenHandler | Client-side token management |
| Automatic family revocation on reuse | Token encryption at rest (handled by E2EE feature) |
| Grace period configuration | Access token rotation (access tokens are short-lived) |
| Event sourcing integration (domain events) | Cross-service token synchronization |
| Redis-backed token family registry | UI/frontend changes |
| Database schema migration for family_id column | Key rotation (already implemented) |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `refresh token rotation`
- `token reuse detection`
- `token family`
- `OAuth2 refresh token security`
- `JWT token rotation pattern`

### 3.2 Secondary Keywords
- `refresh token theft detection`
- `token chain revocation`
- `automatic token invalidation`
- `concurrent refresh handling`
- `token replay attack prevention`
- `token family tree`

### 3.3 Domain-Specific Terms
- `Token Family`: A chain of refresh tokens originating from a single authentication event (login). Each rotation creates a new token in the same family.
- `Reuse Detection`: The ability to detect when a previously-rotated (already-used) refresh token is presented again, indicating potential token theft.
- `Grace Period`: A short time window (e.g., 5-10 seconds) after rotation where the old refresh token is still accepted, handling concurrent requests.
- `Token Chain`: The ordered sequence of refresh tokens within a family, linked by parent-child relationships.
- `Family Revocation`: Revoking all tokens in a token family when suspicious activity (reuse) is detected.

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"refresh token rotation reuse detection best practices"` | Security patterns | High |
| 2 | `"OAuth2 refresh token family tracking implementation"` | Architecture | High |
| 3 | `"Spring Boot refresh token rotation open source"` | Open Source | High |
| 4 | `"token family revocation concurrent requests grace period"` | Edge cases | Medium |
| 5 | `"Auth0 Okta refresh token rotation comparison"` | Products | Medium |
| 6 | `"event sourcing token lifecycle management"` | Architecture | Medium |
| 7 | `"RFC 6749 refresh token rotation security considerations"` | Standards | High |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| RefreshTokenHandler | `auth.application.command` | **Critical** | Current rotation logic — revoke old, issue new. No family tracking. |
| TokenGenerator | `auth.application.command` | **Critical** | Shared token generation — generates access+refresh pair, records TokenIssuedEvent |
| TokenStore (port) | `auth.application.port.out` | **Critical** | Outbound port: saveRefreshToken, findValidRefreshToken, revokeToken, revokeAllForUser |
| TokenStorePersistenceAdapter | `auth.adapter.out.persistence` | High | JPA adapter implementing TokenStore — manages RefreshTokenEntity |
| RefreshTokenEntity | `rbac.adapter.out.persistence.entity` | High | Entity: userId, tokenHash, expiresAt, revoked, createdAt |
| RefreshTokenRepository | `rbac.adapter.out.persistence.repository` | High | JPA repo: findByTokenHashAndRevokedFalse, revokeAllByUserId |
| TokenHasher | `auth.domain.service` | Medium | SHA-256 hashing for refresh tokens |
| TokenEventRecorder | `auth.application.event` | High | Records TokenIssuedEvent and TokenRevokedEvent to event store |
| TokenRevokedEvent | `auth.domain.event` | High | Domain event with RevocationType (ROTATION, LOGOUT, ADMIN_REVOKE, BULK_REVOKE) |
| TokenIssuedEvent | `auth.domain.event` | High | Domain event with IssuanceContext, previousRefreshTokenHash, correlationId |
| CqrsAuthController | `auth.adapter.in.web` | Medium | POST /api/auth/refresh endpoint — cookie-first, body-fallback |
| SecurityProperties | `shared.config` | Medium | JWT config: refreshTokenExpirationMs (7 days), session config |
| TokenBlacklistCacheService | `auth.application` | Low | L1 Caffeine + L2 Redis blacklist (for access tokens, not refresh tokens) |

### 4.2 Existing Code Patterns

**Current Refresh Token Rotation Flow (RefreshTokenHandler):**
1. Hash incoming refresh token using `TokenHasher.hash()`
2. Lookup in DB via `tokenStore.findValidRefreshToken(tokenHash)` — returns `RefreshTokenInfo` or null
3. Check expiry — if expired, revoke and throw `TokenExpiredException`
4. Load user via `userPort.findById()`
5. Revoke old token via `tokenStore.revokeToken(tokenHash)`
6. Record `TokenRevokedEvent(ROTATION)` with correlationId
7. Generate new auth response via `tokenGenerator.generateAuthResponse()` with `IssuanceContext.TOKEN_REFRESH` and `previousRefreshTokenHash`
8. TokenGenerator internally saves new refresh token hash, records `TokenIssuedEvent`

**Key observations:**
- ✅ Already implements basic rotation (revoke old → issue new)
- ✅ Already records previousRefreshTokenHash in TokenIssuedEvent for audit
- ✅ Already uses correlationId to link revocation and issuance events
- ❌ No `familyId` concept — cannot link multiple rotations
- ❌ No reuse detection — revoked token simply returns TokenExpiredException (same as expired)
- ❌ No grace period for concurrent requests
- ❌ RefreshTokenEntity has no `familyId` column
- ❌ No mechanism to revoke entire token family

**CQRS Pattern:** Commands (RefreshTokenCommand) → Handlers (RefreshTokenHandler) → Events (TokenRevokedEvent, TokenIssuedEvent)

**Event Sourcing:** Domain events recorded via `TokenEventRecorder`, stored in event store. Events are immutable and used for audit trails.

**Clean Architecture:** Port/Adapter pattern — `TokenStore` (port) ↔ `TokenStorePersistenceAdapter` (adapter)

### 4.3 Tech Stack Constraints

- Language: Kotlin
- Framework: Spring Boot 4.x (with base-core auto-configuration)
- Database: PostgreSQL (with Flyway migrations)
- Cache: Caffeine (L1) + Redis (L2) via `base-cache-starter`
- Event Store: Custom via `eventsourcing-utils` library
- JWT: jjwt library with RS256 signing
- CQRS: Custom Command/CommandHandler via `eventsourcing-utils`
- Build tool: Gradle (Kotlin DSL)
- Testing: JUnit 5 + Mockito + H2

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| TokenStore.saveRefreshToken() | Port method | `auth.application.port.out.TokenStore` | Needs `familyId` parameter |
| TokenStore.findValidRefreshToken() | Port method | `auth.application.port.out.TokenStore` | Returns RefreshTokenInfo — needs familyId |
| RefreshTokenEntity | JPA Entity | `rbac.adapter.out.persistence.entity` | Needs `family_id` column |
| RefreshTokenRepository | JPA Repo | `rbac.adapter.out.persistence.repository` | Needs queries by familyId |
| TokenStorePersistenceAdapter | Adapter | `auth.adapter.out.persistence` | Needs family-aware operations |
| TokenRevokedEvent | Domain Event | `auth.domain.event` | May need new RevocationType.FAMILY_REVOKE |
| SecurityProperties | Config | `shared.config` | Needs grace period config |
| CqrsAuthController | Web Controller | `auth.adapter.in.web` | No change needed (delegates to handler) |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời

- [x] Q1: What is the recommended pattern for token family tracking (UUID family ID vs. chain linking)?
- [x] Q2: How should reuse detection handle the race condition of concurrent refresh requests?
- [x] Q3: Should family revocation be synchronous (immediate) or asynchronous (event-driven)?
- [x] Q4: What is the optimal grace period duration for concurrent requests?
- [x] Q5: How do Auth0, Okta, and Keycloak implement refresh token rotation with reuse detection?
- [x] Q6: Should the token family registry be stored in Redis (fast lookup) or PostgreSQL (durable)?
- [x] Q7: What domain events should be emitted for reuse detection and family revocation?

### 5.2 Assumptions cần verify

- [x] A1: Current RefreshTokenEntity can be extended with a `familyId` column via Flyway migration without breaking existing data
- [x] A2: `base-cache-starter` Redis integration supports the required data structures for token family tracking
- [x] A3: The `eventsourcing-utils` CQRS framework supports the additional event types needed

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Comprehensive analysis of rotation patterns | ≥ 5 sources |
| Open source options | Evaluate relevant implementations | ≥ 3 repos evaluated |
| Gap analysis | All critical gaps between current and target identified | All critical gaps documented |
| Business analysis | All use cases for rotation, reuse detection, family revocation | All UCs documented with flows |
| Technical spec | Detailed design ready for implementation | Agent-ready with class list, migration scripts |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
