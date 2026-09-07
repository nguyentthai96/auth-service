---
type: brainstorm_notes
change: jwt-security-redesign
date: 2026-09-07
selected_direction: "Approach 2 — Evolutionary Enhancement (Fingerprint-in-JWT + Unified Session-Token Lifecycle + Mail Outbox)"
pre_flow: "Command (multiple write operations)"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: JWT OAuth2 Security Redesign

## Date
2026-09-07

## Context

Research phase đã hoàn thành (`openspec/research/jwt-security-redesign/`). Recommendation: **BUILD** — enhance hệ thống hiện tại thay vì adopt giải pháp ngoài. Codebase đã có 80% foundation. Cần bổ sung 5 core components.

Brainstorm session này tập trung vào:
1. Phân tích design decisions chưa được resolve trong research
2. Đánh giá 3 approaches cho fingerprint binding
3. Quyết định mail system architecture (dedicated vs reuse Outbox + Kafka)
4. Session-Token sync strategy

---

## Questions Asked & Answers

### Q1: Fingerprint nên compute ở đâu — Client, Server, hay cả hai?

**Answer: Cả hai (Hybrid Priority).**

```
Priority: Client-provided X-Device-Fingerprint → Server-computed fallback
```

**Lý do:**
- Client fingerprint (browser-side) dùng nhiều signals hơn (Canvas hash, WebGL, AudioContext, fonts) → accuracy 99%+
- Server fingerprint chỉ dựa vào request headers (UA, Accept-Language, IP prefix) → accuracy ~85%
- **Hybrid**: Nếu client gửi header → dùng. Nếu không → server tự tính (graceful degradation)
- Mobile app: fingerprint stable hơn browser vì không có plugin/extension interference

```
┌─────────────────────────────────────────────────────┐
│                  FingerprintService                  │
├─────────────────────────────────────────────────────┤
│ resolveFingerprint(request):                         │
│   IF header "X-Device-Fingerprint" present:          │
│     → validate format (hex, 64 chars)                │
│     → return client fingerprint ✓                    │
│   ELSE:                                              │
│     → compute server fingerprint:                    │
│       SHA-256(UA + Accept-Language + IP/24 + SEC-CH)  │
│     → return server fingerprint ✓                    │
└─────────────────────────────────────────────────────┘
```

### Q2: FingerprintValidationFilter riêng hay integrate vào JwtAuthFilter?

**Answer: Integrate vào JwtAuthFilter (không tạo filter riêng).**

**Lý do:**
- Filter riêng = thêm HTTP filter overhead cho mỗi request
- Fingerprint chỉ có ý nghĩa SAU KHI JWT đã parse thành công (cần claim `device_fingerprint`)
- JwtAuthFilter đã có pattern: parse → blacklist check → claim validation → **fingerprint validation** (new step)
- Consistent với Chain of Responsibility hiện tại (ClaimValidatorChain)

```
JwtAuthFilter.doFilterInternal():
  1. Extract Bearer token
  2. Parse JWT (RS256 → prev → HMAC)
  3. Check blacklist by JTI
  4. Validate claims (issuer, audience, token type)
  5. [NEW] Validate device fingerprint            ← ADD HERE
  6. Set SecurityContext

KHÔNG cần FingerprintValidationFilter riêng biệt.
```

**Cập nhật so với Technical Spec:** Tech spec ban đầu đề xuất filter riêng — quyết định integrate vào JwtAuthFilter thay vì tạo filter mới.

### Q3: Mail system — Dùng riêng mail_queue table hay reuse EventOutboxEntity + Kafka?

**Answer: Dedicated mail_queue table + dedicated MailJobScheduler.**

**Phân tích 3 options:**

