# Design: JWT OAuth2 Security Redesign

> Validated design document — kết quả brainstorm session 2026-09-07.
> Dựa trên research artifacts (`openspec/research/jwt-security-redesign/`) và codebase exploration (GitNexus).

## 1. Overview

Enhance hệ thống JWT OAuth2 auth-service hiện tại với 5 core components mới:

| Component | Purpose | Pattern |
|-----------|---------|---------|
| **FingerprintService** | Compute/validate device fingerprint | Strategy (client vs server) |
| **MailQueueService + MailJobScheduler** | Async email delivery | Transactional Outbox |
| **NewDeviceMailHandler** | Bridge: new device event → mail queue | Observer/Event |
| **DeviceController** | Device management REST API | REST + CQRS |
| **Session-Token Sync** | Unified lifecycle management | Eager sync, @Transactional |

## 2. Architecture

### 2.1 Filter Chain (No Change to Order)

```
Request → LoginRateLimitFilter → JwtAuthFilter (ENHANCED) → ServiceAuthFilter → Controller
                                      │
                                      ├── 1. Parse JWT
                                      ├── 2. Check blacklist (JTI)
                                      ├── 3. Validate claims
                                      ├── 4. Validate fingerprint [NEW]
                                      └── 5. Set SecurityContext
```

> **DD-002**: Fingerprint validation integrated INTO JwtAuthFilter (step 4). NO separate filter — fingerprint needs parsed JWT claim.

### 2.2 Component Dependency Graph

```
┌──────────────────────────────────────────────────────────┐
│                    NEW COMPONENTS                         │
│                                                          │
│  FingerprintService ──┐                                  │
│  (pure computation)    ├──→ JwtAuthFilter (step 4)       │
│                       └──→ LoginHandler (generate JWT)   │
│                                                          │
│  MailQueueEntity ──┐                                     │
│  MailTemplateEntity ├──→ MailQueueService (enqueue)       │
│                    └──→ MailJobScheduler (poll + send)    │
│                                                          │
│  NewDeviceMailHandler                                     │
│  (event → mail queue)                                    │
│                                                          │
│  DeviceController                                        │
│  (REST: list, kick, kick all)                            │
│                                                          │
├──────────────────────────────────────────────────────────┤
│                   MODIFIED COMPONENTS                     │
│                                                          │
│  JwtService         → add deviceFingerprint param        │
│  JwtAuthFilter      → add fingerprint validation step    │
│  LoginHandler       → integrate FingerprintService       │
│  LoginSessionService → add kickDevice, listDevicesForUser│
│  LoginSessionEntity → add accessTokenJti, deviceName     │
│  SecurityProperties → add FingerprintProperties, MailProps│
│  SecurityConfig     → add device endpoint permissions     │
│  TokenStore         → add revokeByRefreshTokenId         │
│  RefreshTokenHandler → blacklist old access JTI          │
└──────────────────────────────────────────────────────────┘
```

## 3. Components Detail

### 3.1 FingerprintService

**Pattern**: Strategy (client-provided vs server-computed)

```kotlin
@Service
class FingerprintService(securityProperties: SecurityProperties) {

    fun resolveFingerprint(request: HttpServletRequest): String
    // Priority: X-Device-Fingerprint header → server-computed fallback

    fun validateFingerprint(claimFingerprint: String, requestFingerprint: String): Boolean
    // Exact match comparison

    private fun computeServerFingerprint(request: HttpServletRequest): String
    // SHA-256(User-Agent + Accept-Language + IP-Prefix/24 + SEC-CH-UA)
}
```

**Configuration** (SecurityProperties.FingerprintProperties):
```yaml
app.security.fingerprint:
  enabled: true
  validation-enabled: true    # Validate per-request
  strict-mode: false          # false=warn, true=reject
  header-name: X-Device-Fingerprint
  server-compute-fallback: true
```

### 3.2 MailQueueService + MailJobScheduler

**Pattern**: Transactional Outbox (mirroring OutboxPoller)

