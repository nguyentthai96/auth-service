# Research Brief: Anonymous Login Optimization

> Tài liệu khởi đầu cho quá trình research tính năng — xác định scope, keywords, context.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | Anonymous Login Optimization |
| **Ngày tạo** | 2025-01-20 |
| **Input source** | Name + Description (seed prompt) |
| **Input content** | Research anonymous/guest authentication patterns for enterprise IAM systems. Focus on: temporary anonymous sessions, session promotion (merging anonymous session data into authenticated user after login), guest cart management, seamless authentication upgrade flow, and anonymous-to-authenticated data transfer. Include Spring Boot + Redis + JWT implementation patterns. |
| **Người yêu cầu** | Pipeline (headless mode) |

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)

The auth-service currently supports only fully authenticated users — every API interaction requires a valid JWT token obtained through username/password login, SSO, or MFA verification. There is no mechanism to allow unauthenticated users to interact with the system in a limited capacity before requiring login.

Many enterprise applications and e-commerce platforms need to support anonymous/guest sessions to reduce friction for first-time visitors. Users should be able to browse, add items to a cart, save preferences, or interact with the system without creating an account — and then seamlessly merge that temporary data into their profile upon authentication.

This is commonly known as "session promotion" or "authentication upgrade" — transitioning from an anonymous identity to a fully authenticated one while preserving the user's work and context.

### 2.2 Mục tiêu (Objectives)
- [x] Objective 1: Enable anonymous/guest token generation with limited permissions and configurable TTL
- [x] Objective 2: Implement session promotion — seamlessly upgrading anonymous sessions to authenticated user sessions
- [x] Objective 3: Design a data transfer mechanism to merge temporary anonymous data (cart, preferences) into the authenticated user's profile
- [x] Objective 4: Define anonymous token lifecycle management (creation, renewal, expiration, cleanup)
- [x] Objective 5: Establish security guardrails for anonymous sessions (rate limiting, abuse prevention, resource quotas)

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| Anonymous token generation (JWT with `type=anonymous`) | Full guest checkout flow (belongs to order-service) |
| Session promotion flow (anonymous → authenticated) | UI/UX for anonymous user onboarding |
| Redis-backed temporary data store for anonymous sessions | Shopping cart domain logic (belongs to cart-service) |
| Anonymous session lifecycle (TTL, cleanup, renewal) | Analytics tracking of anonymous users |
| Rate limiting and abuse prevention for anonymous endpoints | A/B testing of anonymous vs. forced-login flows |
| API endpoints for anonymous token creation and promotion | Social login integration for anonymous promotion |
| Configuration properties for anonymous session behavior | Mobile SDK for anonymous authentication |
| Integration with existing CQRS command/handler architecture | Multi-tenant anonymous session isolation |

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `anonymous authentication`
- `guest session management`
- `session promotion`
- `anonymous to authenticated upgrade`
- `temporary session token`

### 3.2 Secondary Keywords
- `guest cart merge`
- `anonymous JWT token`
- `session handoff`
- `lazy registration`
- `progressive authentication`
- `ephemeral session`
- `anonymous user tracking`

### 3.3 Domain-Specific Terms
- `Session Promotion`: The process of upgrading an anonymous/guest session to a fully authenticated session, preserving temporary data accumulated during the anonymous phase.
- `Anonymous Token`: A JWT token issued without user credentials, carrying limited permissions and a shorter TTL. Contains a `type=anonymous` claim and a unique anonymous session ID.
- `Data Transfer / Session Merge`: The mechanism of migrating temporary data (cart items, preferences, form data) from an anonymous session to the authenticated user's permanent storage.
- `Progressive Authentication`: A UX pattern where users begin interacting without credentials and are prompted to authenticate only when accessing privileged features.
- `Lazy Registration`: Delaying account creation until the user needs to perform an action requiring identity (checkout, save, etc.).
- `Ephemeral Session`: A short-lived session stored in Redis with automatic TTL-based expiration.

