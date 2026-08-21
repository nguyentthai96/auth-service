# Proposal: jwt-token-validation

> **Change**: jwt_token_validation | **Type**: EXTEND | **Flow**: Query
> **Direction**: Custom TokenBlacklistCacheService with Direct Caffeine + StringRedisTemplate — Incremental Enhancement of Existing JJWT Pipeline (from brainstorm)
> **_Generated**: 2025-08-26_

## Changes

- **auth.application [NEW]**: `TokenBlacklistCacheService.kt` — three-level blacklist lookup (L1 Caffeine → L2 Redis EXISTS → DB fallback). Write-through on revocation. Circuit breaker on Redis. (FR-001, FR-002, FR-015, FR-016)
- **auth.application [NEW]**: `ClaimValidator.kt` — interface with `validate(claims: Claims): ClaimValidationResult`. (FR-004)
- **auth.application [NEW]**: `IssuerClaimValidator.kt` — validates `iss` claim against `SecurityProperties.jwt.issuer`. (FR-004)
- **auth.application [NEW]**: `AudienceClaimValidator.kt` — validates `aud` claim when `SecurityProperties.jwt.audience` is non-empty; skip when empty (backward compatible). (FR-004, FR-010)
- **auth.application [NEW]**: `TokenTypeClaimValidator.kt` — rejects `mfa` and `refresh` type tokens used as access tokens; allows `null`, `access`, `anonymous`. (FR-004)
- **auth.application [NEW]**: `ClaimValidatorChain.kt` — @Component collecting all `ClaimValidator` beans. Exposes `validateOrThrow()` (fail-fast for filter) and `validateAll()` (collect-all for introspection). (FR-004)
- **auth.application [NEW]**: `ClaimValidationResult.kt` — data class: `validatorName`, `status` (PASS/FAIL), `reason`. (FR-004)
- **auth.domain.event [NEW]**: `TokenValidationFailedEvent.kt` — domain event for suspicious validation failures (blacklisted, signature_invalid, audience_mismatch, type_rejected). (FR-012)
- **auth.application.event [MODIFY]**: `TokenEventRecorder.kt` — add `recordValidationFailure()` method following existing `recordIssuance()`/`recordRevocation()` pattern. (FR-012)
- **shared.security [MODIFY]**: `JwtAuthFilter.kt` — inject `TokenBlacklistCacheService` (replaces `TokenBlacklistRepository`), inject `ClaimValidatorChain`, integrate claim validation after parse. (FR-003, FR-009)
- **auth.application [MODIFY]**: `JwtService.kt` — add clock skew tolerance via `allowedClockSkewSeconds()`, support dual-key RS256 rotation (current + previous), update `getJwks()` to return multiple keys. (FR-005, FR-006)
- **shared.config [MODIFY]**: `SecurityProperties.kt` — add `clockSkewSeconds`, `audience`, `previousPublicKeyPath`, `previousKeyId` to `JwtProperties`. Add `BlacklistCacheProperties` nested class. Add `ValidationEventProperties` nested class. (FR-005, FR-006, FR-010, FR-012, FR-015)
- **auth.adapter.in.web [MODIFY]**: `TokenController.kt` — inject `TokenBlacklistCacheService` (replaces `TokenBlacklistRepository`), integrate `ClaimValidatorChain` in introspect(), add RFC 7662 fields, add ETag support to jwks(). (FR-007, FR-008, FR-011)
- **auth.adapter.in.web.dto [MODIFY]**: `TokenDtos.kt` — add `tokenType`, `scope`, `clientId` fields to `IntrospectionResponse` per RFC 7662. (FR-007)
- **auth.adapter.out.persistence [MODIFY]**: `TokenStorePersistenceAdapter.kt` — add Redis + Caffeine write-through in `blacklistToken()`. (FR-002)
- **auth.application [MODIFY]**: `JwtService.kt` — add `aud` claim to `generateAccessToken()` when `audience` property is configured. (FR-013)

**Total**: ~8 new files, ~7 modified files. 16 FRs. ~5-8 developer-days effort.

---

## 1. Executive Summary

Nâng cấp JWT token validation pipeline trong auth-service lên production-grade theo RFC 8725 BCP và RFC 7662. Hiện tại, `JwtAuthFilter` (96 LOC) thực hiện validation cơ bản: parse token RS256/HMAC, check blacklist qua `TokenBlacklistRepository.existsByTokenJti()` — **query DB trực tiếp trên mỗi request** gây bottleneck tại throughput cao (>500 req/s). Feature này bao gồm 6 major enhancements:

1. **Two-tier blacklist cache** (Caffeine L1 → Redis L2 → DB fallback) — giảm latency từ ~1-5ms (DB) xuống ~0.01ms (L1 cache hit)
2. **Claim validation pipeline** (chain of responsibility) — formal, testable, extensible validators cho iss, aud, exp, nbf, type
3. **JWKS key rotation** — dual-key overlap strategy cho zero-downtime RS256 key rotation
4. **RFC 7662 introspection** — thêm `token_type`, `scope`, `client_id` fields
5. **Clock skew tolerance** — configurable 60s default theo RFC 8725
6. **Validation failure events** — suspicious pattern recording cho audit trail (blacklisted, signature_invalid, audience_mismatch, type_rejected)

