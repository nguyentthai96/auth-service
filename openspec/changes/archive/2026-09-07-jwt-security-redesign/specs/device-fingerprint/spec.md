## Purpose

Device fingerprint binding cho JWT tokens — tính toán fingerprint từ device signals, embed vào JWT claim, và validate per-request để ngăn token replay từ device khác.

## ADDED Requirements

### Requirement: Fingerprint resolution
Hệ thống SHALL resolve device fingerprint theo hybrid priority: client-provided header `X-Device-Fingerprint` (hex string 64 chars) ưu tiên hơn server-computed fallback SHA-256(User-Agent + Accept-Language + IP-Prefix/24 + SEC-CH-UA).

#### Scenario: Client provides valid fingerprint header
- **WHEN** request chứa header `X-Device-Fingerprint` với giá trị hex 64 ký tự
- **THEN** hệ thống MUST sử dụng giá trị từ header làm device fingerprint

#### Scenario: Client provides invalid fingerprint format
- **WHEN** request chứa header `X-Device-Fingerprint` với format không phải hex 64 chars
- **THEN** hệ thống MUST fallback sang server-computed fingerprint

#### Scenario: No fingerprint header provided
- **WHEN** request KHÔNG chứa header `X-Device-Fingerprint`
- **THEN** hệ thống MUST tính server fingerprint từ SHA-256(UA + Accept-Language + IP/24 + SEC-CH-UA)

### Requirement: Fingerprint embedded in JWT
Hệ thống SHALL embed claim `device_fingerprint` vào JWT access token tại thời điểm token generation (login, refresh).

#### Scenario: Login generates token with fingerprint claim
- **WHEN** user đăng nhập thành công
- **THEN** access token MUST chứa claim `device_fingerprint` với giá trị resolved fingerprint

#### Scenario: Refresh generates new token with fingerprint claim
- **WHEN** user refresh token thành công
- **THEN** new access token MUST chứa claim `device_fingerprint` từ current request

### Requirement: Per-request fingerprint validation
JwtAuthFilter SHALL validate `device_fingerprint` claim trong JWT so với fingerprint resolved từ current request.

#### Scenario: Fingerprint matches (any mode)
- **WHEN** claim `device_fingerprint` == resolved fingerprint từ request
- **THEN** request MUST được pass through bình thường

#### Scenario: Fingerprint mismatch in strict mode
- **WHEN** claim `device_fingerprint` != resolved fingerprint VÀ `strict-mode = true`
- **THEN** hệ thống MUST reject request với 401 Unauthorized VÀ record security event

#### Scenario: Fingerprint mismatch in lenient mode
- **WHEN** claim `device_fingerprint` != resolved fingerprint VÀ `strict-mode = false`
- **THEN** hệ thống MUST pass request through VÀ log warning metric

### Requirement: Fingerprint feature toggle
Hệ thống SHALL cho phép enable/disable fingerprint validation qua configuration.

#### Scenario: Fingerprint disabled
- **WHEN** `app.security.fingerprint.enabled = false`
- **THEN** hệ thống MUST skip fingerprint resolution và validation hoàn toàn

#### Scenario: Validation disabled but fingerprint enabled
- **WHEN** `app.security.fingerprint.enabled = true` VÀ `validation-enabled = false`
- **THEN** hệ thống MUST resolve fingerprint và embed vào JWT NHƯNG skip per-request validation
