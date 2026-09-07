# Pre-OpenSpec: jwt-security-redesign

> **Type**: EXTEND
> **Flow**: Command
> **Source**: Research Analysis (`openspec/research/jwt-security-redesign/business_analysis.md`)
> **Classification Evidence**: keyword `JwtService` → module `auth.application` → file `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` (354 LOC, EXISTS); keyword `LoginSessionService` → module `auth.application` → file `src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt` (224 LOC, EXISTS); keyword `JwtAuthFilter` → module `shared.security` → file `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` (95 LOC, EXISTS); keyword `TokenBlacklistCacheService` → module `auth.application` → file `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt` (EXISTS); keyword `SessionController` → module `auth.adapter.in.web` → file `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt` (93 LOC, EXISTS); keyword `EventOutboxEntity` → module `auth.adapter.out.persistence.entity` → file `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/EventOutboxEntity.kt` (48 LOC, EXISTS)
> **Archive**: `openspec/changes/archive/2026-08-26-jwt-token-validation/` (previous iteration, scope: MAINTENANCE — JWT validation only. Current scope: EXTEND — full security redesign)
> **Quality Score**: 88/100

## 📋 Feature Summary

Mở rộng hệ thống JWT OAuth2 auth-service hiện tại với 5 components mới: (1) FingerprintService — device fingerprint computation/validation với hybrid client→server priority, (2) MailQueueService + MailJobScheduler — hệ thống email async theo Transactional Outbox pattern, (3) NewDeviceMailHandler — bridge event new device → mail queue, (4) DeviceController — REST API quản lý thiết bị đang hoạt động, (5) Session-Token lifecycle sync — đồng bộ eager giữa session, access token, refresh token khi revoke/kick/logout.

| Metric | Giá trị |
|--------|---------|
| Số FR | 23 (URD: 19, Enriched: 4) |
| Issues | 4 (🔴: 0, 🟡: 3, 🟢: 1) |
| Open Questions | 2 |
| **Quality Score** | **88/100** |

---

## 1. Actors

- **End User (authenticated)**: Đăng nhập, xem danh sách device, kick device, logout
- **System (auth-service)**: Validate JWT + fingerprint, blacklist token, sync lifecycle, gửi email
- **Admin**: Force revoke sessions, kick device user khác
- **Background Job (MailJobScheduler)**: Poll mail queue, render template, gửi SMTP
- **Frontend Client (Browser/Mobile)**: Thu thập device signals → hash → gửi header `X-Device-Fingerprint`

## 2. Functional Requirements

### FR-001: Fingerprint resolution hybrid client→server [URD]
- **Actor**: System
- **Action**: Hệ thống phải resolve device fingerprint theo priority: (1) header `X-Device-Fingerprint` từ client, (2) server-computed fallback SHA-256(UA + Accept-Language + IP-Prefix/24 + SEC-CH-UA) khi client không gửi.
- **Validation**: Client fingerprint phải là hex string 64 ký tự (SHA-256). Nếu format sai → fallback sang server-computed.

### FR-002: Fingerprint embedded as JWT claim [URD]
- **Actor**: System
- **Action**: Hệ thống phải embed claim `device_fingerprint` vào JWT access token khi issue token tại login và refresh.
- **Validation**: Claim `device_fingerprint` là required — không có claim → token invalid.

### FR-003: Per-request fingerprint validation [URD]
- **Actor**: System
- **Action**: JwtAuthFilter phải validate `device_fingerprint` claim trong JWT so với fingerprint resolve từ request hiện tại (mỗi request).
- **Validation**: Strict mode → reject 401 nếu mismatch. Lenient mode → warn metric only. Mode configurable via `app.security.fingerprint.strict-mode`.

### FR-004: Fingerprint validation configurable [URD]
- **Actor**: System
- **Action**: Hệ thống phải cho phép enable/disable fingerprint validation toàn bộ qua config `app.security.fingerprint.enabled` và `validation-enabled`.
- **Validation**: Dev environment có thể disable hoàn toàn; production bắt buộc enable.