```kotlin
// Service — enqueue within existing @Transactional
@Service
class MailQueueService(
    mailQueueRepository: MailQueueRepository,
    mailTemplateRepository: MailTemplateRepository
) {
    fun enqueue(recipient: String, templateCode: String, templateData: Map<String, Any>, createdBy: Long? = null): MailQueueEntity
    fun renderTemplate(template: MailTemplateEntity, data: Map<String, Any>): RenderedMail
}

// Scheduler — poll and send (mirrors OutboxPoller pattern)
@Component
class MailJobScheduler(
    mailQueueRepository: MailQueueRepository,
    mailTemplateRepository: MailTemplateRepository,
    javaMailSender: JavaMailSender,
    meterRegistry: MeterRegistry
) {
    @Scheduled(fixedDelayString = "\${app.security.mail.queue.poll-interval-ms:5000}")
    @Transactional
    fun processMailQueue()
    // SELECT ... FOR UPDATE SKIP LOCKED
    // Render template → send SMTP → update status
    // Retry: exponential backoff (30s * 2^retryCount)
}
```

**Entity** (extends SnowflakePersistentAuditableEntity):
```
mail_queue:
  id, recipient, subject, template_code, template_data (JSONB),
  body_rendered, status (PENDING/PROCESSING/SENT/FAILED),
  retry_count, max_retries, error_message,
  created_at, sent_at, next_retry_at, created_by

mail_templates:
  id, code (UNIQUE), name, subject_template, body_template,
  language, active, created_at, updated_at
```

### 3.3 NewDeviceMailHandler

```kotlin
@Component
class NewDeviceMailHandler(
    mailQueueService: MailQueueService,
    userPort: UserPort
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleNewDeviceLogin(event: NewDeviceLoginEvent)
    // Lookup user email → enqueue "NEW_DEVICE_LOGIN" template
}
```

> Uses `AFTER_COMMIT` to ensure login transaction succeeded before enqueuing mail.

### 3.4 DeviceController

```
GET    /api/auth/devices           → list devices (with isCurrent marker)
DELETE /api/auth/devices/{sessionId} → kick specific device
DELETE /api/auth/devices           → kick all OTHER devices
```

**DeviceResponse**:
```json
{
  "sessionId": 123,
  "deviceType": "DESKTOP",
  "browserName": "Chrome",
  "osName": "Windows",
  "ipAddress": "192.168.1.xxx",
  "loginAt": "2026-09-07T10:00:00Z",
  "lastActivityAt": "2026-09-07T10:25:00Z",
  "deviceName": "Chrome on Windows",
  "isCurrent": true
}
```

### 3.5 Session-Token Lifecycle Sync (Eager)

**DD-005**: All revocation operations are **eager** — executed atomically in same `@Transactional`:

```
kickDevice(sessionId):
  1. revokeSession(sessionId, "KICKED_BY_USER")
  2. blacklistJTI(session.accessTokenJti, remainingTTL)    ← L1+L2+DB
  3. revokeRefreshToken(session.refreshTokenId)
  4. enqueueNotification("DEVICE_KICKED")                  ← optional

logout():
  1. revokeSession(currentSessionId, "LOGOUT")
  2. blacklistJTI(currentJti, remainingTTL)
  3. revokeRefreshToken(currentRefreshTokenId)

refreshToken():
  1. revokeOldRefreshToken()
  2. blacklistOldAccessJTI()                               ← NEW
  3. issueNewPair()
  4. updateSession(newAccessJti)                            ← NEW
```

## 4. Data Flow Diagrams

### 4.1 Login (Enhanced)

```
Client → LoginHandler
  → FingerprintService.resolveFingerprint(request) → fingerprint hash
  → JwtService.generateAccessToken(..., fingerprint) → JWT with device_fingerprint claim
  → TokenStore.saveRefreshToken(..., fingerprint, accessJti)
  → LoginSessionService.recordLogin(..., accessTokenJti)
    → detectNewDevice()
    → if isNewDevice: publish(NewDeviceLoginEvent)
      → @TransactionalEventListener AFTER_COMMIT
      → MailQueueService.enqueue("NEW_DEVICE_LOGIN", data)
  → return {accessToken, refreshToken}
```

