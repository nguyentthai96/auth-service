## Purpose

Multi-dimensional rate limiting cho login endpoint — giới hạn login request theo IP, username, và device fingerprint để chống brute force, credential stuffing, và distributed attacks.

## ADDED Requirements

### Requirement: Per-IP Rate Limiting
Hệ thống MUST giới hạn login request từ cùng IP address không quá 5 request/phút.

#### Scenario: IP rate limit exceeded
- **WHEN** cùng IP gửi request thứ 6 trong vòng 1 phút
- **THEN** hệ thống trả HTTP 429 Too Many Requests với header `Retry-After` chứa số giây còn lại

#### Scenario: IP rate limit window reset
- **WHEN** 1 phút trôi qua kể từ request đầu tiên
- **THEN** counter reset về 0, IP có thể login bình thường

---

### Requirement: Per-Username Rate Limiting
Hệ thống MUST giới hạn login failures từ cùng username không quá 3 lần thất bại trong 15 phút.

#### Scenario: Username rate limit exceeded
- **WHEN** username nhận được lần login thất bại thứ 4 trong 15 phút
- **THEN** hệ thống trả HTTP 429 với body chứa error code `AUTH_020` và `Retry-After` header

#### Scenario: Successful login resets username counter
- **WHEN** user login thành công
- **THEN** counter failed attempts cho username đó reset về 0

---

### Requirement: Per-Device Rate Limiting
Hệ thống MUST giới hạn login request từ cùng device fingerprint không quá 10 request/giờ.

#### Scenario: Device rate limit exceeded
- **WHEN** cùng device fingerprint gửi request thứ 11 trong 1 giờ
- **THEN** hệ thống trả HTTP 429 với `Retry-After` header

#### Scenario: Missing device fingerprint
- **WHEN** request không có header `X-Device-Fingerprint`
- **THEN** hệ thống skip device-level rate limiting (chỉ check IP + username)

---

### Requirement: Fail-Open Strategy
Hệ thống MUST cho phép request đi qua khi Redis không available (fail-open).

#### Scenario: Redis unavailable
- **WHEN** Redis connection timeout hoặc error xảy ra
- **THEN** hệ thống log error, cho phép request đi qua, không block user

---

### Requirement: Rate Limit Audit
Hệ thống MUST ghi audit log khi rate limit bị exceeded.

#### Scenario: Rate limit triggered
- **WHEN** bất kỳ dimension nào bị exceeded
- **THEN** hệ thống ghi audit log với userId (nếu biết), IP, dimension, attempt count, lock duration
- **THEN** publish `RateLimitExceededEvent` cho monitoring
