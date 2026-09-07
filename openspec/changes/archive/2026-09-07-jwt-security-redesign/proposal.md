## Why

Hệ thống JWT OAuth2 hiện tại thiếu các lớp bảo mật quan trọng: (1) không có cơ chế binding token với device cụ thể — token bị đánh cắp có thể dùng trên bất kỳ device nào, (2) không có quản lý device đang hoạt động — user không thể xem/kick device lạ, (3) không có thông báo email khi login từ device mới, (4) session-token lifecycle không đồng bộ — revoke session nhưng access token vẫn valid đến hết TTL.

## What Changes

- **[NEW]** Device fingerprint binding: embed fingerprint vào JWT claim, validate per-request (hybrid client→server)
- **[NEW]** Device management API: list active devices, kick specific/all devices
- **[NEW]** Email notification system: Transactional Outbox pattern với mail_queue + MailJobScheduler
- **[NEW]** New device login email alert via `@TransactionalEventListener(AFTER_COMMIT)`
- **[MODIFY]** JwtAuthFilter: thêm fingerprint validation step (step 4)
- **[MODIFY]** JwtService: embed `device_fingerprint` claim khi generate token
- **[MODIFY]** LoginSessionService: thêm kickDevice(), listDevicesForUser(), eager sync revocation
- **[MODIFY]** LoginSessionEntity: thêm `accessTokenJti`, `deviceName` columns
- **[MODIFY]** SecurityProperties: thêm FingerprintProperties, MailProperties
- **[MODIFY]** RefreshTokenHandler: blacklist old access JTI khi refresh

## Capabilities

### New Capabilities
- `device-fingerprint`: Device fingerprint computation (hybrid client/server), JWT claim embedding, per-request validation (strict/lenient mode)
- `device-management`: REST API quản lý active devices — list, kick specific, kick all other devices
- `mail-notification`: Hệ thống email async (Transactional Outbox): mail_queue table, template rendering, MailJobScheduler, retry with exponential backoff
- `session-token-sync`: Đồng bộ eager giữa session, access token (JTI blacklist), refresh token khi revoke/kick/logout

### Modified Capabilities
<!-- No existing specs to modify — all are new capabilities -->

## Impact

- **Code**: 10 new files + 9 modified files trong `auth-service`
- **Database**: 4 Flyway migrations (V20-V23): mail_queue, mail_templates, alter login_sessions, alter refresh_tokens
- **APIs**: 3 new endpoints (GET/DELETE `/api/auth/devices`, DELETE `/api/auth/devices/{sessionId}`)
- **Dependencies**: `spring-boot-starter-mail` (new)
- **Config**: `app.security.fingerprint.*` + `app.security.mail.*` (additive, backward compatible)
- **Risk**: 🟡 MEDIUM — JwtService has 8 callers, LoginSessionService has 5 callers. Mitigated by Kotlin default params + additive methods only.