### 3.4 Search Queries (pre-defined)

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"anonymous authentication session promotion Spring Boot best practices"` | General | High |
| 2 | `"guest session to authenticated user migration open source"` | Open Source | High |
| 3 | `"anonymous JWT token generation Redis temporary session"` | Architecture | High |
| 4 | `"session promotion guest cart merge enterprise patterns"` | Patterns | Medium |
| 5 | `"Spring Security anonymous authentication filter configuration"` | Framework | Medium |
| 6 | `"anonymous user rate limiting abuse prevention"` | Security | Medium |
| 7 | `"progressive authentication lazy registration IAM"` | Comparison | Low |
| 8 | `"ephemeral session Redis TTL guest user management"` | Implementation | Medium |

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| Login (CQRS) | `auth.application.command.LoginHandler` | High | Main authentication handler — anonymous promotion must integrate with this flow |
| JWT Token Generation | `auth.application.JwtService` | High | Already supports multiple token types (`access`, `refresh`, `mfa`) — need to add `anonymous` type |
| Session Management | `auth.application.LoginSessionService` | High | Records login sessions, device info — need to extend for anonymous sessions |
| Session Policy | `auth.application.SessionPolicyService` | Medium | Enforces max sessions — need to decide if anonymous sessions count toward limits |
| Token Generator | `auth.application.command.TokenGenerator` | High | Shared token generation — needs extension for anonymous tokens |
| Security Config | `shared.config.SecurityConfig` | High | Defines public vs. authenticated endpoints — needs anonymous endpoint rules |
| Rate Limiting | `auth.application.LoginRateLimitService` | Medium | IP/username/device rate limiting — needs anonymous-specific limits |
| SSO Adapter | `auth.application.SsoAdapter` | Low | SSO callback could trigger session promotion |
| MFA Service | `auth.application.MfaService` | Low | MFA flow after anonymous promotion |
| CAPTCHA Verifier | `auth.application.CaptchaVerifier` | Medium | May be required for anonymous token generation to prevent abuse |

### 4.2 Existing Code Patterns

**Architecture**: Clean Architecture with Hexagonal / Ports & Adapters pattern:
- **Domain layer**: `auth.domain.model` — Pure Kotlin domain models (User, AuthToken, value objects)
- **Application layer**: `auth.application` — Services + CQRS Handlers (LoginHandler, RegisterHandler, etc.)
- **Adapter layer**: 
  - `adapter.in.web` — REST Controllers
  - `adapter.out.persistence` — JPA repositories + entities
  - `adapter.out.cache` — Caffeine + Redis caching
  
**CQRS Pattern**: Commands handled by `CommandHandler<C, R>` from `eventsourcing-utils`. Both "legacy" `AuthService` and "CQRS" handlers (`LoginHandler`, `RegisterHandler`) coexist via `@ConditionalOnProperty`.

**Token Types Already Supported**:
- Access Token (JWT, RS256, 15 min TTL)
- Refresh Token (JWT, 7 day TTL, stored hash in DB)
- MFA Token (JWT, 5 min TTL, `type=mfa` claim)

**Session Tracking**: `LoginSessionEntity` stores active sessions with device fingerprint, IP, user agent, browser/OS detection.

**Security**: Stateless JWT authentication, Spring Security filter chain, BCrypt password encoding, CAPTCHA, rate limiting.

### 4.3 Tech Stack Constraints
- Language: Kotlin
- Framework: Spring Boot (with Spring Security, Spring Data JPA, Spring Data Redis)
- Database: PostgreSQL (Flyway migrations, Snowflake IDs)
- Cache: Redis (session data, rate limiting) + Caffeine (L1 permission cache)
- Build tool: Gradle (Kotlin DSL) with custom conventions plugin
- Token: JJWT (RS256 primary, HMAC fallback)
- CQRS: `eventsourcing-utils` library (custom `CommandHandler`)
- Base framework: `base-core`, `base-web-starter`, `base-data-starter` (custom platform)

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `JwtService` | Service | `auth.application.JwtService` | Extend to generate/validate anonymous tokens |
| `TokenGenerator` | Service | `auth.application.command.TokenGenerator` | Extend to handle anonymous → authenticated promotion |
| `SecurityConfig` | Config | `shared.config.SecurityConfig` | Add anonymous endpoints to permitAll() list |
| `SecurityProperties` | Config | `shared.config.SecurityProperties` | Add anonymous session config properties |
| `LoginSessionService` | Service | `auth.application.LoginSessionService` | Track anonymous sessions |
| `LoginSessionRepository` | Repository | `auth.adapter.out.persistence.repository` | New anonymous session queries |
| `login_sessions` | DB Table | `V4__create_login_sessions.sql` | May need migration for anonymous session columns |
| Redis | Cache | Spring Data Redis (already configured) | Temporary anonymous session data store |
| `LoginHandler` | Command Handler | `auth.application.command.LoginHandler` | Session promotion integration point |
| `AuthController` / `CqrsAuthController` | Controller | `auth.adapter.in.web` | New anonymous endpoints |

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] Q1: How do enterprise IAM systems (Keycloak, Auth0, Firebase Auth) handle anonymous/guest users with temporary sessions?
- [x] Q2: What are best practices for session promotion (anonymous → authenticated) — specifically data merge strategies?
- [x] Q3: How to seamlessly transfer temporary data (cart, preferences) after login without data loss?
- [x] Q4: What JWT claims and token structure should anonymous tokens use?
- [x] Q5: What are the security risks of anonymous sessions and how to mitigate them (rate limiting, abuse, resource exhaustion)?
- [x] Q6: Should anonymous sessions be stored in Redis only or also persisted to PostgreSQL?
- [x] Q7: How should anonymous session TTL be configured (short-lived vs. longer for better UX)?
- [x] Q8: What happens when an anonymous user logs in to an existing account — merge or discard anonymous data?
- [x] Q9: Should anonymous tokens count toward the existing session limit (maxSessions=3)?
- [x] Q10: How to handle concurrent anonymous → authenticated promotions (race conditions)?

### 5.2 Assumptions cần verify
- [x] A1: The system should support anonymous sessions without requiring CAPTCHA for initial token generation (CAPTCHA only after suspicious activity)
- [x] A2: Anonymous session data will be stored in Redis with configurable TTL (default 24h)
- [x] A3: Anonymous tokens will NOT count toward the authenticated session limit (maxSessions)
- [x] A4: Session promotion is a one-time, atomic operation — after promotion, the anonymous session is destroyed
- [x] A5: The anonymous token uses the same RS256 signing key as authenticated tokens

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Comprehensive survey of anonymous auth patterns | ≥ 5 sources analyzed |
| Open source options | Evaluate existing solutions and frameworks | ≥ 3 projects evaluated |
| Gap analysis | Identify what's missing from existing solutions vs. our needs | All critical gaps identified |
| Business analysis | Complete use case decomposition with flows | All UCs documented with basic + exception flows |
| Technical spec | Actionable spec that an agent can implement from | Agent-ready with classes, APIs, schemas defined |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
