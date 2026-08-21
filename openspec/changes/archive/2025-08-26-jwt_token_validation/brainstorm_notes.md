---
type: brainstorm_notes
change: jwt_token_validation
date: 2026-08-26
selected_direction: "Approach B: Custom TokenBlacklistCacheService with Direct Caffeine + StringRedisTemplate — Incremental Enhancement of Existing JJWT Pipeline"
pre_flow: "Query"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: JWT Token Validation — Production-Grade Hardening

## Date
2026-08-26

## Context

The auth-service has a functional JWT validation pipeline (`JwtAuthFilter` → `JwtService.parseToken()` → `TokenBlacklistRepository.existsByTokenJti()`) but it has six critical gaps identified during research:

1. **DB blacklist bottleneck**: Every authenticated request hits PostgreSQL via `TokenBlacklistRepository.existsByTokenJti()` — O(1) indexed query but ~1-5ms per call. At >500 req/s, this becomes the throughput ceiling.
2. **Missing audience (aud) validation**: Per RFC 8725, tokens MUST validate audience claim. Current system skips this entirely — tokens issued for one service could be replayed against another in a multi-service environment.
3. **Zero clock skew tolerance**: `JwtService.parseToken()` uses JJWT's default 0-second clock skew. In distributed environments with NTP drift, valid tokens may be falsely rejected.
4. **No JWKS key rotation**: `JwtService` loads a single RSA key pair. Rotating keys requires application restart — zero-downtime rotation is impossible.
5. **Non-standard introspection**: `TokenController.introspect()` returns a custom response shape missing RFC 7662 fields (`token_type`, `scope`, `client_id`).
6. **No claim validation pipeline**: Issuer validation is implicit via JJWT parser. Token type validation is inline in JwtAuthFilter (L57-77). No formal, testable, extensible chain.

**Source**: Research artifacts (`openspec/research/jwt_token_validation/`), codebase analysis of `JwtService.kt` (270 LOC), `JwtAuthFilter.kt` (96 LOC), `TokenController.kt` (66 LOC), `SecurityProperties.kt` (207 LOC), `TokenStorePersistenceAdapter.kt` (63 LOC).

**Related completed features**:
- `jwt_token_issuance` (archived: `archive/2026-08-25-jwt_token_issuance/`) — TokenIssuedEvent, TokenRevokedEvent, TokenEventRecorder patterns. Direct template for TokenValidationFailedEvent.
- `anonymous-login-optimization` (archived) — StringRedisTemplate usage patterns, Redis key conventions.

## Questions Asked & Answers

### Q1: Should the blacklist cache use base-cache-starter's TwoLevelCacheManager or a custom implementation?

**Analysis**:
- `TwoLevelCacheManager` is configured in `SecurityConfig.kt` (L108-117) but uses `ConcurrentMapCacheManager` for L1 (not Caffeine) and `l2CacheManager = null` (no L2 configured).
- `AbstractTwoTierCache` is referenced in research docs but does NOT exist in the source code (grep returns 0 matches).
- The blacklist needs SET-membership semantics (Redis `SISMEMBER`), not key-value cache semantics. Spring Cache `@Cacheable` returns cached values — it doesn't naturally model "is this JTI in a set?" boolean checks.
- `StringRedisTemplate` is used in 15+ injection points across the codebase — it's the established Redis access pattern.

**A**: **Custom implementation with direct Caffeine + StringRedisTemplate.** Reasons:
1. TwoLevelCacheManager's API is key-value oriented — not suited for SET membership checks.
2. L2 is null in current configuration — would need wiring anyway.
3. Direct control over circuit breaker, timeout, fallback logic is critical on the hot path.
4. Follows existing codebase patterns (StringRedisTemplate injection everywhere).
5. Caffeine can be instantiated programmatically without Spring Cache abstraction for maximum control.

### Q2: Should audience (aud) validation be mandatory immediately or phased in with feature flag?

