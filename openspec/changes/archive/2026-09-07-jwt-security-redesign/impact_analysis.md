# Impact Analysis: jwt-security-redesign

> **Type**: EXTEND | **Generated**: 2026-09-07 | **Source**: GitNexus + grep verification

## 1. Core Files (Modify Target)

| # | File | Risk | Direct Callers (d=1) | Action |
|---|------|------|---------------------|--------|
| 1 | [JwtService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt) | 🟡 MEDIUM | 8 (JwtAuthFilter, TokenGenerator, RenewAnonymousTokenHandler, AnonymousSessionHandler, TokenController, CqrsAuthController, AuthController, AnonymousAuthController) | Add `deviceFingerprint` param to `generateAccessToken()` — backward-compatible via default param |
| 2 | [JwtAuthFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt) | 🟢 LOW | 1 (SecurityConfig) | Add fingerprint validation step 4 — new constructor param `FingerprintService` |
| 3 | [LoginSessionService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt) | 🟡 MEDIUM | 5 (LoginHandler, SessionController, CqrsAuthController, AuthController, AdminSessionController) | Add `kickDevice()`, `listDevicesForUser()`, `kickAllOtherDevices()` — new methods, no signature change |
| 4 | [LoginSessionEntity.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/LoginSessionEntity.kt) | 🟢 LOW | 2 (LoginSessionService, LoginSessionRepository) | Add `accessTokenJti`, `deviceName` columns — nullable, backward-compatible |
| 5 | [SecurityProperties.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | 🟢 LOW | additive | Add nested `FingerprintProperties`, `MailProperties` — additive, no break |
| 6 | [SecurityConfig.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt) | 🟢 LOW | 0 (config class) | Add `/api/auth/devices/**` permission — additive |

## 2. Call Tree (d=1 → d=2)

```
JwtService (d=0)
├── JwtAuthFilter (d=1) → SecurityConfig (d=2)
├── TokenGenerator (d=1) → BuildAuthResponseHandler (d=2)
├── RenewAnonymousTokenHandler (d=1)
├── AnonymousSessionHandler (d=1)
├── TokenController (d=1)
├── CqrsAuthController (d=1)
├── AuthController (d=1)
└── AnonymousAuthController (d=1)

LoginSessionService (d=0)
├── LoginHandler (d=1)
├── SessionController (d=1)
├── CqrsAuthController (d=1)
├── AuthController (d=1)
└── AdminSessionController (d=1)

JwtAuthFilter (d=0)
└── SecurityConfig (d=1)
```

## 3. Blast Radius Assessment

| Symbol | Depth=1 | Risk | Mitigation |
|--------|---------|------|------------|
| JwtService | 8 callers | 🟡 MEDIUM | Use Kotlin default params: `generateAccessToken(..., deviceFingerprint: String? = null)` → ALL existing callers unaffected |
| LoginSessionService | 5 callers | 🟡 MEDIUM | Add NEW methods only, ZERO signature changes to existing methods |
| JwtAuthFilter | 1 caller | 🟢 LOW | Add constructor param + validation step. SecurityConfig auto-wired. |
| LoginSessionEntity | 2 callers | 🟢 LOW | Add nullable columns. No impact on existing queries. |
| SecurityProperties | additive | 🟢 LOW | Add nested data classes. Zero impact on existing properties. |

**Overall Risk: 🟡 MEDIUM** — JwtService has 8 callers, but all changes are backward-compatible via Kotlin default parameters.

## 4. Reuse Map

| Logic Block | Escalation Result | Decision | Source |
|-------------|------------------|----------|--------|
| FingerprintService | Not found (NEW) | [NEW] | Create `auth.application.FingerprintService` |
| MailQueueEntity | Not found (NEW) | [NEW] | Create, follow `EventOutboxEntity` pattern |
| MailTemplateEntity | Not found (NEW) | [NEW] | Create, follow `SnowflakePersistentAuditableEntity` pattern |
| MailQueueService | Not found (NEW) | [NEW] | Create, reuse `OutboxPersistenceAdapter` pattern |
| MailJobScheduler | 80% match: `OutboxPoller` | [EXTRACT pattern] | Mirror `OutboxPoller.processOutbox()` structure → `processMailQueue()` |
| NewDeviceMailHandler | Pattern exists: `@TransactionalEventListener` in codebase | [REUSE pattern] | Follow existing event listener pattern |
| DeviceController | Not found (NEW) | [NEW] | Create, follow `SessionController` structure |
| DeviceResponse DTO | 50% match: `SessionResponse` | [NEW] | Create new DTO, superset of SessionResponse |
| kickDevice logic | Not found (NEW method) | [NEW] | Add to `LoginSessionService` |
| Session-Token sync | Partial: `TokenBlacklistCacheService.addToBlacklist()` exists | [REUSE + COMPOSE] | Compose: `revokeSession()` + `addToBlacklist()` + `revokeRefreshToken()` |

## 5. Context Snapshot

```
Date: 2026-09-07
Classification: EXTEND
Confidence: 1.0/1.0
FRs: 23 (URD: 19, Enriched: 4)
GitNexus Index: auth-service (10589 symbols, 16904 relationships)
New Files: 10 (FingerprintService, MailQueueEntity, MailTemplateEntity, MailQueueService, MailJobScheduler, NewDeviceMailHandler, DeviceController, DeviceResponse, MailQueueRepository, MailTemplateRepository)
Modified Files: 9 (JwtService, JwtAuthFilter, LoginSessionService, LoginHandler, RefreshTokenHandler, LoginSessionEntity, SecurityProperties, SecurityConfig, TokenStore)
Reuse: 1 (TokenEventRecorder)
HIGH risk items: 0
MEDIUM risk items: 2 (JwtService 8 callers, LoginSessionService 5 callers)
Mitigation: Kotlin default params + additive methods only
```