```
┌───────────────────────────────────────────────────────────────┐
│ Option A: Reuse EventOutboxEntity + Kafka → mail worker       │
│                                                               │
│ Producer → EventOutboxEntity → OutboxPoller → Kafka           │
│         → notification-service → SMTP                         │
│                                                               │
│ ✅ Reuse existing outbox pattern                              │
│ ✅ Decoupled (notification-service handles mail)              │
│ ❌ Requires notification-service (chưa có)                    │
│ ❌ Over-engineering cho single use case (new device email)     │
│ ❌ Kafka dependency cho email sending                          │
└───────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────┐
│ Option B: Reuse NotificationGateway port                      │
│                                                               │
│ Event → NotificationGateway.sendNotification()                │
│       → KafkaNotificationGateway → Kafka topic                │
│       → (external worker sends email)                         │
│                                                               │
│ ✅ Port already exists with sendNotification()                │
│ ❌ Still needs external email worker                           │
│ ❌ Fire-and-forget: no retry, no tracking, no template         │
└───────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────┐
│ Option C: Dedicated mail_queue + MailJobScheduler (SELECTED) ✓│
│                                                               │
│ Event → @TransactionalEventListener → INSERT mail_queue       │
│       → MailJobScheduler (@Scheduled) → SMTP                  │
│                                                               │
│ ✅ Self-contained (no external dependency)                    │
│ ✅ Template rendering built-in                                │
│ ✅ Retry + status tracking + audit trail                      │
│ ✅ Follows same pattern as OutboxPoller (FOR UPDATE SKIP LOCKED)│
│ ✅ User requirement: "insert vào một bảng table mail"         │
│ ❌ New table + new entity + new scheduler                     │
│ ❌ Not reusable for non-email notifications (SMS, push)       │
└───────────────────────────────────────────────────────────────┘
```

**Quyết định Option C vì:**
1. User explicitly yêu cầu "insert vào một bảng table mail"
2. Self-contained, không cần service/infrastructure mới
3. Reuse pattern OutboxPoller (đã proven) nhưng cho email specifically
4. Template rendering + retry + audit trail built-in

### Q4: DeviceController mới hay extend SessionController?

**Answer: Tạo DeviceController mới, KHÔNG extend SessionController.**

```
SessionController (/api/auth/sessions)
  ├── GET    /           → list active sessions (existing)
  ├── DELETE /{sessionId} → revoke session (existing)
  └── DELETE /           → revoke all sessions (existing)

DeviceController (/api/auth/devices) ← NEW
  ├── GET    /           → list devices (with isCurrent, device name, geo)
  ├── DELETE /{sessionId} → kick device (revoke + blacklist + notification)
  └── DELETE /           → kick all OTHER devices (exclude current)
```

**Lý do tách riêng:**
- **Different concerns**: Session = internal state. Device = user-facing representation.
- **Different response shape**: DeviceResponse có `isCurrent`, `deviceName`, UI-friendly data. SessionResponse minimal.
- **Different behavior**: Kick device = revoke session + blacklist JTI + revoke refresh token + optional notification. Revoke session chỉ update DB.
- **Backward compatible**: SessionController APIs không thay đổi.

**Trade-off accepted:** Có duplication nhỏ (cả hai delegate to LoginSessionService), nhưng concerns khác nhau rõ ràng.

### Q5: Session-Token lifecycle sync — Eager hay Lazy?

**Answer: Eager synchronization (sync ngay tại thời điểm revoke).**

```
EAGER (selected ✓):
  kickDevice() → revokeSession() → blacklistJTI() → revokeRefreshToken()
  ↑ All in same @Transactional method

LAZY (rejected ✗):
  kickDevice() → revokeSession()
  ↑ SessionCleanupScheduler later finds orphaned tokens → blacklist/revoke
```

**Lý do chọn Eager:**
- Security-critical: Một token bị revoke phải bị invalidate NGAY LẬP TỨC (không chờ scheduler)
- Caffeine L1 đã có TTL 30s → worst case 30s window cho propagation. Thêm lazy = worse.
- Transactional consistency: all-or-nothing. Nếu blacklist fail → session cũng rollback → no inconsistency.