### 4.2 Request Validation (Enhanced JwtAuthFilter)

```
Request → JwtAuthFilter
  → parse JWT → claims
  → check blacklist(claims.jti) → if blacklisted: 401
  → validate claims (issuer, audience, type)
  → validate fingerprint:
    → extract claims["device_fingerprint"]
    → FingerprintService.resolveFingerprint(request)
    → if mismatch AND strictMode: 401 + record event
    → if mismatch AND lenientMode: warn metric
  → set SecurityContext
  → pass to controller
```

## 5. Error Handling

| Scenario | Response | Action |
|----------|----------|--------|
| Fingerprint mismatch (strict) | 401 `FINGERPRINT_MISMATCH` | Record validation event, increment suspicious counter |
| Fingerprint mismatch (lenient) | Pass through | Log warning, Micrometer counter |
| Kick device: session not found | 404 | - |
| Kick device: not owned by user | 403 | - |
| Mail send failure | - | Retry with backoff, max 3 times, then FAILED |
| Mail template not found | - | Log error, mark mail FAILED, skip |

## 6. Database Migrations

```
V20__create_mail_queue.sql
V21__create_mail_templates.sql (with seed data)
V22__alter_login_sessions_add_device_fields.sql (accessTokenJti, deviceName)
V23__alter_refresh_tokens_add_fingerprint.sql (deviceFingerprint, accessTokenJti)
```

## 7. Configuration Additions

```yaml
app:
  security:
    fingerprint:
      enabled: true
      validation-enabled: true
      strict-mode: false
      header-name: X-Device-Fingerprint
      server-compute-fallback: true
    mail:
      queue:
        batch-size: 10
        poll-interval-ms: 5000
        max-retries: 3
        base-retry-delay-seconds: 30
        cleanup-after-days: 30
      templates:
        default-language: vi
```

## 8. Testing Strategy

| Type | Scope | Key Scenarios |
|------|-------|---------------|
| Unit | FingerprintService | Client header priority, server fallback, SHA-256 consistency |
| Unit | MailQueueService | Enqueue within transaction, template rendering |
| Unit | MailJobScheduler | Retry logic, exponential backoff, status transitions |
| Integration | DeviceController | List devices, kick device, kick all, authorization |
| Integration | Login flow | Fingerprint in JWT, new device detection → mail queue |
| Integration | Refresh flow | Old JTI blacklisted, session updated |

## 9. Implementation Phases

| Phase | Files | Dependency |
|-------|-------|------------|
| **P1: Foundation** | FingerprintService, entities, repositories, migrations | None |
| **P2: Mail System** | MailQueueService, MailJobScheduler, NewDeviceMailHandler | P1 |
| **P3: Auth Enhancement** | JwtService, JwtAuthFilter, LoginSessionService, LoginHandler, RefreshTokenHandler | P1 |
| **P4: APIs & Config** | DeviceController, SecurityProperties, SecurityConfig | P1-P3 |
| **P5: Tests** | Unit + Integration tests | P1-P4 |

---

## Design Decisions Summary

| # | Decision | Alternative Considered |
|---|----------|----------------------|
| DD-001 | Hybrid fingerprint (client > server) | Client-only, Server-only |
| DD-002 | Integrate fingerprint into JwtAuthFilter | Separate FingerprintValidationFilter |
| DD-003 | Dedicated mail_queue table | Reuse EventOutboxEntity + Kafka |
| DD-004 | New DeviceController | Extend SessionController |
| DD-005 | Eager session-token sync | Lazy cleanup by scheduler |
| DD-006 | `device_fingerprint` claim name | `dfp` shorthand |
| DD-007 | Templates in DB | Templates in filesystem |
| DD-008 | Approach 2 (evolutionary) over DPoP | DPoP RFC 9449 |
