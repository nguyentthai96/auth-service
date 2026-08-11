## Purpose

Self-hosted Proof-of-Work CAPTCHA sử dụng ALTCHA — bảo vệ login endpoint khỏi automated attacks mà không phụ thuộc external service, zero cost, invisible UX cho legitimate users.

## ADDED Requirements

### Requirement: Challenge Generation
Hệ thống MUST cung cấp ALTCHA challenge qua public endpoint.

#### Scenario: Request challenge
- **WHEN** client gửi GET `/api/captcha/challenge`
- **THEN** hệ thống trả về JSON object `{algorithm, challenge, salt, signature, maxnumber}`
- **THEN** challenge được ký bằng HMAC-SHA256 với server secret key

#### Scenario: Challenge uniqueness
- **WHEN** cùng client gọi endpoint 2 lần liên tiếp
- **THEN** mỗi lần trả về challenge khác nhau (salt unique)

---

### Requirement: Challenge Verification
Hệ thống MUST verify ALTCHA solution từ client.

#### Scenario: Valid solution
- **WHEN** client gửi Base64-encoded ALTCHA payload kèm login request
- **THEN** hệ thống decode, verify HMAC signature, verify PoW solution correct
- **THEN** cho phép login request tiếp tục

#### Scenario: Invalid solution
- **WHEN** ALTCHA payload có HMAC signature không hợp lệ hoặc PoW solution sai
- **THEN** hệ thống trả HTTP 400 Bad Request với error code `AUTH_008`

#### Scenario: Expired or missing payload
- **WHEN** CAPTCHA required nhưng client không gửi payload hoặc gửi payload hết hạn
- **THEN** hệ thống trả HTTP 428 Precondition Required với error code `AUTH_007`

---

### Requirement: Replay Protection
Hệ thống MUST ngăn chặn replay attacks bằng cách tracking used challenges.

#### Scenario: Replay attempt
- **WHEN** client gửi cùng ALTCHA payload đã được verify trước đó
- **THEN** hệ thống reject payload, trả HTTP 400

#### Scenario: Cache expiry
- **WHEN** challenge đã vượt quá TTL (5 phút)
- **THEN** hệ thống tự động xóa khỏi replay cache

---

### Requirement: CAPTCHA Triggering
Hệ thống MUST chỉ yêu cầu CAPTCHA khi phát hiện login suspicious.

#### Scenario: Normal login (no CAPTCHA)
- **WHEN** user login lần đầu hoặc login thành công gần đây
- **THEN** không yêu cầu CAPTCHA

#### Scenario: Multiple failed logins trigger CAPTCHA
- **WHEN** user có >= (maxFailedAttempts - 1) lần login thất bại
- **THEN** hệ thống trả HTTP 428, frontend hiển thị ALTCHA widget
- **THEN** ALTCHA widget tự động giải PoW trong background (invisible UX)

---

### Requirement: Provider Extensibility
Hệ thống MUST cho phép chuyển đổi CAPTCHA provider qua configuration.

#### Scenario: Switch provider
- **WHEN** admin thay đổi config `app.security.captcha.provider` sang `"altcha"`, `"turnstile"`, hoặc `"noop"`
- **THEN** hệ thống tự động sử dụng provider tương ứng mà không cần thay đổi code