### Q6: Fingerprint claim name trong JWT — `device_fingerprint` hay `dfp`?

**Answer: `device_fingerprint`.**

**Lý do:**
- `LoginSessionEntity` đã có column `device_fingerprint` → consistent naming
- `LoginHandler` đã pass `command.deviceFingerprint` → consistent
- `dfp` quá ngắn, dễ confuse với DFP (DoubleClick for Publishers)
- JWT payload size: thêm ~15 bytes cho tên claim → negligible

### Q7: `mail_templates` store ở DB hay file system?

**Answer: Database (mail_templates table).**

**Lý do:**
- Admin có thể update template mà không cần deploy
- i18n: multiple rows per template_code (1 per language)
- Template rendering đơn giản: `{{placeholder}}` substitution, không cần engine phức tạp (Thymeleaf/Freemarker overkill)
- Seed data via Flyway migration (initial templates)

---

## Approaches Considered

### Approach 1: DPoP (RFC 9449) Token Binding
**Full cryptographic proof-of-possession:**
- Client generates keypair → sends DPoP proof header → server binds token to public key
- Spring Security 6.5+ native support
- **Pros**: Strongest security, industry standard, cryptographically verifiable
- **Cons**: Heavy client-side work, requires all clients to implement DPoP, breaks existing clients, requires Spring Boot 3.5+ upgrade
- **Verdict**: Over-engineering cho current phase. Good candidate for Phase 2.

### Approach 2: Evolutionary Enhancement (SELECTED ✓)
**Fingerprint-in-JWT + Unified Session-Token Lifecycle + Mail Outbox:**
- Embed `device_fingerprint` claim in JWT → validate per-request
- Sync session ↔ token ↔ blacklist lifecycle eagerly
- mail_queue table + MailJobScheduler for async email
- **Pros**: Incremental, no breaking changes, reuse existing patterns, matches codebase style
- **Cons**: Fingerprint is softer security than DPoP (can be spoofed)
- **Verdict**: Best fit for current needs. 80% of security benefit with 20% of complexity.

### Approach 3: Full Session Server (Redis-backed)
**Replace JWT validation with server-side session check:**
- Every request → Redis lookup for session validity + device binding
- **Pros**: Instant revocation (no TTL gap), full control
- **Cons**: Defeats purpose of JWT (stateless), Redis becomes SPOF, latency increase, architecture redesign
- **Verdict**: Too disruptive. Contradicts existing stateless JWT architecture.

---

## Selected Direction

**Approach 2: Evolutionary Enhancement** — vì:

1. **Incremental**: Thêm từng layer mà không phá vỡ existing behavior
2. **Pattern-consistent**: Reuse OutboxPoller pattern, CQRS handlers, ClaimValidator chain
3. **Configurable**: strict/lenient mode cho fingerprint, enable/disable qua config
4. **Backward compatible**: Existing APIs không thay đổi, chỉ thêm mới
5. **Pragmatic security**: Fingerprint claim + blacklist + session sync = defense-in-depth đủ mạnh

```
╔══════════════════════════════════════════════════════════╗
║             DEFENSE-IN-DEPTH LAYERS                      ║
╠══════════════════════════════════════════════════════════╣
║ Layer 1: Short-lived access token (15 min)               ║
║ Layer 2: Refresh token rotation (revoke old on refresh)  ║
║ Layer 3: JTI blacklist (Redis L2 + Caffeine L1)          ║
║ Layer 4: Device fingerprint claim (SHA-256 binding)      ║
║ Layer 5: Session-token sync (eager revocation)           ║
║ Layer 6: New device email alert (user awareness)         ║
║ Layer 7: Device management API (user control)            ║
╚══════════════════════════════════════════════════════════╝
```

---

## Pre-classifications (preliminary)