### Business Value
- **Performance**: L1 cache hit ~99%+ trên steady-state → near-zero latency blacklist check
- **Security hardening**: RFC 8725 compliant claim validation, audience validation (feature-flagged), key rotation support
- **Compliance**: Validation failure events → audit trail cho suspicious patterns (SOC2 CC7.1)
- **Operational**: Configurable via application.yml, zero-code changes for tuning
- **Backward compatible**: All new features are additive or feature-flagged

### Key Metrics
| Metric | Value |
|--------|-------|
| Functional Requirements | 16 (Idea: 12, Enriched: 4) |
| New Kotlin files | ~8 |
| Modified files | ~7 |
| New DB tables | 0 |
| New Kafka topics | 1 (iam.token.validation-failed) |
| Estimated effort | 5-8 developer-days |

---

## 2. Problem Statement

auth-service hiện tại có 6 gaps trong JWT token validation:

1. **DB blacklist bottleneck**: `JwtAuthFilter` gọi `tokenBlacklistRepository.existsByTokenJti(jti)` trên **mỗi authenticated request**. PostgreSQL indexed query ~1-5ms. Tại >500 req/s → connection pool exhaustion, latency spike.

2. **Missing claim validation pipeline**: Issuer validation implicit trong JJWT parser. Audience validation không có — tokens issued cho một service có thể replay sang service khác trong multi-service environment. Token type check inline tại JwtAuthFilter L57-77 — not testable, not extensible.

3. **Zero clock skew tolerance**: `JwtService.parseToken()` dùng JJWT default 0-second clock skew. Distributed environments với NTP drift → valid tokens bị falsely rejected.

4. **No JWKS key rotation**: `JwtService` load single RSA key pair qua `by lazy` val — immutable. Rotating keys requires application restart. Zero-downtime rotation impossible.

5. **Non-standard introspection**: `TokenController.introspect()` trả custom response thiếu RFC 7662 fields (`token_type`, `scope`, `client_id`).

6. **No validation failure audit**: Invalid/suspicious token presentations (blacklisted tokens still presented, tampered tokens, cross-service replay) không được recorded. SIEM/SOC systems không có signals.

---

## 3. Scope Definition

### In Scope — This Feature
| FR | Description | Priority |
|----|-------------|----------|
| FR-001 | Two-tier blacklist cache service (Caffeine → Redis → DB) | P0 |
| FR-002 | Blacklist cache write-through on revocation | P0 |
| FR-003 | JwtAuthFilter migrate to cache-based blacklist | P0 |
| FR-004 | Claim validation pipeline (ClaimValidator chain) | P0 |
| FR-005 | Clock skew tolerance configuration | P1 |
| FR-006 | JWKS key rotation — dual-key overlap | P1 |
| FR-007 | Introspection endpoint upgrade RFC 7662 | P1 |
| FR-008 | JWKS endpoint ETag support | P2 |
| FR-009 | JwtAuthFilter integrate ClaimValidatorChain | P0 |
| FR-010 | Audience claim configuration | P1 |
| FR-011 | TokenController use cache-based blacklist | P1 |
| FR-012 | Validation failure event recording | P1 |
| FR-013 | Add aud claim to token generation when audience configured | P1 |
| FR-014 | Structured logging for validation failures | P2 |
| FR-015 | Redis timeout handling for cache lookup | P0 |
| FR-016 | Retry mechanism for cache write failures | P2 |

### Out of Scope — Future Features
| Item | Reason |
|------|--------|
| Token blacklist cleanup/eviction scheduler | Operational concern — Redis TTL auto-evicts |
| JWKS hot-reload (runtime file watcher) | Monthly rotation doesn't justify complexity |
| Audience validation mandatory mode | Requires ecosystem coordination — deferred |
| Full validation event recording (incl. expired) | Too noisy — configurable in future |
| Token validation read projections (CQRS query) | Separate feature — query side |
| Distributed blacklist sync (multi-region) | Infrastructure concern — deferred |

---

## 4. Architecture Decision

### Selected: Approach B — Custom TokenBlacklistCacheService with Direct Caffeine + StringRedisTemplate

**Rationale** (from brainstorm, score 9/10):
1. **SET membership semantics**: Blacklist check is "is this JTI in a set?" — not key-value cache. `TwoLevelCacheManager` API is key-value oriented with `ConcurrentMapCacheManager` L1 and null L2.
2. **Per-JTI TTL**: Redis key-value with `EX` flag auto-evicts when token expires. SET members can't have individual TTLs.
3. **Established patterns**: `StringRedisTemplate` used in 15+ injection points across the codebase (OtpService, MfaRateLimitService, AnonymousSessionHandler, etc.).
4. **Critical path control**: Custom circuit breaker, timeout, fallback logic on hot path (every request).
5. **100% feature coverage**: All must-have features from comparison matrix.
6. **Backward compatible**: All new features are additive or feature-flagged.