**Analysis**:
- Current system is single-service — aud claim is not in generated tokens (JwtService.generateAccessToken() doesn't set aud).
- Enabling aud validation immediately would break all existing tokens (no aud claim → validation fails).
- Multi-service deployment is planned but not imminent.
- RFC 8725 recommends aud validation but acknowledges it requires ecosystem coordination.

**A**: **Feature-flagged, disabled by default.** New property `app.security.jwt.audience` (empty string = disabled). When set, two things happen: (1) `JwtService.generateAccessToken()` adds `aud` claim, (2) `AudienceClaimValidator` validates it. This ensures backward compatibility and allows phased rollout. Open OQ-002 from pre_openspec RESOLVED.

### Q3: What is the optimal L1 cache TTL — 15s or 30s?

**Analysis**:
- **15s TTL**: Higher Redis call frequency (~4x/min per JTI), shorter inconsistency window. A revoked token is accepted for max 15s.
- **30s TTL**: Lower Redis overhead, but 30s window where revoked token could be accepted.
- **Security SLA**: NFR-006 specifies "<30 seconds" acceptance window. Both values satisfy this.
- **Performance**: At 10K req/s, most JTIs are NOT blacklisted. L1 cache for "not blacklisted" results has high hit ratio because the same tokens are validated repeatedly. Short TTL means more L2 lookups for negative results.
- **Industry benchmark**: Auth0 uses 30s, Spring Security defaults to 60s for clock skew.

**A**: **30 seconds default, configurable.** Property: `app.security.blacklist.caffeine-ttl-seconds: 30`. Meets NFR-006 (<30s). Configurable for stricter environments. The difference between 15s and 30s is negligible for security (both are within one refresh cycle), but 30s reduces Redis load by ~50%. Open OQ-001 from pre_openspec RESOLVED.

### Q4: Should TokenBlacklistCacheService use Redis SET (SISMEMBER) or simple key-value (EXISTS)?

**Analysis**:
- **SET approach**: `SADD token_blacklist {jti}` + `SISMEMBER token_blacklist {jti}`. Single set for all blacklisted JTIs. Pro: O(1) membership check, single key. Con: Can't set per-JTI TTL — SET members don't expire individually. Would need external cleanup.
- **Key-value approach**: `SET token:blacklist:{jti} "1" EX {remaining_ttl}`. One key per blacklisted JTI. Pro: per-JTI TTL auto-eviction, Redis handles cleanup. Con: slightly more key space. `EXISTS` check is O(1).
- **Pre_openspec spec says**: "Redis SET (SISMEMBER)" — but this conflicts with "L2 TTL = remaining token lifetime" (BR-018) because SET members can't have individual TTLs.

**A**: **Key-value approach with per-key TTL.** `SET token:blacklist:{jti} "1" EX {remaining_seconds}`. Reasons:
1. Per-JTI TTL auto-eviction aligns with BR-018.
2. No external cleanup scheduler needed — Redis handles it.
3. `EXISTS` is O(1), same as SISMEMBER.
4. Key pattern `token:blacklist:{jti}` is namespace-clean.
5. Consistent with existing Redis key patterns in the codebase (e.g., `anon:session:{id}`, `otp:{userId}:{channel}`).

This corrects the pre_openspec's mention of SISMEMBER — the implementation should use key-value EXISTS instead. ⚠️ Assumption: pre_openspec FR-001 references SISMEMBER but implementation uses key-value EXISTS for TTL support.

### Q5: How should ClaimValidatorChain handle partial failures — fail-fast or collect-all?

**Analysis**:
- **Fail-fast**: Stop at first validator failure. Efficient but provides minimal error info.
- **Collect-all**: Run all validators, collect all failures. Better diagnostics but wastes cycles.
- **Context**: Validation runs on every request (hot path). Diagnostics matter for logging/events but not for the response (filter just skips auth).

**A**: **Fail-fast for the filter, collect-all for introspection.** The ClaimValidatorChain exposes two methods:
1. `validateOrThrow(claims: Claims)` — fail-fast, used in JwtAuthFilter (hot path).
2. `validateAll(claims: Claims): List<ClaimValidationResult>` — collect-all, used in introspection (diagnostic path).

Both backed by the same ordered list of `ClaimValidator` instances.

### Q6: Should validation failure events record ALL failures (including expired tokens) or only suspicious patterns?

**Analysis**:
- **All failures**: Complete audit trail. But expired tokens are routine (user walks away, token expires naturally) — generates noise.
- **Suspicious only**: Blacklisted tokens (revoked but still presented), signature invalid (potential attack), audience mismatch (token replay). These are actionable security signals.
- **Volume**: At 10K req/s, ~1-5% may have expired tokens = 100-500 events/s of noise. Blacklisted/invalid = <1 event/s.

**A**: **Record only suspicious patterns by default, configurable.** Default events recorded:
- `BLACKLISTED` — token was revoked but still presented (potential compromise)
- `SIGNATURE_INVALID` — tampered or forged token (attack indicator)
- `AUDIENCE_MISMATCH` — cross-service token replay attempt
- `TYPE_REJECTED` — MFA/refresh token used as access token

NOT recorded by default:
- `EXPIRED` — routine, too noisy
- `ISSUER_MISMATCH` — could be high-volume in misconfigured environments

Configurable via `app.security.validation.record-expired-events: false`. Open OQ-004 from pre_openspec RESOLVED.

### Q7: Should the JWKS key rotation support runtime hot-reload or config-based restart?

**Analysis**:
- **Hot-reload**: File watcher or admin API triggers key reload without restart. Complex, needs thread-safety.
- **Config-based**: Add `previousKeyPaths` to SecurityProperties, restart to pick up. Simple, aligns with current `lazy` key loading.
- **Current state**: `keyPair` is a `by lazy` val — loaded once, immutable. Adding a second key pair for overlap is straightforward.
- **Rotation frequency**: Monthly or on-demand (UC-003 says "Monthly or on-demand").

**A**: **Config-based with dual-key support.** For monthly rotation, restart is acceptable. The approach:
1. Add `previousPublicKeyPath` + `previousKeyId` to `SecurityProperties.JwtProperties`.
2. `JwtService` loads both key pairs (current + previous) at startup.
3. `getJwks()` returns both keys in JWKS response.
4. `parseToken()` tries current key first, then previous key, then HMAC fallback.
5. After overlap period (>7 days = max refresh token lifetime), remove previous key config and restart.

Hot-reload can be added later as a separate feature if rotation frequency increases. ⚠️ Assumption: Monthly key rotation does not require zero-restart capability.

### Q8: How should the circuit breaker for Redis blacklist lookups be configured?

**Analysis**:
- JwtAuthFilter runs on EVERY request. Redis failure must NOT cascade to service failure.
- Resilience4j is in the tech stack (spring-boot dependency).
- Existing patterns: `LoginRateLimitService` and `MfaRateLimitService` use `redisTimeoutMs: 500` for Redis operations.

**A**: **Resilience4j CircuitBreaker with conservative thresholds:**
```
failure-rate-threshold: 50%
slow-call-duration-threshold: 500ms
slow-call-rate-threshold: 80%
sliding-window-size: 10
wait-duration-in-open-state: 30s
```
When circuit is OPEN → skip Redis, go directly to DB fallback. When DB also fails → treat as "not blacklisted" (fail-open). This matches BR-007 (fail-open for filter) and BR-020 (fail-open on cache miss).

## Approaches Considered

### Approach A: Spring Security OAuth2 Resource Server Migration

**Description**: Replace custom `JwtAuthFilter` + `JwtService` with Spring Security's `BearerTokenAuthenticationFilter` + `NimbusJwtDecoder`. Use Spring's built-in `JwtTimestampValidator`, `JwtIssuerValidator`, `JwtClaimValidator` chain. Add custom validators for blacklist and token type.

**Pros**:
- Framework-maintained — security patches automatic
- Built-in JWKS caching and rotation via `NimbusJwtDecoder`
- Standard claim validators out of the box (iss, aud, exp, nbf)
- Community-tested at scale

**Cons**:
- **Migration risk**: Custom `JwtAuthFilter` handles anonymous tokens, type discrimination, blacklist check — Spring's filter doesn't support these natively
- **Filter chain conflict**: Both `JwtAuthFilter` and `BearerTokenAuthenticationFilter` would be in the chain — need careful ordering or full replacement
- **Loss of HMAC fallback**: Spring OAuth2 RS expects RS256 only (HMAC not standard for resource servers)
- **Anonymous token handling**: No Spring equivalent for `type=anonymous` → `ROLE_ANONYMOUS` mapping
- **Effort**: 8-12 dev-days (from comparison_analysis.md) vs 5-8 days for enhancement
- **Blast radius**: SecurityConfig, JwtAuthFilter, JwtService, all tests — high regression risk

**Score**: 4/10 — High risk, moderate reward. The existing system is well-structured; migration introduces more risk than it solves.

### Approach B: Custom TokenBlacklistCacheService with Direct Caffeine + StringRedisTemplate — Incremental Enhancement ✅ SELECTED

**Description**: Enhance the existing `JwtAuthFilter` + `JwtService` pipeline with:
1. New `TokenBlacklistCacheService` — Caffeine L1 + Redis L2 + DB fallback with circuit breaker
2. New `ClaimValidatorChain` — chain of responsibility with `IssuerClaimValidator`, `AudienceClaimValidator`, `TokenTypeClaimValidator`
3. Modified `JwtService` — clock skew tolerance, dual-key JWKS support, audience claim in token generation
4. Modified `JwtAuthFilter` — inject cache service + validator chain, structured logging, validation events
5. Modified `TokenController` — RFC 7662 compliant introspection, cache-based blacklist
6. New `TokenValidationFailedEvent` — following TokenIssuedEvent/TokenRevokedEvent patterns

**Architecture**:
```
┌────────────────────────────────────────────────────────────────────────┐
│                        JwtAuthFilter (modified)                        │
│                                                                        │
│  ┌──────────┐   ┌──────────────────┐   ┌──────────────────┐           │
│  │ Extract   │──▶│ JwtService       │──▶│ ClaimValidator   │           │
│  │ Bearer    │   │ .parseToken()    │   │ Chain            │           │
│  │ Token     │   │ (RS256→prev→HMAC)│   │ (iss,aud,type)   │           │
│  └──────────┘   │ +clockSkew(60s)  │   └──────┬───────────┘           │
│                  └──────────────────┘          │                       │
│                                                ▼                       │
│  ┌────────────────────────────────────────────────────────────────┐   │
│  │               TokenBlacklistCacheService                       │   │
│  │                                                                │   │
│  │   ┌──────────┐    ┌──────────────┐    ┌──────────────┐        │   │
│  │   │ Caffeine  │───▶│ Redis L2     │───▶│ PostgreSQL   │        │   │
│  │   │ L1 (30s)  │miss│ (per-JTI TTL)│miss│ (DB fallback)│        │   │
│  │   └──────────┘    └──────────────┘    └──────────────┘        │   │
│  │                    ▲ circuit breaker                            │   │
│  └────────────────────────────────────────────────────────────────┘   │
│                                                                        │
│  Result: SecurityContext(subject, authorities, details)                 │
└────────────────────────────────────────────────────────────────────────┘

Write Path (on revocation):
  TokenStorePersistenceAdapter.blacklistToken()
    ├── DB: tokenBlacklistRepository.save()          ← existing
    ├── Redis: SET token:blacklist:{jti} "1" EX ttl  ← NEW
    └── Caffeine: put(jti, true, 30s)                ← NEW
```

**Pros**:
- **Zero migration risk** — incremental changes to existing classes
- **Follows established patterns** — StringRedisTemplate (15+ usages), TokenEventRecorder pattern, SecurityProperties @ConfigurationProperties
- **100% feature coverage** — all 12 must-have features from comparison matrix
- **Predictable effort** — 5-8 dev-days (from comparison_analysis.md)
- **Full control** — custom circuit breaker, timeout, fallback logic tailored to critical path
- **Backward compatible** — audience validation disabled by default, clock skew adds tolerance (doesn't restrict)

**Cons**:
- Maintaining custom validation code (acceptable — team already maintains JwtService, JwtAuthFilter)
- Not leveraging Spring's built-in JWT validators (acceptable — custom validators are simpler and domain-specific)
- Caffeine dependency needs explicit management (mitigation: Caffeine is already available via base-cache-starter transitive dependency)

**Score**: 9/10 — Low risk, high reward, follows existing patterns.

### Approach C: Hybrid — Spring OAuth2 RS for JWKS + Custom for Blacklist

**Description**: Use `NimbusJwtDecoder` only for JWKS caching/rotation. Keep custom `JwtAuthFilter` for blacklist + type discrimination. Delegate signature verification to Nimbus, keep custom claim validators.

**Pros**:
- Best-in-class JWKS handling from Spring ecosystem
- Custom control over blacklist and type discrimination

**Cons**:
- Two JWT libraries (JJWT + Nimbus) in the stack — confusion, dependency bloat
- Nimbus decoder outputs `Jwt` object (Spring Security type), not JJWT `Claims` — interface mismatch
- Migration effort for parseToken() to use Nimbus internally
- JJWT already handles RS256 verification well — Nimbus adds no value for verification itself

**Score**: 5/10 — Unnecessary complexity for marginal benefit in JWKS handling. Custom dual-key support in JwtService is simpler.

## Selected Direction

**Approach B: Custom TokenBlacklistCacheService with Direct Caffeine + StringRedisTemplate — Incremental Enhancement of Existing JJWT Pipeline.**

This approach was selected because it:
1. Has the highest score (9/10) across all evaluation criteria
2. Carries the lowest migration/regression risk (incremental changes to existing well-structured code)
3. Provides 100% coverage of all 12 must-have features from the comparison matrix
4. Follows every established codebase pattern (StringRedisTemplate, TokenEventRecorder, SecurityProperties, hexagonal architecture)
5. Has the most predictable delivery timeline (5-8 dev-days)
6. Maintains full backward compatibility (all new features are additive or feature-flagged)

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Cache implementation | Direct Caffeine + StringRedisTemplate | SET membership needs custom logic; TwoLevelCacheManager API is key-value oriented (not SET) and L2=null |
| Redis data model | Key-value (EXISTS) not SET (SISMEMBER) | Per-JTI TTL auto-eviction needed; SET members can't have individual TTLs |
| L1 TTL | 30 seconds | Meets NFR-006 (<30s), reduces Redis load vs 15s, configurable |
| Audience validation | Feature-flagged, disabled by default | Backward compatible; no aud claim in existing tokens |
| Clock skew | 60 seconds default | Follows Spring Security convention, handles NTP drift |
| Key rotation | Config-based dual-key, not hot-reload | Monthly rotation doesn't justify hot-reload complexity |
| Claim chain mode | Fail-fast (filter) + collect-all (introspect) | Hot path needs speed; diagnostic path needs completeness |
| Validation events | Suspicious only (blacklisted, invalid sig, aud mismatch) | Expired tokens too noisy; configurable for full recording |
| Circuit breaker | Resilience4j on Redis, fail-open to DB | Critical path must not block on Redis failure |

## Pre-classifications (preliminary)
- Feature type: EXTEND (enhancing existing JwtAuthFilter + JwtService + TokenController)
- Flow type: Query (token validation is read-only per-request check; introspection is query endpoint)
- Affected modules:
  - `auth.application` — TokenBlacklistCacheService (NEW), ClaimValidator chain (NEW), JwtService (MODIFY)
  - `auth.domain.event` — TokenValidationFailedEvent (NEW)
  - `auth.adapter.in.web` — TokenController (MODIFY)
  - `auth.adapter.in.web.dto` — IntrospectionResponse (MODIFY)
  - `auth.adapter.out.persistence` — TokenStorePersistenceAdapter (MODIFY)
  - `shared.security` — JwtAuthFilter (MODIFY)
  - `shared.config` — SecurityProperties (MODIFY)

## GitNexus Findings (if explored)

GitNexus was not available for this session. Codebase exploration was done via grep_search and view_file.

### Key Symbols Analyzed
- `JwtAuthFilter` — 96 LOC, `shared.security` package. OncePerRequestFilter. Injects `JwtService` + `TokenBlacklistRepository`. Critical path.
- `JwtService` — 270 LOC, `auth.application` package. Token generation (access, refresh, MFA, anonymous) + parsing + JWKS. RS256 primary, HMAC fallback.
- `TokenController` — 66 LOC, `auth.adapter.in.web` package. Introspection (POST /api/auth/introspect), JWKS (GET /.well-known/jwks.json), session revocation.
- `TokenStorePersistenceAdapter` — 63 LOC, `auth.adapter.out.persistence` package. JPA adapter for TokenStore port. blacklistToken() writes to DB only.
- `SecurityProperties.JwtProperties` — data class with secretKey, algorithm, keyPaths, keyId, TTLs, issuer. No clock skew, no audience, no rotation config.
- `TokenEventRecorder` — 75 LOC, `auth.application.event` package. Helper for recordIssuance() and recordRevocation(). Template for recordValidationFailure().
- `EventService.record()` — 90 LOC. Transactional event recording: EventEnvelope → EventStore + Outbox.
- `TokenIssuedEvent` / `TokenRevokedEvent` — domain events implementing `DomainEvent` interface. Pattern for `TokenValidationFailedEvent`.
- `IntrospectionResponse` — data class with active, sub, username, roles, permissions, exp, iat, iss, jti. Missing RFC 7662 fields: token_type, scope, client_id.
- `TwoLevelCacheManager` (base-core) — configured in SecurityConfig with ConcurrentMapCacheManager L1, null L2. Not suitable for SET-based blacklist operations.

### Architecture Insights
- Clean Architecture (hexagonal ports/adapters) consistently applied
- CQRS command handlers (`TokenGenerator`, `RefreshTokenHandler`, `RenewAnonymousTokenHandler`)
- Event Sourcing via `EventService` → `EventStorePort` + `OutboxPort` → `OutboxPoller` → `KafkaEventPublisher`
- Redis used extensively for rate limiting, session data, OTP storage, anti-replay — StringRedisTemplate is the standard access pattern
- Caffeine available via base-cache-starter transitive dependency but not directly used for domain-specific caching

## Open Questions for Design Phase

- [RESOLVED] OQ-001: L1 cache TTL → 30 seconds (configurable). Meets NFR-006, reduces Redis load.
- [RESOLVED] OQ-002: Audience validation → feature-flagged, disabled by default.
- [OPEN] OQ-003: Does `base-cache-starter` expose Caffeine as a direct bean, or do we need to instantiate `Caffeine.newBuilder()` ourselves? Design phase should verify Caffeine dependency availability.
- [RESOLVED] OQ-004: Validation failure events → suspicious patterns only by default (blacklisted, signature_invalid, audience_mismatch, type_rejected). Configurable.
- [OPEN] OQ-005: Should `TokenBlacklistCacheService` implement a port interface (e.g., `TokenBlacklistChecker`) in `auth.application.port.out` for hexagonal purity, or is direct service injection acceptable? Design phase should decide based on test isolation needs.
- [OPEN] OQ-006: What Micrometer metrics should be emitted? Candidates: `token.validation.count` (by result), `token.blacklist.cache.hit` (by tier: l1/l2/db), `token.validation.duration` (histogram). Design phase should define metric names and tags.
- [OPEN] OQ-007: Should the dual-key rotation in JwtService change the parseToken() fallback order from `RS256(current) → HMAC` to `RS256(current) → RS256(previous) → HMAC`? This changes the migration window semantics. Design phase should clarify.

## Open Questions for URD Analysis

- No formal URD exists for this feature. Pre_openspec was generated from user idea + research analysis.
- If URD is needed, the business_analysis.md in research artifacts serves as the functional equivalent (6 use cases, 23 business rules, 9 functional requirements).

## Risk Analysis

```
┌─────────────────────────────────────────────────────────────────┐
│                    RISK HEAT MAP                                │
│                                                                 │
│  Impact ▲                                                       │
│    HIGH │  ●Redis critical     ○Key rotation                    │
│         │   path failure        race condition                  │
│         │                                                       │
│  MEDIUM │  ●L1 cache           ○HMAC fallback                  │
│         │   inconsistency       removal timing                  │
│         │                                                       │
│    LOW  │  ○Clock skew          ○Audience                      │
│         │   too generous         misconfiguration               │
│         └──────────────────────────────────────────────────▶    │
│           LOW          MEDIUM         HIGH      Probability     │
│                                                                 │
│  ● = Addressed by design   ○ = Acceptable residual risk        │
└─────────────────────────────────────────────────────────────────┘
```

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|------------|
| Redis unavailability on critical path | LOW | HIGH | Circuit breaker (Resilience4j) + L1 Caffeine cache (30s window) + DB fallback. Fail-open design. |
| L1 cache inconsistency (revoked token accepted) | MEDIUM | MEDIUM | 30s max window (configurable). Write-through on revocation writes to L1 immediately on same instance. Cross-instance: 30s eventual consistency. |
| Key rotation race condition | LOW | MEDIUM | Dual-key overlap period > max token lifetime (7 days for refresh). Both keys in JWKS during overlap. |
| Clock skew too generous (60s) | LOW | LOW | 60s follows Spring Security default. NTP-synced servers typically <1s drift. Configurable to tighten. |
| HMAC fallback removal timing | LOW | MEDIUM | 7-day migration window is configurable. Can extend if needed. Separate from key rotation. |

## Validation Data Flow (Complete)

```
                              REQUEST FLOW
                              ═══════════

  Client                JwtAuthFilter           JwtService
    │                       │                       │
    │ Authorization:        │                       │
    │ Bearer {token}        │                       │
    │──────────────────────▶│                       │
    │                       │                       │
    │                  ┌────┴────┐                  │
    │                  │ Extract │                  │
    │                  │ token   │                  │
    │                  └────┬────┘                  │
    │                       │                       │
    │                       │  parseToken(token)    │
    │                       │──────────────────────▶│
    │                       │                       │
    │                       │           ┌───────────┴───────────┐
    │                       │           │ 1. Try RS256(current) │
    │                       │           │ 2. Try RS256(previous)│
    │                       │           │ 3. Try HMAC(legacy)   │
    │                       │           │ 4. clockSkew(60s)     │
    │                       │           └───────────┬───────────┘
    │                       │                       │
    │                       │◀── Claims ────────────│
    │                       │                       │
    │                  ┌────┴────────────────┐
    │                  │ ClaimValidatorChain │
    │                  │ .validateOrThrow()  │
    │                  │  ├── IssuerValid.   │
    │                  │  ├── AudienceValid. │
    │                  │  └── TypeValidator  │
    │                  └────┬───────────────┘
    │                       │
    │                  ┌────┴──────────────────────────────────┐
    │                  │ TokenBlacklistCacheService             │
    │                  │ .isBlacklisted(jti)                    │
    │                  │                                        │
    │                  │  L1 Caffeine ──miss──▶ L2 Redis        │
    │                  │  (30s TTL)            (per-JTI TTL)    │
    │                  │                       │                │
    │                  │                       ──miss──▶ DB     │
    │                  │                       (fallback)       │
    │                  │                                        │
    │                  │  Circuit Breaker: Redis fail → DB      │
    │                  │  DB fail → treat as NOT blacklisted    │
    │                  └────┬─────────────────────────────────┘
    │                       │
    │                  ┌────┴────────────┐
    │                  │ if blacklisted: │──▶ 401 Unauthorized
    │                  │ else:           │
    │                  │  extract type   │
    │                  │  build auth     │
    │                  │  set context    │
    │                  └────┬────────────┘
    │                       │
    │◀──── filter chain ────│


                           WRITE-THROUGH PATH
                           ════════════════════

  Caller              TokenStorePersistence       Redis        Caffeine
    │                  Adapter                      │              │
    │ blacklistToken   │                            │              │
    │ (jti, userId,    │                            │              │
    │  reason, exp)    │                            │              │
    │─────────────────▶│                            │              │
    │                  │                            │              │
    │                  │── DB: save(entity) ──────▶ [PostgreSQL]   │
    │                  │                            │              │
    │                  │── Redis: SET key "1" EX ──▶│              │
    │                  │   token:blacklist:{jti}     │              │
    │                  │   (retry 2x on failure)     │              │
    │                  │                            │              │
    │                  │── Caffeine: put(jti,true) ────────────▶  │
    │                  │   (30s TTL)                │              │
    │                  │                            │              │
    │◀─── done ────────│                            │              │
```

## Implementation Ordering Strategy

The pre_openspec suggests this phased approach (Section 13, "Suggested Approach"), which aligns with dependency analysis:

```
Phase 1: Core Infrastructure (FR-001, FR-002)
  └── TokenBlacklistCacheService + write-through
       ├── Caffeine L1 instance (programmatic)
       ├── StringRedisTemplate L2 (injection)
       ├── TokenBlacklistRepository DB fallback (injection)
       └── Circuit breaker (Resilience4j)

Phase 2: Filter Migration (FR-003, FR-011)
  └── JwtAuthFilter + TokenController
       ├── Replace TokenBlacklistRepository → TokenBlacklistCacheService
       └── Backward compatible (same behavior, faster)

Phase 3: Claim Validation (FR-004, FR-009)
  └── ClaimValidatorChain + validators
       ├── ClaimValidator interface
       ├── IssuerClaimValidator
       ├── AudienceClaimValidator (disabled by default)
       ├── TokenTypeClaimValidator
       └── Integrate into JwtAuthFilter

Phase 4: Configuration Hardening (FR-005, FR-010, FR-013)
  └── SecurityProperties + JwtService
       ├── clockSkewSeconds (60s default)
       ├── audience property + feature flag
       └── aud claim in generateAccessToken()

Phase 5: Key Rotation (FR-006)
  └── JwtService + SecurityProperties
       ├── previousPublicKeyPath + previousKeyId
       ├── Dual-key JWKS response
       └── RS256(current) → RS256(previous) → HMAC fallback

Phase 6: Introspection Upgrade (FR-007, FR-008)
  └── TokenController + IntrospectionResponse
       ├── RFC 7662 fields (token_type, scope, client_id)
       ├── Claim validation in introspection
       └── ETag support for JWKS endpoint

Phase 7: Observability (FR-012, FR-014, FR-015, FR-016)
  └── TokenValidationFailedEvent + structured logging
       ├── Event class following TokenIssuedEvent pattern
       ├── TokenEventRecorder.recordValidationFailure()
       ├── Structured debug logging (jti, failureType, timestamp, remoteAddr)
       └── Redis timeout + retry configuration
```

## New/Modified Configuration Properties

```yaml
app:
  security:
    jwt:
      # ── Existing (unchanged) ──
      issuer: "auth-service"
      access-token-expiration-ms: 900000         # 15 min
      refresh-token-expiration-ms: 604800000     # 7 days
      absolute-ceiling-ms: 36000000              # 10 hours
      key-id: "auth-service-key-1"
      private-key-path: "/keys/private.pem"
      public-key-path: "/keys/public.pem"
      secret-key: ""                             # HMAC legacy

      # ── New properties ──
      clock-skew-seconds: 60                     # FR-005: Clock skew tolerance
      audience: ""                               # FR-010: Audience claim (empty = disabled)
      validate-audience: false                   # FR-010: Feature flag

      # Key rotation (FR-006)
      previous-public-key-path: ""               # Previous RSA public key for overlap
      previous-key-id: ""                        # Previous key ID

    # ── New section ──
    blacklist:
      caffeine-ttl-seconds: 30                   # FR-001: L1 cache TTL
      caffeine-max-size: 10000                   # FR-001: L1 max entries
      redis-enabled: true                        # FR-001: Enable Redis L2
      redis-key-prefix: "token:blacklist:"       # FR-001: Redis key pattern
      redis-timeout-ms: 500                      # FR-015: Redis lookup timeout
      retry-max-attempts: 2                      # FR-016: Write-through retry
      retry-delay-ms: 100                        # FR-016: Retry base delay

    validation:
      record-expired-events: false               # FR-012: Record expired token events
      record-issuer-mismatch-events: false       # FR-012: Record issuer mismatch events
```

## Classes Impact Summary

```
NEW FILES (7):
  auth/application/TokenBlacklistCacheService.kt    ── L1→L2→DB cache service
  auth/application/ClaimValidator.kt                ── Interface + ClaimValidationResult
  auth/application/IssuerClaimValidator.kt          ── iss == configured issuer
  auth/application/AudienceClaimValidator.kt        ── aud contains service ID (flagged)
  auth/application/TokenTypeClaimValidator.kt       ── type ∈ {null, access, anonymous}
  auth/application/ClaimValidatorChain.kt           ── Ordered chain, fail-fast + collect-all
  auth/domain/event/TokenValidationFailedEvent.kt   ── Validation failure domain event

MODIFIED FILES (5):
  shared/security/JwtAuthFilter.kt                  ── Cache service + validator chain
  shared/config/SecurityProperties.kt               ── New properties (blacklist, validation)
  auth/application/JwtService.kt                    ── Clock skew, dual-key, audience
  auth/adapter/in/web/TokenController.kt            ── RFC 7662, cache-based blacklist
  auth/adapter/out/persistence/TokenStorePersist...  ── Write-through (Redis + Caffeine)

MODIFIED DTOs (1):
  auth/adapter/in/web/dto/TokenDtos.kt              ── IntrospectionResponse RFC 7662 fields
```

## Event Naming Convention Alignment

Existing events use dot-separated naming:
- `iam.token.issued` (TokenIssuedEvent.eventType)
- `iam.token.revoked` (TokenRevokedEvent.eventType)

New event should follow the same pattern:
- `iam.token.validation-failed` (TokenValidationFailedEvent.eventType)

Note: pre_openspec FR-012 uses `iam.token.validation_failed` (underscore). The pre_openspec quality score flagged this inconsistency (Section 7, deduction #3). **Decision**: Use `iam.token.validation-failed` (hyphen) to match the kebab-case convention used in Kafka topic naming. The `eventType` field stays dot-separated for hierarchy (`iam.token.`) with hyphen for the action (`validation-failed`).

⚠️ Assumption: Kafka topic naming follows kebab-case convention for multi-word action suffixes.

## Token Validation State Machine

```
                     ┌─────────────────────────────────────┐
                     │        TOKEN VALIDATION              │
                     │        STATE MACHINE                 │
                     └─────────────────────────────────────┘

     ┌──────────┐     parse       ┌──────────┐
     │          │────────────────▶│          │
     │ RECEIVED │                 │ PARSED   │
     │          │◀─ fail ─ ─ ─ ─ │          │
     └──────────┘   (sig invalid) └────┬─────┘
                                       │
                                  validate claims
                                       │
                                  ┌────▼─────┐
                                  │ CLAIMS   │
                                  │ VALID    │
                                  └────┬─────┘
                                       │
                                  check blacklist
                                  (L1→L2→DB)
                                       │
                         ┌─────────────┼─────────────┐
                         │             │             │
                    ┌────▼─────┐  ┌────▼─────┐  ┌───▼──────┐
                    │BLACKLIST │  │ ACCEPTED │  │ CACHE    │
                    │ HIT     │  │          │  │ ERROR    │
                    │→ 401    │  │→ context │  │→ fallback│
                    └──────────┘  └──────────┘  └──────────┘
```

## Concurrency Considerations

1. **Caffeine thread safety**: Caffeine caches are thread-safe by design. No synchronization needed in `TokenBlacklistCacheService`.
2. **Redis operations**: `StringRedisTemplate` is thread-safe. Single operation per blacklist check (EXISTS or GET).
3. **JwtService key loading**: `by lazy` is thread-safe in Kotlin (LazyThreadSafetyMode.SYNCHRONIZED default). Adding `previousKeyPair` as another `by lazy` val is safe.
4. **SecurityContextHolder**: `ThreadLocal` by default. Filter sets per-thread. Correct for servlet model.
5. **Write-through race**: If two revocations happen simultaneously for the same JTI, both write to Redis (idempotent: SET is last-writer-wins with same value "1") and DB (entity with unique constraint on `token_jti` — second write gets constraint violation → catch and ignore).
