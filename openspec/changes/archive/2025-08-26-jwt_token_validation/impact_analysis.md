# Impact Analysis: jwt-token-validation

_Generated: 2025-08-26_
_Type: EXTEND_

---

## 1. Direct Impact (Files Modified)

| File | Action | FR | Risk |
|------|--------|-----|------|
| `shared/security/JwtAuthFilter.kt` | MODIFY | FR-003, FR-009, FR-012, FR-014 | 🔴 HIGH — critical path (every request) |
| `auth/application/JwtService.kt` | MODIFY | FR-005, FR-006, FR-013 | 🟡 MEDIUM — token parsing/signing |
| `auth/adapter/in/web/TokenController.kt` | MODIFY | FR-007, FR-008, FR-011 | 🟡 MEDIUM — API endpoints |
| `shared/config/SecurityProperties.kt` | MODIFY | FR-005, FR-006, FR-010 | 🟢 LOW — configuration only |
| `auth/adapter/in/web/dto/TokenDtos.kt` | MODIFY | FR-007 | 🟢 LOW — DTO addition |
| `auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt` | MODIFY | FR-002 | 🟡 MEDIUM — write path |
| `auth/application/event/TokenEventRecorder.kt` | MODIFY | FR-012 | 🟢 LOW — additive method |

## 2. New Files

| File | Module | FR |
|------|--------|-----|
| `auth/application/TokenBlacklistCacheService.kt` | auth.application | FR-001, FR-015, FR-016 |
| `auth/application/ClaimValidator.kt` | auth.application | FR-004 |
| `auth/application/ClaimValidationResult.kt` | auth.application | FR-004 |
| `auth/application/ClaimValidationException.kt` | auth.application | FR-004 |
| `auth/application/IssuerClaimValidator.kt` | auth.application | FR-004 |
| `auth/application/AudienceClaimValidator.kt` | auth.application | FR-004, FR-010 |
| `auth/application/TokenTypeClaimValidator.kt` | auth.application | FR-004 |
| `auth/application/ClaimValidatorChain.kt` | auth.application | FR-004, FR-009 |
| `auth/domain/event/TokenValidationFailedEvent.kt` | auth.domain.event | FR-012 |
| `auth/domain/event/ValidationFailureReason.kt` | auth.domain.event | FR-012 |

## 3. Blast Radius

### JwtAuthFilter (HIGHEST RISK)
- **Callers**: Every HTTP request through Spring Security filter chain
- **Changes**: Constructor params, blacklist check, claim validation, event recording, logging
- **Risk**: Any exception = auth bypass or service outage
- **Mitigation**: All new logic wrapped in try-catch, fail-open behavior preserved

### JwtService
- **Callers**: JwtAuthFilter, TokenController, AuthService, MfaService, AnonymousSessionHandler
- **Changes**: Clock skew, dual-key rotation, aud claim
- **Risk**: Parse failure = auth failure for all token types
- **Mitigation**: Clock skew is additive (more permissive), key rotation is fallback chain

### TokenController
- **Callers**: Internal microservices (introspection), JWKS consumers
- **Changes**: Dependency swap, RFC 7662 fields, ETag
- **Risk**: Response shape change (additive — new fields only)
- **Mitigation**: New fields are nullable, existing fields unchanged

## 4. Downstream Dependencies (Read-only)

| Component | Usage | Change |
|-----------|-------|--------|
| TokenBlacklistRepository | DB fallback in cache service | None (read-only reference) |
| EventService | Event recording via TokenEventRecorder | None |
| StringRedisTemplate | Redis operations in cache service | None (existing bean) |

## 5. Expected Scope

All changes confined to:
- `auth.application` (service layer)
- `auth.domain.event` (domain events)
- `auth.adapter.in.web` (controller + DTOs)
- `auth.adapter.out.persistence` (persistence adapter)
- `shared.security` (filter)
- `shared.config` (properties)

No changes to: `rbac` module (beyond read-only usage), `shared.exception`, database schema, Kafka infrastructure.