### FR-005: Login flow integrate fingerprint [URD]
- **Actor**: System
- **Action**: LoginHandler phải gọi FingerprintService.resolveFingerprint() → truyền vào JwtService khi generate access token → lưu vào LoginSessionEntity.
- **Validation**: LoginSessionEntity.deviceFingerprint KHÔNG được null sau login.

### FR-006: Device list API [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cung cấp API `GET /api/auth/devices` trả danh sách active sessions với device info, isCurrent marker, và device name.
- **Validation**: Response chứa: sessionId, deviceType, browserName, osName, ipAddress, loginAt, lastActivityAt, deviceName, isCurrent.

### FR-007: Kick device cụ thể [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cung cấp API `DELETE /api/auth/devices/{sessionId}` — kick device cụ thể. Kick = revoke session + blacklist access token JTI + revoke refresh token.
- **Validation**: sessionId phải thuộc user hiện tại (403 nếu không). Session không tồn tại → 404.

### FR-008: Kick all other devices [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cung cấp API `DELETE /api/auth/devices` — kick tất cả device khác trừ device hiện tại.
- **Validation**: Current session (matched by JWT JTI) KHÔNG bị kick.

### FR-009: Admin kick device [URD]
- **Actor**: Admin
- **Action**: Admin có thể kick device của bất kỳ user qua API riêng hoặc qua AdminSessionController hiện có.
- **Validation**: Requires ADMIN/SUPER_ADMIN role.

### FR-010: Eager session-token lifecycle sync [URD]
- **Actor**: System
- **Action**: Khi revoke session (logout/kick/policy), hệ thống phải ĐỒNG THỜI: (1) revoke session record, (2) blacklist access token JTI vào 3-tier cache, (3) revoke refresh token — tất cả trong cùng @Transactional.
- **Validation**: Nếu bất kỳ step nào fail → rollback toàn bộ.

### FR-011: Refresh token rotation enhanced [URD]
- **Actor**: System
- **Action**: Khi refresh token, hệ thống phải: (1) revoke old refresh token, (2) blacklist old access token JTI, (3) issue new pair, (4) update session với new access token JTI.
- **Validation**: Old access token JTI phải xuất hiện trong blacklist SAU refresh.

### FR-012: Session entity mở rộng [URD]
- **Actor**: System
- **Action**: LoginSessionEntity phải có thêm fields: `accessTokenJti` (VARCHAR 36, current access token JTI), `deviceName` (VARCHAR 100, display name cho UI).
- **Validation**: accessTokenJti populated khi login/refresh. deviceName computed từ browserName + osName.

### FR-013: New device login email event [URD]
- **Actor**: System
- **Action**: Khi `isNewDevice = true` trong login flow, hệ thống phải publish `NewDeviceLoginEvent`. Event listener (`NewDeviceMailHandler`) nhận event → insert record vào `mail_queue` table.
- **Validation**: Mail queue record tạo AFTER_COMMIT (đảm bảo login transaction thành công).

### FR-014: Mail queue table [URD]
- **Actor**: System
- **Action**: Hệ thống phải có bảng `mail_queue` với fields: id, recipient, subject, template_code, template_data (JSONB), body_rendered, status (PENDING/PROCESSING/SENT/FAILED), retry_count, max_retries, error_message, created_at, sent_at, next_retry_at, created_by.
- **Validation**: Entity extends `SnowflakePersistentAuditableEntity`. Index trên `(status, next_retry_at)`.

### FR-015: Mail template table [URD]
- **Actor**: System
- **Action**: Hệ thống phải có bảng `mail_templates` với fields: id, code (UNIQUE), name, subject_template, body_template, language, active. Seed data qua Flyway cho template `NEW_DEVICE_LOGIN`.
- **Validation**: Template rendering dùng `{{placeholder}}` substitution.

