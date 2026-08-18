# Research Brief: Anonymous Login Optimization

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | Anonymous Login Optimization |
| **Ngày tạo** | 2025-01-20 |
| **Input source** | Name + Description (seed_prompt) |
| **Input content** | Research anonymous/guest authentication patterns for enterprise IAM systems. Focus on: temporary anonymous sessions, session promotion (merging anonymous session data into authenticated user after login), guest cart management, seamless authentication upgrade flow, and anonymous-to-authenticated data transfer. Include Spring Boot + Redis + JWT implementation patterns. |
| **Người yêu cầu** | Pipeline (headless mode) |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

The auth-service currently requires full authentication (username/password + optional MFA) before any user interaction. This creates friction for scenarios where users need to perform actions (browse products, add to cart, configure preferences) before committing to account creation. Enterprise IAM systems commonly support "anonymous" or "guest" users — lightweight temporary identities that can later be promoted to fully authenticated accounts. The challenge is managing the lifecycle of these temporary sessions and seamlessly transferring accumulated data (cart items, preferences, browsing history) when the anonymous user decides to register or log in.

### 2.2 Mục tiêu (Objectives)

- [ ] Objective 1: Define a secure anonymous session creation mechanism that issues lightweight JWT tokens for unauthenticated users
- [ ] Objective 2: Design a session promotion flow that merges anonymous session data into an authenticated user account upon login/registration
- [ ] Objective 3: Establish anonymous session lifecycle management (creation, TTL, cleanup, rate limiting)
- [ ] Objective 4: Design temporary data storage patterns (Redis-backed) for anonymous user activities
- [ ] Objective 5: Ensure security controls to prevent abuse (rate limiting, fingerprinting, token rotation)

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| Anonymous token generation (JWT with `type=anonymous`) | Full e-commerce cart implementation (only data transfer pattern) |
| Session promotion (anonymous → authenticated) | Social login anonymous → SSO merge (defer to SSO module) |
| Anonymous session data storage in Redis | Complex personalization engine |
| Rate limiting for anonymous session creation | Anonymous user analytics/tracking |
| Anonymous session TTL and cleanup | Multi-service anonymous session federation |
| Data transfer hook/callback mechanism | Payment processing for anonymous users |
| Security controls (abuse prevention) | GDPR consent flow for anonymous data |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `anonymous authentication`
- `guest session management`
- `session promotion`
- `anonymous to authenticated migration`
- `anonymous JWT token`

### 3.2 Secondary Keywords
- `guest checkout pattern`
- `anonymous user temporary data`
- `session merge strategy`
- `token upgrade flow`
- `anonymous session Redis`
- `lazy registration`
- `progressive authentication`