- **Feature type**: EXTEND (enhance existing auth system)
- **Flow type**: Command (login, refresh, kick are all write operations)
- **Affected modules**:
  - `auth.application` — JwtService, LoginSessionService, FingerprintService (NEW), MailQueueService (NEW)
  - `auth.adapter.in.web` — DeviceController (NEW)
  - `auth.adapter.out.persistence.entity` — MailQueueEntity (NEW), MailTemplateEntity (NEW), LoginSessionEntity (MODIFY)
  - `shared.security` — JwtAuthFilter (MODIFY)
  - `shared.config` — SecurityProperties (MODIFY), SecurityConfig (MODIFY)

---

## GitNexus Findings (explored)

### Related processes:
- `proc_152_dofilterinternal` — JwtAuthFilter → ServiceClaims (parse + validate)
- `proc_57_logout` — Logout → FindByUserIdAndSessionActiveTrue (session revocation)
- `proc_70_forcerevokeusersessions` — Admin force revoke (session cleanup)
- `proc_35_processdeletionrequests` — Account deletion → session cleanup
- `proc_104_deactivateaccount` — Account deactivate → session cleanup

### Key symbols:
- `JwtAuthFilter` (Class, L20-95) → imported by `SecurityConfig`
- `LoginSessionService` (Class, L16-224) → imported by LoginHandler, SessionController, AdminSessionController, AuthController, CqrsAuthController
- `EventOutboxEntity` (Class, L18) → `SnowflakePersistentAuditableEntity` base, outbox pattern
- `OutboxPoller` (Class) → `@Scheduled` + `@Transactional` + `FOR UPDATE SKIP LOCKED`
- `NotificationGateway` (Interface) → `sendNotification(userId, type, channel, payload)` — existing port

### Architecture insights:
- **LoginSessionService** is heavily coupled: 7 importers. Changes must be backward compatible.
- **OutboxPoller** pattern is proven: exact same `@Scheduled` + `FOR UPDATE SKIP LOCKED` + retry logic → reuse for MailJobScheduler.
- **NotificationGateway** has `sendNotification()` method → nhưng fire-and-forget, không có retry/tracking → mail_queue approach tốt hơn.
- **SecurityProperties** đã có nested `SessionProperties`, `BlacklistCacheProperties` → thêm `FingerprintProperties`, `MailProperties` consistent.

---

## Design Decisions (Captured)

| # | Decision | Reasoning | Impact |
|---|----------|-----------|--------|
| DD-001 | Fingerprint: Hybrid client→server priority | Client richer signals; server fallback for old clients | FingerprintService design |
| DD-002 | NO separate FingerprintValidationFilter | Fingerprint needs parsed JWT claim → must be AFTER parse | JwtAuthFilter modification |
| DD-003 | Dedicated mail_queue table | User requirement + self-contained + template support | New entity + migration |
| DD-004 | DeviceController separate from SessionController | Different concerns, responses, behaviors | New controller |
| DD-005 | Eager session-token sync | Security-critical, no lazy gap acceptable | LoginSessionService changes |
| DD-006 | `device_fingerprint` claim name | Consistent with existing entity column | JWT generation |
| DD-007 | Templates in DB, not filesystem | Admin-editable, i18n support, Flyway seeds | mail_templates table |
| DD-008 | Approach 2 over DPoP | Pragmatic security, incremental, no breaking changes | Overall architecture |

---

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Fingerprint changes mid-session (VPN, browser update) | Medium | Lenient mode (warn only) as default; strict mode opt-in |
| Mail queue grows unbounded | Low | Cleanup job archives SENT records after 30 days |
| LoginSessionService becomes too large | Medium | Extract device-specific methods to `DeviceSessionService` if > 300 lines |
| JWT payload size increases | Low | +~30 bytes for fingerprint claim. Total still < 1KB |
| Race condition on kick device + refresh token rotation | Medium | Eager blacklist JTI + `@Transactional` ensures atomicity |
| Mail template injection | Low | `{{placeholder}}` replacement only, no eval/expression engine |