### FR-016: Mail job scheduler [URD]
- **Actor**: System (Background Job)
- **Action**: MailJobScheduler chạy `@Scheduled(fixedDelay)` — poll `mail_queue` với `SELECT ... FOR UPDATE SKIP LOCKED`, render template, gửi SMTP, update status.
- **Validation**: Batch size configurable. Retry với exponential backoff (30s × 2^retryCount). Max retries = 3.

### FR-017: Mail retry mechanism [URD]
- **Actor**: System
- **Action**: Mail gửi thất bại → increment retry_count, tính next_retry_at theo exponential backoff. Khi retry_count >= max_retries → status = FAILED.
- **Validation**: Status transitions: PENDING → PROCESSING → SENT | FAILED.

### FR-018: Device response DTO [URD]
- **Actor**: System
- **Action**: DeviceController trả về `DeviceResponse` DTO chứa: sessionId, deviceType, browserName, osName, ipAddress, loginAt, lastActivityAt, deviceName, isCurrent.
- **Validation**: isCurrent = true khi sessionId match với current user's session (từ JWT JTI).

### FR-019: SecurityConfig update cho device endpoints [URD]
- **Actor**: System
- **Action**: SecurityConfig phải authorize `/api/auth/devices/**` cho authenticated users. Admin endpoints require ADMIN role.
- **Validation**: Unauthenticated request → 401. Non-admin request to admin endpoint → 403.

### FR-020: Fingerprint mismatch suspicious event recording [ENRICHED]
- **Actor**: System
- **Action**: Khi fingerprint mismatch (dù strict hay lenient mode), hệ thống phải record event qua existing `TokenValidationFailedEvent` mechanism.
- **Validation**: Event ghi nhận: userId, JTI, expected fingerprint, actual fingerprint, request IP.

### FR-021: Mail queue cleanup job [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải có scheduled job dọn dẹp mail_queue records có status SENT older than 30 days.
- **Validation**: Cleanup job chạy daily. Delete physical (không soft delete).

### FR-022: Fingerprint properties trong SecurityProperties [ENRICHED]
- **Actor**: System
- **Action**: SecurityProperties phải có nested `FingerprintProperties` data class với fields: enabled, validationEnabled, strictMode, headerName, serverComputeFallback.
- **Validation**: Default: enabled=true, validationEnabled=true, strictMode=false.

### FR-023: Mail properties trong SecurityProperties [ENRICHED]
- **Actor**: System
- **Action**: SecurityProperties phải có nested `MailProperties` data class với queue config (batchSize, pollIntervalMs, maxRetries, baseRetryDelaySeconds, cleanupAfterDays) và template config (defaultLanguage).
- **Validation**: Default: batchSize=10, pollIntervalMs=5000, maxRetries=3.

## 3. Non-functional Requirements

| NFR | Target | Measurement |
|-----|--------|-------------|
| Fingerprint validation latency | < 0.5ms | Hash comparison only |
| Blacklist check latency | < 1ms (L1), < 5ms (L2) | Existing Micrometer timer |
| Device list API latency | < 50ms P95 | New Micrometer timer |
| Mail queue throughput | ≥ 100 emails/min | Job metrics |
| Token revocation propagation | < 30s worst case | Caffeine L1 TTL |

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. Các FR đã được tách riêng từ 6 Use Cases trong business analysis (UC-001 ~ UC-006).

## 5. Enriched Domain Requirements

4 enriched FRs (limit: min(5, ceil(23×0.20)) = min(5, 5) = 5):

### Enriched FRs

- **FR-020** [ENRICHED]: Fingerprint mismatch suspicious event recording — justify: security audit trail, reuse existing TokenValidationFailedEvent
- **FR-021** [ENRICHED]: Mail queue cleanup job — justify: data hygiene, prevent unbounded growth
- **FR-022** [ENRICHED]: Fingerprint properties trong SecurityProperties — justify: consistent config pattern, enable/disable per environment
- **FR-023** [ENRICHED]: Mail properties trong SecurityProperties — justify: consistent config pattern, tunable queue parameters

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| SMTP Server | Gửi email thông báo new device login | JavaMailSender (Spring Boot starter-mail) |
| Redis | L2 blacklist cache, rate limiting | Existing integration |
| Caffeine | L1 in-process cache | Existing integration |
| PostgreSQL | Primary data store | Existing integration |

