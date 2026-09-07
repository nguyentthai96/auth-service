## Purpose

REST API quản lý các thiết bị đang hoạt động — cho phép user xem danh sách device, kick device cụ thể, kick tất cả device khác.

## ADDED Requirements

### Requirement: List active devices
Hệ thống SHALL cung cấp API liệt kê tất cả active sessions của user hiện tại với device info.

#### Scenario: User views active devices
- **WHEN** authenticated user gọi `GET /api/auth/devices`
- **THEN** hệ thống MUST trả danh sách active sessions chứa: sessionId, deviceType, browserName, osName, ipAddress, loginAt, lastActivityAt, deviceName, isCurrent

#### Scenario: Current device marker
- **WHEN** response chứa session matching với JWT JTI của request hiện tại
- **THEN** session đó MUST có `isCurrent = true`, tất cả session khác MUST có `isCurrent = false`

#### Scenario: Unauthenticated access
- **WHEN** request KHÔNG có valid Bearer token
- **THEN** hệ thống MUST respond 401 Unauthorized

### Requirement: Kick specific device
Hệ thống SHALL cho phép user kick (revoke) một device cụ thể bằng sessionId.

#### Scenario: Successful device kick
- **WHEN** authenticated user gọi `DELETE /api/auth/devices/{sessionId}` VÀ session thuộc user đó
- **THEN** hệ thống MUST revoke session VÀ blacklist access token JTI VÀ revoke refresh token VÀ return 200 OK

#### Scenario: Kick device owned by another user
- **WHEN** authenticated user gọi `DELETE /api/auth/devices/{sessionId}` VÀ session KHÔNG thuộc user đó
- **THEN** hệ thống MUST respond 403 Forbidden

#### Scenario: Kick non-existent session
- **WHEN** authenticated user gọi `DELETE /api/auth/devices/{sessionId}` VÀ session KHÔNG tồn tại
- **THEN** hệ thống MUST respond 404 Not Found

### Requirement: Kick all other devices
Hệ thống SHALL cho phép user kick tất cả device khác ngoại trừ device hiện tại.

#### Scenario: Successful kick all
- **WHEN** authenticated user gọi `DELETE /api/auth/devices`
- **THEN** hệ thống MUST revoke tất cả active sessions NGOẠI TRỪ session hiện tại VÀ return số sessions bị kick

#### Scenario: No other devices to kick
- **WHEN** authenticated user chỉ có 1 active session (current)
- **THEN** hệ thống MUST return 200 OK với revokedCount = 0

### Requirement: Admin force kick
Admin SHALL có thể kick device của bất kỳ user nào.

#### Scenario: Admin kicks user device
- **WHEN** user có role ADMIN/SUPER_ADMIN gọi admin kick endpoint
- **THEN** hệ thống MUST revoke target session bất kể ownership

#### Scenario: Non-admin tries admin kick
- **WHEN** user KHÔNG có role ADMIN gọi admin kick endpoint
- **THEN** hệ thống MUST respond 403 Forbidden