---

## Open Questions for Design Phase

- [RESOLVED] Fingerprint source priority (client > server) → DD-001
- [RESOLVED] Filter architecture (integrate vs separate) → DD-002
- [RESOLVED] Mail system (dedicated vs reuse outbox) → DD-003
- [RESOLVED] Controller structure (new vs extend) → DD-004
- [RESOLVED] Sync strategy (eager vs lazy) → DD-005
- [OPEN] Cần confirm: SMTP config đã có trong environment hay cần setup? → Check `spring.mail.*` properties
- [OPEN] IP masking trong DeviceResponse: hiển thị full IP hay mask (192.168.1.xxx)? → Security vs UX trade-off

## Open Questions for URD Analysis

- Template i18n: user preferred language stored ở đâu? `users` table hay separate `user_preferences`?
- Mail queue cleanup: Archive sang bảng `mail_queue_archive` hay soft delete + periodic purge?
- Rate limit mail per user: Bao nhiêu email/giờ là hợp lý? (Prevent spam từ repeated login/kick)

---

## Summary State Machine (Entire System)

```
                    ┌──────────────────────────────────────┐
                    │          CLIENT REQUEST               │
                    └──────────┬───────────────────────────┘
                               │
                    ┌──────────▼───────────────────────────┐
                    │     LoginRateLimitFilter              │
                    │  (IP + Username + Device rate check)   │
                    └──────────┬───────────────────────────┘
                               │
                    ┌──────────▼───────────────────────────┐
                    │        JwtAuthFilter (ENHANCED)       │
                    │  1. Parse JWT                         │
                    │  2. Blacklist check (JTI)             │
                    │  3. Claim validation                  │
                    │  4. Fingerprint validation [NEW]      │
                    │  5. Set SecurityContext                │
                    └──────────┬───────────────────────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
    ┌─────────▼──┐   ┌────────▼─────┐   ┌─────▼──────────┐
    │   LOGIN     │   │   REFRESH    │   │  DEVICE MGMT   │
    │  Handler    │   │   Handler    │   │  Controller     │
    └─────┬───────┘   └──────┬──────┘   └──────┬──────────┘
          │                  │                  │
          │ 1.Auth           │ 1.Validate       │ 1.List devices
          │ 2.Fingerprint    │   old refresh    │ 2.Kick device
          │ 3.Generate JWT   │ 2.Blacklist old  │ 3.Kick all
          │   +fingerprint   │   access JTI     │
          │   claim          │ 3.Issue new      │     ┌──────────────┐
          │ 4.Record session │   pair           │     │ KICK DEVICE: │
          │ 5.Detect new     │ 4.Update session │     │  Revoke sess │
          │   device         │                  │     │  Blacklist   │
          │                  │                  │     │   JTI        │
          │    ┌─────────┐   │                  │     │  Revoke      │
          │    │NEW DEVICE│   │                  │     │   refresh    │
          │    │ EVENT    │   │                  │     │  Notify      │
          │    └────┬────┘   │                  │     └──────────────┘
          │         │        │                  │
          │ ┌───────▼──────────────────────────┐│
          │ │   NewDeviceMailHandler            ││
          │ │  @TransactionalEventListener      ││
          │ │  → INSERT mail_queue              ││
          │ └──────────────────────────────────┘│
          │                                     │
          │ ┌──────────────────────────────────┐│
          │ │   MailJobScheduler (@Scheduled)   ││
          │ │  → Poll mail_queue               ││
          │ │  → Render template               ││
          │ │  → Send SMTP                     ││
          │ │  → Update status                 ││
          │ └──────────────────────────────────┘│
          │                                     │
          └─────────────────────────────────────┘
```

---

> **Status**: BRAINSTORM COMPLETE. Ready for `/wf_openspec jwt-security-redesign`.