## 6. Assumptions

- ⚠️ Assumption: SMTP server đã được cấu hình trong environment (`spring.mail.*` properties). Nếu chưa → MailJobScheduler sẽ log error nhưng không block startup.
- ⚠️ Assumption: Client (browser) sẽ gửi `X-Device-Fingerprint` header. Nếu chưa implement → server fallback hoạt động bình thường.
- ⚠️ Assumption: `NewDeviceLoginEvent` đã được emit trong login flow hiện tại (xác nhận qua `LoginSessionService.isNewDevice`).

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 23/25 | FR-003: "strict/lenient mode" cần define rõ behavior khi switch runtime |
| Đầy đủ (Completeness) | 22/25 | FR-013: Chưa specify user email lookup source; FR-009: Admin kick endpoint path chưa finalize |
| Nhất quán (Consistency) | 23/25 | FR-006 & FR-018: DeviceResponse overlap với SessionResponse — cần clarify relationship |
| Kiểm thử được (Testability) | 20/25 | FR-001: Server fingerprint deterministic test khó vì phụ thuộc request headers; FR-016: SMTP mock strategy chưa specify |
| **Tổng** | **88/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -2 | FR-003 | "Strict mode: reject... Lenient mode: warn only" — chưa rõ khi nào admin chuyển mode runtime | Thêm config reload mechanism hoặc ghi rõ restart required |
| 2 | Completeness | -2 | FR-013 | "recipient (user email)" — user email lấy từ đâu? UserEntity hay UserPort? | Specify lookup path: UserPort → UserEntity.email |
| 3 | Completeness | -1 | FR-009 | "Admin có thể kick device" — dùng AdminSessionController hiện có hay tạo endpoint mới? | Decide: reuse AdminSessionController.forceRevokeUserSessions hoặc thêm method |
| 4 | Consistency | -2 | FR-006, FR-018 | DeviceResponse vs SessionResponse — hai DTO tương tự cho cùng data | DeviceResponse là superset, SessionResponse giữ nguyên backward compat |
| 5 | Testability | -3 | FR-001 | "SHA-256(UA + Accept-Language + IP/24 + SEC-CH-UA)" — request header mock phức tạp | Tách computation logic thành pure function testable |
| 6 | Testability | -2 | FR-016 | "gửi SMTP" — mail server integration test strategy? | Dùng GreenMail hoặc mock JavaMailSender |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | Fingerprint thay đổi giữa session (VPN switch, browser update) → user bị kick | FR-003 | Default lenient mode, strict mode opt-in |
| 2 | Missing | 🟡 | SMTP config chưa confirm có trong environment | FR-016 | Conditional: `@ConditionalOnProperty("spring.mail.host")` cho MailJobScheduler |
| 3 | Risk | 🟡 | LoginSessionService có 7 importers — changes phải backward compatible | FR-010, FR-012 | Thêm methods mới, KHÔNG sửa signature methods cũ |
| 4 | Info | 🟢 | mail_queue table sẽ grow — cần cleanup mechanism | FR-021 | Cleanup job daily, archive SENT records > 30 days |

> Không phát hiện issue Critical (🔴).

## 9. Open Questions

- **OQ-1**: User email lookup — từ `UserEntity.email` (direct JPA query) hay qua `UserPort` (hexagonal port)? → Recommend: thêm method `findEmailByUserId` vào existing port.
- **OQ-2**: IP hiển thị trong DeviceResponse — full IP hay masked (192.168.1.xxx)? → Security vs UX trade-off.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain

Auth Security — JWT token lifecycle, device fingerprint, session management, email notification

### 10.2 Flow Type

Command (write operations: login, refresh, kick, logout, enqueue mail)

### 10.3 Candidate Services
- **auth-service**: Primary service — tất cả components nằm trong service này. Evidence: JwtService (auth.application), LoginSessionService (auth.application), JwtAuthFilter (shared.security), SessionController (auth.adapter.in.web), EventOutboxEntity (auth.adapter.out.persistence.entity)

