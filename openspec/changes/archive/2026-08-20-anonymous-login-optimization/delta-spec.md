# Delta Spec: anonymous-login-optimization

_Generated: 2025-01-20_

> **Type**: MAINTENANCE — Post-Implementation Hardening
> **Scope**: Bug fixes (FIX-001, FIX-002), Observability (FIX-003), Tests (TEST-001, TEST-002)

---

## Behavioral Changes

### FIX-001: JTI Blacklisting (🔴 Critical Security Fix)

| Aspect | Before | After |
|--------|--------|-------|
| `LoginRequestDto` | No `anonymousToken` field | `val anonymousToken: String? = null` |
| `RegisterRequestDto` | No `anonymousToken` field | `val anonymousToken: String? = null` |
| `LoginCommand` | No `anonymousTokenJti` field | `val anonymousTokenJti: String? = null` |
| `RegisterCommand` | No `anonymousTokenJti` field | `val anonymousTokenJti: String? = null` |
| JTI passed to promotion | `anonymousJti = ""` (empty string) | `anonymousJti = command.anonymousTokenJti ?: ""` (real JTI) |
| `token_blacklist` entry | `tokenJti = ""` (useless) | `tokenJti = "<real-uuid>"` (correct) |
| Anonymous token after promotion | **Still valid** (NOT blacklisted) ❌ | **Blacklisted** (correctly rejected) ✅ |
| CqrsAuthController | No JTI extraction | `extractAnonymousTokenJti()` parses token, extracts JTI safely |

**Backward compatibility**: If client does not send `anonymousToken` → JTI defaults to `null` → falls back to `""` → same behavior as before (no regression).

### FIX-002: ThreadLocal Removal (🟡 Architecture Clean)

| Aspect | Before | After |
|--------|--------|-------|
| `RegisterHandler` return type | `CommandHandler<RegisterCommand, AuthToken>` | `CommandHandler<RegisterCommand, RegisterResult>` |
| Promotion result passing | `ThreadLocal<PromotionResult?>` + `lastPromotionResult` property | `RegisterResult.Success(authToken, promotionResult)` |
| Thread safety | ❌ Fragile with virtual threads / thread pools | ✅ Fully thread-safe |
| `CqrsAuthController.register()` | Read `registerHandler.lastPromotionResult` after `handle()` | Unwrap `RegisterResult.Success` directly |

### FIX-003: Observability Metrics (🟡 Operational Gap)

| Metric | Previously | Now |
|--------|-----------|-----|
| Session creation count | Not tracked | `auth.anonymous.sessions.created` (Counter) |
| Token renewal count | Not tracked | `auth.anonymous.sessions.renewed` (Counter) |
| Promotion outcomes | Not tracked | `auth.anonymous.sessions.promoted` (Counter, tagged by status) |
| Rate limit rejections | Not tracked | `auth.anonymous.rate_limited` (Counter) |
| Data store operations | Not tracked | `auth.anonymous.data.stored` (Counter) |
| Data size exceeded | Not tracked | `auth.anonymous.data.size_exceeded` (Counter) |
| Promotion duration | Not tracked | `auth.anonymous.promotion.duration` (Timer) |
| Token generation duration | Not tracked | `auth.anonymous.token.generation.duration` (Timer) |

---

## API Impact

### Request Body Changes

**POST /api/auth/login** — new optional field:
```json
{
  "username": "...",
  "password": "...",
  "anonymousSessionId": "...",
  "anonymousToken": "eyJ..."  // ← NEW (optional)
}
```

**POST /api/auth/register** — new optional field:
```json
{
  "username": "...",
  "email": "...",
  "password": "...",
  "fullName": "...",
  "anonymousSessionId": "...",
  "anonymousToken": "eyJ..."  // ← NEW (optional)
}
```

**Response format**: No changes. `promotedFromAnonymous` and `dataTransferred` fields already exist.

### Breaking Changes

**None.** All new fields are optional with `null` defaults. Existing clients work without modification.

---

## Test Coverage Added

| Test File | Type | Test Count | Coverage |
|-----------|------|-----------|----------|
| `JwtServiceAnonymousTest.kt` | Unit | 6 | FR-001, FR-008 |
| `AnonymousSessionDataServiceTest.kt` | Unit | 7 | FR-004, FR-006, FR-007 |
| `SessionPromotionServiceTest.kt` | Unit | 7 | FR-003, FR-004, FR-005, FR-010 |
| `AnonymousRateLimitServiceTest.kt` | Unit | 5 | FR-009 |
| `AnonymousSessionIntegrationTest.kt` | Integration | 5 | FR-001, FR-002, FR-008, FR-009 |
| `SessionPromotionIntegrationTest.kt` | Integration | 7 | FR-003, FR-004, FR-005, FR-010, FR-013 |
| **Total** | | **37** | **13/13 FRs** |