### 3.3 Domain-Specific Terms
- `Session Promotion`: The process of converting an anonymous/guest session into a fully authenticated user session, transferring accumulated data.
- `Anonymous Token`: A lightweight JWT token issued without credentials, identifying a temporary session with limited permissions.
- `Data Transfer Hook`: A callback mechanism invoked during session promotion to migrate temporary data from anonymous session to the authenticated user's persistent storage.
- `Progressive Authentication`: Authentication pattern where users gradually increase their trust level — from anonymous to email-verified to fully authenticated.
- `Lazy Registration`: Deferring user registration until absolutely necessary, allowing interaction first.
- `Session Linking`: Associating an anonymous session ID with a subsequently authenticated user ID.

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"anonymous authentication best practices enterprise IAM"` | General patterns | High |
| 2 | `"guest session promotion Spring Boot Redis"` | Implementation | High |
| 3 | `"anonymous JWT token generation lifecycle"` | Architecture | High |
| 4 | `"session merge anonymous authenticated user"` | Data transfer | High |
| 5 | `"anonymous authentication open source GitHub"` | Open Source | Medium |
| 6 | `"Firebase anonymous auth session promotion pattern"` | Reference impl | Medium |
| 7 | `"Keycloak anonymous user guest access"` | Enterprise IAM | Medium |
| 8 | `"anonymous session abuse prevention rate limiting"` | Security | Medium |
| 9 | `"progressive authentication lazy registration"` | UX pattern | Low |
| 10 | `"anonymous cart merge login e-commerce"` | Domain example | Low |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| Login/Register flow | `auth.application.AuthService`, `auth.application.command.LoginHandler` | High | Core auth flow — anonymous login will bypass this initially and integrate during promotion |
| JWT token generation | `auth.application.JwtService` | High | Already supports multiple token types (access, refresh, mfa) — will need `anonymous` type |
| Session management | `auth.application.LoginSessionService` | High | Tracks login sessions with device fingerprinting — anonymous sessions need similar tracking |
| Session policy | `auth.application.SessionPolicyService` | Medium | Enforces max sessions — anonymous sessions need separate policy |
| Rate limiting | `auth.application.LoginRateLimitService` | High | Multi-dimensional rate limiting (IP, username, device) — anonymous creation needs similar controls |
| CQRS command/handler | `auth.application.command.*` | Medium | Existing pattern for commands — anonymous login should follow same pattern |
| Redis integration | `spring-boot-starter-data-redis` | High | Already configured for OTP, MFA state, rate limiting — will store anonymous session data |
| Cipher key sessions | `auth.adapter.out.cipher.RedisCipherKeySessionResolver` | Low | Uses `"anonymous"` userId for key exchange without auth — shows existing anonymous concept |
| AuthResponse DTO | `auth.adapter.in.web.dto.AuthResponse` | Medium | Response structure needs anonymous variant |
| SecurityConfig | `shared.config.SecurityConfig` | High | Must permit anonymous endpoints without JWT |

### 4.2 Existing Code Patterns

**Architecture Pattern**: Clean Architecture (Hexagonal) with CQRS command/query separation.
- **Adapter layer**: `adapter.in.web` (controllers), `adapter.out.persistence` (JPA repos), `adapter.out.cache` (Redis/Caffeine)
- **Application layer**: Services, command handlers, port interfaces
- **Domain layer**: Domain models with value objects, domain services

**CQRS Pattern**: Commands go through `CommandHandler<C, R>` interface from `eventsourcing-utils`. Example: `LoginCommand → LoginHandler → LoginResult`.

**Token Pattern**: JWT with RS256 primary / HMAC fallback. Token types: `access`, `refresh`, `mfa`. Each has different TTL and claims.

**Session Pattern**: `LoginSessionEntity` tracks each login with device fingerprint, IP, user agent, activity timestamps. Sessions have active/revoked states.

**Rate Limiting Pattern**: Multi-dimensional (IP, username, device fingerprint) using Redis with configurable windows and lock durations.

**Entity Base Class**: All entities extend `SnowflakePersistentAuditableEntity` (provides Snowflake ID, createdAt, updatedAt, createdBy, updatedBy, active flag).

### 4.3 Tech Stack Constraints
- Language: Kotlin
- Framework: Spring Boot (with Spring Security, Spring Data JPA, Spring Data Redis)
- Database: PostgreSQL
- Cache: Redis (for OTP, MFA, rate limiting, cipher keys) + Caffeine (L1 permission cache)
- Build tool: Gradle (Kotlin DSL)
- JWT library: io.jsonwebtoken (jjwt)
- ID generation: Snowflake IDs
- Testing: JUnit 5, Mockito-Kotlin, ArchUnit

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `JwtService.generateAccessToken()` | Internal Service | `auth.application.JwtService` | Will need new `generateAnonymousToken()` method |
| `LoginSessionService.recordLogin()` | Internal Service | `auth.application.LoginSessionService` | Promotion flow needs to link anonymous session to user |
| `SecurityConfig.securityFilterChain()` | Configuration | `shared.config.SecurityConfig` | Must add `/api/auth/anonymous` to permitAll() |
| `LoginHandler.handle()` | CQRS Handler | `auth.application.command.LoginHandler` | Session promotion during login |
| `RegisterHandler.handle()` | CQRS Handler | `auth.application.command.RegisterHandler` | Session promotion during registration |
| `RefreshTokenHandler.handle()` | CQRS Handler | `auth.application.command.RefreshTokenHandler` | Anonymous token refresh |
| Redis | External | `spring-boot-starter-data-redis` | Anonymous session data storage (key: `anon:session:{sessionId}`) |
| `LoginRateLimitService` | Internal Service | `auth.application.LoginRateLimitService` | Rate limit anonymous session creation per IP |
| `AuthResponse` | DTO | `auth.adapter.in.web.dto.AuthResponse` | Anonymous response variant (no userId, limited roles) |
| `SessionCleanupScheduler` | Scheduler | `auth.application.SessionCleanupScheduler` | Cleanup expired anonymous sessions |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [ ] Q1: How do enterprise IAM systems (Keycloak, Auth0, Firebase) handle anonymous/guest users with temporary sessions?
- [ ] Q2: What are best practices for session promotion (anonymous → authenticated) — what data is transferred and how?
- [ ] Q3: How to seamlessly transfer temporary data (cart, preferences) after login without data loss?
- [ ] Q4: What is the optimal anonymous token generation strategy — should it use JWT or opaque tokens?
- [ ] Q5: Security considerations for anonymous sessions — rate limiting, abuse prevention, token rotation?
- [ ] Q6: How should anonymous session TTL be managed — fixed expiry vs sliding window vs activity-based?
- [ ] Q7: What happens when an anonymous user logs in with an account that already has data from a previous session?
- [ ] Q8: How to handle concurrent anonymous sessions from the same device/IP?
- [ ] Q9: Should anonymous sessions be stored in Redis only (ephemeral) or also persisted to PostgreSQL?
- [ ] Q10: How to implement data transfer hooks that are extensible for different data types (cart, preferences, etc.)?

### 5.2 Assumptions cần verify
- [ ] A1: Anonymous tokens should be lightweight JWTs (not opaque) so downstream services can validate without calling auth-service
- [ ] A2: Anonymous session data should be stored in Redis (not PostgreSQL) due to ephemeral nature and high volume
- [ ] A3: Session promotion is a one-time operation — once promoted, the anonymous session is invalidated
- [ ] A4: Anonymous sessions should have shorter TTL than authenticated sessions (e.g., 24h vs 7 days)
- [ ] A5: The auth-service only manages anonymous identity — downstream services manage their own data transfer during promotion

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Comprehensive understanding of anonymous auth patterns | ≥ 5 sources analyzed |
| Open source options | Evaluation of existing implementations | ≥ 3 repos evaluated with scoring |
| Gap analysis | Mapping of features to project needs | All critical gaps identified |
| Business analysis | Complete use case decomposition | All UCs documented with flows |
| Technical spec | Detailed design ready for implementation | Agent-ready with ERD, sequence diagrams, API spec |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