### Detection Evidence
- Keyword: `JwtService` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`
- Keyword: `LoginSessionService` → Module: `auth.application` → File: `src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt`
- Keyword: `JwtAuthFilter` → Module: `shared.security` → File: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt`
- Keyword: `EventOutboxEntity` → Module: `auth.adapter.out.persistence.entity` → File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/EventOutboxEntity.kt`
- Keyword: `OutboxPoller` → Module: `auth.adapter.out.event` → File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/OutboxPoller.kt`
- Keyword: `NotificationGateway` → Module: `auth.application.port.out` → File: `src/main/kotlin/com/ntt/authservice/auth/application/port/out/NotificationGateway.kt`

### 10.4 External Integrations

- SMTP Server (JavaMailSender) — gửi email
- Redis (StringRedisTemplate) — blacklist L2 cache
- Caffeine (programmatic cache) — blacklist L1 cache
- PostgreSQL (JPA) — primary data store

### 10.5 Required Modules

- `auth.application` — FingerprintService (NEW), MailQueueService (NEW), MailJobScheduler (NEW)
- `auth.adapter.in.web` — DeviceController (NEW)
- `auth.adapter.out.persistence.entity` — MailQueueEntity (NEW), MailTemplateEntity (NEW)
- `auth.adapter.out.persistence.repository` — MailQueueRepository (NEW), MailTemplateRepository (NEW)
- `auth.adapter.in.web.dto` — DeviceResponse (NEW)
- `shared.security` — JwtAuthFilter (MODIFY)
- `shared.config` — SecurityProperties (MODIFY)

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Client | Gửi login request + `X-Device-Fingerprint` header | Resolve fingerprint (client > server) |
| 2 | System | Authenticate user (username/password) | Validate credentials |
| 3 | System | Generate JWT with `device_fingerprint` claim | JwtService.generateAccessToken() |
| 4 | System | Record login session (with accessTokenJti, deviceFingerprint) | LoginSessionService.recordLogin() |
| 5 | System | Detect new device | isNewDevice → publish NewDeviceLoginEvent |
| 6 | System | Event listener → enqueue mail | NewDeviceMailHandler → MailQueueService.enqueue() |
| 7 | Job | Poll mail queue → render template → send SMTP | MailJobScheduler.processMailQueue() |
| 8 | Client | Subsequent requests with Bearer token | JwtAuthFilter: parse → blacklist → claims → fingerprint |
| 9 | User | View active devices | GET /api/auth/devices |
| 10 | User | Kick device | DELETE /api/auth/devices/{sessionId} → revoke + blacklist + revoke refresh |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | UC-002: Device Fingerprint Binding | design.md §3.1 | FingerprintService (NEW) | [ADD] |
| FR-002 | UC-002: Basic Flow step 3 | design.md §3.1 | JwtService (MODIFY) | [MODIFY] |
| FR-003 | UC-002: Basic Flow step 4 | design.md §2.1 | JwtAuthFilter (MODIFY) | [MODIFY] |
| FR-004 | UC-002: BR-006 | design.md §7 | SecurityProperties (MODIFY) | [MODIFY] |
| FR-005 | UC-001: Basic Flow step 1 | design.md §4.1 | LoginHandler (MODIFY) | [MODIFY] |
| FR-006 | UC-003: Basic Flow step 1-2 | design.md §3.4 | DeviceController (NEW) | [ADD] |
| FR-007 | UC-003: Basic Flow step 3-4 | design.md §3.4 | DeviceController (NEW) | [ADD] |
| FR-008 | UC-003: Basic Flow step 5 | design.md §3.4 | DeviceController (NEW) | [ADD] |
| FR-009 | UC-003: SF1 | design.md §3.4 | AdminSessionController (MODIFY) | [MODIFY] |
| FR-010 | UC-006: Basic Flow step 4-5 | design.md §3.5 | LoginSessionService (MODIFY) | [MODIFY] |
| FR-011 | UC-006: Basic Flow step 3 | design.md §3.5 | RefreshTokenHandler (MODIFY) | [MODIFY] |
| FR-012 | UC-003: BR-011 | design.md §6 | LoginSessionEntity (MODIFY) | [MODIFY] |
| FR-013 | UC-004: Basic Flow step 2-4 | design.md §3.3 | NewDeviceMailHandler (NEW) | [ADD] |
| FR-014 | UC-005: Database Schema | design.md §3.2 | MailQueueEntity (NEW) | [ADD] |
| FR-015 | UC-005: Basic Flow step 3a | design.md §3.2 | MailTemplateEntity (NEW) | [ADD] |
| FR-016 | UC-005: Basic Flow step 1-4 | design.md §3.2 | MailJobScheduler (NEW) | [ADD] |
| FR-017 | UC-005: BR-017 | design.md §3.2 | MailJobScheduler (NEW) | [ADD] |
| FR-018 | UC-003: Basic Flow step 2 | design.md §3.4 | DeviceResponse (NEW) | [ADD] |
| FR-019 | UC-003 | design.md §7 | SecurityConfig (MODIFY) | [MODIFY] |
| FR-020 | Enriched | design.md §5 | TokenEventRecorder (REUSE) | [REUSE] |
| FR-021 | Enriched | design.md §3.2 | MailJobScheduler (NEW) | [ADD] |
| FR-022 | Enriched | design.md §7 | SecurityProperties (MODIFY) | [MODIFY] |
| FR-023 | Enriched | design.md §7 | SecurityProperties (MODIFY) | [MODIFY] |

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations

Feature này có scope **EXTEND** rộng nhưng rủi ro thấp vì:
- 80% foundation đã có (JWT service, blacklist 3-tier, session management, CQRS handlers)
- Chỉ cần thêm 5 components mới + modify 8 components existing
- Pattern reuse cao (OutboxPoller → MailJobScheduler, ClaimValidator → FingerprintValidator)
- Tất cả changes đều backward compatible

Điểm phức tạp nhất: **Eager session-token sync** (FR-010) — phải đảm bảo atomicity trong @Transactional method khi revoke session + blacklist JTI + revoke refresh token.

### Related Features / Precedents

- `openspec/changes/archive/2026-08-26-jwt-token-validation/`: JWT validation enhancement — đã implement 3-tier blacklist, ClaimValidatorChain, TokenValidationFailedEvent. Reuse patterns từ đây.
- `openspec/changes/archive/2026-08-24-user-login-event/`: User login event — event publishing pattern tương tự NewDeviceLoginEvent.
- `OutboxPoller.kt`: Proven `@Scheduled` + `FOR UPDATE SKIP LOCKED` + retry pattern → mirror cho MailJobScheduler.

### Integration Notes

- **SMTP**: Dependency `spring-boot-starter-mail` cần thêm vào `build.gradle.kts` nếu chưa có. Conditional activation via `@ConditionalOnProperty("spring.mail.host")`.
- **JavaMailSender**: Auto-configured by Spring Boot khi `spring.mail.*` properties present.
- **Kafka**: KHÔNG cần cho mail system. Mail self-contained trong auth-service.

### Suggested Approach

1. **Phase 1 (Foundation)**: FingerprintService + entities + Flyway migrations V20-V23. Pure additions, zero risk.
2. **Phase 2 (Mail System)**: MailQueueEntity + MailTemplateEntity + MailQueueService + MailJobScheduler + NewDeviceMailHandler. Self-contained module.
3. **Phase 3 (Auth Enhancement)**: Modify JwtService, JwtAuthFilter, LoginSessionService, LoginHandler, RefreshTokenHandler. Highest risk phase — cần test kỹ.
4. **Phase 4 (APIs)**: DeviceController + SecurityProperties additions + SecurityConfig. API layer, low risk.
5. **Phase 5 (Tests)**: Unit + Integration tests.

### Context from Confluence Images

N/A — source is research analysis, không phải Confluence.