### Rejected Alternatives
- **Approach A** (Spring OAuth2 Resource Server / Nimbus migration): Full migration from JJWT to NimbusJwtDecoder — 7/10 score. High migration risk, breaks existing MFA/anonymous token type discrimination logic, requires rewrite of `JwtService` (270 LOC).
- **Approach C** (Hybrid Nimbus + Custom): Two JWT libraries (JJWT + Nimbus) — 5/10 score. Unnecessary complexity, dependency bloat, interface mismatch between Nimbus `Jwt` and JJWT `Claims`.

---

## 5. Impact Summary

### Direct Impact (🔴)
| File | Action | FR |
|------|--------|----|
| `JwtAuthFilter.kt` | [MODIFY] Replace `TokenBlacklistRepository` → `TokenBlacklistCacheService`, add `ClaimValidatorChain` | FR-003, FR-009 |
| `JwtService.kt` | [MODIFY] Clock skew, dual-key rotation, aud claim | FR-005, FR-006, FR-013 |
| `TokenController.kt` | [MODIFY] Cache-based blacklist, RFC 7662, ETag | FR-007, FR-008, FR-011 |
| `SecurityProperties.kt` | [MODIFY] New JWT properties + nested config classes | FR-005, FR-006, FR-010, FR-012, FR-015 |
| `TokenStorePersistenceAdapter.kt` | [MODIFY] Write-through to Caffeine + Redis layers | FR-002 |
| `TokenDtos.kt` | [MODIFY] RFC 7662 response fields | FR-007 |
| `TokenEventRecorder.kt` | [MODIFY] Add `recordValidationFailure()` | FR-012 |

### New Files
| File | Module | FR |
|------|--------|----|
| `TokenBlacklistCacheService.kt` | auth.application | FR-001, FR-015, FR-016 |
| `ClaimValidator.kt` | auth.application | FR-004 |
| `ClaimValidationResult.kt` | auth.application | FR-004 |
| `IssuerClaimValidator.kt` | auth.application | FR-004 |
| `AudienceClaimValidator.kt` | auth.application | FR-004, FR-010 |
| `TokenTypeClaimValidator.kt` | auth.application | FR-004 |
| `ClaimValidatorChain.kt` | auth.application | FR-004, FR-009 |
| `TokenValidationFailedEvent.kt` | auth.domain.event | FR-012 |

### Shared Utilities (🟢 — read-only usage)
| File | Usage |
|------|-------|
| `EventService.kt` | Delegates event recording via `TokenEventRecorder` |
| `TokenBlacklistRepository` | Used as DB fallback tier in `TokenBlacklistCacheService` |
| `DomainEvent` interface | Implemented by `TokenValidationFailedEvent` |

---

## 6. Risk Assessment

| # | Risk | Severity | Mitigation |
|---|------|----------|------------|
| 1 | Redis unavailable on critical path (JwtAuthFilter runs per request) | 🟡 Medium | Circuit breaker fail-open to DB. L1 Caffeine cache covers ~99% reads. Timeout 200ms. |
| 2 | L1 cache inconsistency window (up to 30s) — revoked token accepted | 🟡 Medium | Acceptable tradeoff — documented SLA. NFR-006 specifies <30s. |
| 3 | Key rotation overlap period too short → tokens rejected | 🟡 Medium | Overlap period MUST exceed max token lifetime (7d for refresh tokens). Config-validated. |
| 4 | Caffeine dependency availability | 🟢 Low | Available via base-cache-starter transitive dependency. Verified in brainstorm. |
| 5 | Audience validation breaks existing tokens | 🟢 Low | Feature-flagged, disabled by default. Only activates when `app.security.jwt.audience` is non-empty. |

---

## 7. Traceability

| FR | Proposal Section | SRS Section | Design Section | Task |
|----|-----------------|-------------|----------------|------|
| FR-001 | Changes #1 | §3.1 | §2.1 | T1 |
| FR-002 | Changes #1 | §3.1 | §2.2 | T2 |
| FR-003 | Changes #10 | §3.1 | §2.3 | T7 |
| FR-004 | Changes #2-7 | §3.2 | §3.1-3.5 | T3-T6 |
| FR-005 | Changes #11 | §3.3 | §4.1 | T9 |
| FR-006 | Changes #11 | §3.3 | §4.2 | T10 |
| FR-007 | Changes #13 | §3.4 | §5.1 | T12 |
| FR-008 | Changes #13 | §3.4 | §5.2 | T13 |
| FR-009 | Changes #10 | §3.2 | §3.6 | T8 |
| FR-010 | Changes #12 | §3.3 | §4.3 | T11 |
| FR-011 | Changes #13 | §3.4 | §5.1 | T12 |
| FR-012 | Changes #8-9 | §3.5 | §6.1 | T14 |
| FR-013 | Changes #16 | §3.3 | §4.3 | T11 |
| FR-014 | Changes #10 | §3.5 | §6.2 | T15 |
| FR-015 | Changes #1 | §3.1 | §2.1 | T1 |
| FR-016 | Changes #1 | §3.1 | §2.2 | T2 |
