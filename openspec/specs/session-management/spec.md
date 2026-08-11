## Purpose

Quản lý session login — configurable per role (single/multi session, max devices), login history tracking với device fingerprint, và API cho admin quản lý active sessions.

## ADDED Requirements

### Requirement: Configurable Session Policy
Hệ thống MUST cho phép cấu hình session policy tùy theo role của user.

#### Scenario: Default policy
- **WHEN** user login và role không có override config
- **THEN** áp dụng default policy: maxSessions=3, maxDevices=3, onExceed=REVOKE_OLDEST

#### Scenario: Role-specific policy
- **WHEN** user có role `SUPER_ADMIN` với config maxSessions=1, onExceed=REVOKE_ALL
- **THEN** hệ thống áp dụng policy tương ứng thay vì default

#### Scenario: Session limit exceeded — REVOKE_OLDEST
- **WHEN** user đạt maxSessions và onExceed=REVOKE_OLDEST
- **THEN** hệ thống revoke session cũ nhất (earliest login_at), tạo session mới

#### Scenario: Session limit exceeded — REJECT_NEW
- **WHEN** user đạt maxSessions và onExceed=REJECT_NEW
- **THEN** hệ thống trả HTTP 409 Conflict, yêu cầu user revoke session cũ trước

#### Scenario: Session limit exceeded — REVOKE_ALL
- **WHEN** user đạt maxSessions và onExceed=REVOKE_ALL
- **THEN** hệ thống revoke tất cả sessions cũ, tạo session mới

---

### Requirement: Login History Tracking
Hệ thống MUST ghi lại mọi lần login thành công.

#### Scenario: Successful login recorded
- **WHEN** user login thành công
- **THEN** hệ thống tạo record trong `login_sessions` với: userId, IP, userAgent, deviceFingerprint, deviceType, browserName, osName, loginAt

#### Scenario: New device detected
- **WHEN** device fingerprint chưa xuất hiện trong login history của user
- **THEN** hệ thống đánh dấu `is_new_device=true`, emit `NewDeviceLoginEvent`

#### Scenario: Known device login
- **WHEN** device fingerprint đã tồn tại trong history
- **THEN** hệ thống đánh dấu `is_new_device=false`, không emit event

---

### Requirement: Active Session Listing
User MUST xem được danh sách active sessions của mình.

#### Scenario: List active sessions
- **WHEN** authenticated user gửi GET `/api/auth/sessions/active`
- **THEN** hệ thống trả danh sách sessions (id, deviceType, browserName, osName, ipAddress, loginAt, lastActivityAt, isCurrentSession)

---

### Requirement: Session Revocation
User MUST có thể revoke session cụ thể.

#### Scenario: Revoke specific session
- **WHEN** user gửi DELETE `/api/auth/sessions/{sessionId}`
- **THEN** hệ thống set `is_active=false`, `revoked_at=now()`, `revoke_reason=MANUAL`
- **THEN** revoke refresh token liên kết, user trên device đó bị logout

#### Scenario: Revoke someone else's session
- **WHEN** user cố revoke session của user khác (sessionId thuộc userId khác)
- **THEN** hệ thống trả HTTP 403 Forbidden

---

### Requirement: Session Activity Update
Hệ thống MUST cập nhật last activity khi user active.

#### Scenario: Token refresh updates activity
- **WHEN** refresh token được sử dụng thành công
- **THEN** hệ thống cập nhật `last_activity_at` của session tương ứng
