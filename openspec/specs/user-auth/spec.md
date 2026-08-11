## MODIFIED Requirements

### Requirement: Login với Username thay vì Email
Hệ thống MUST chấp nhận username (thay vì email) cho admin login.

#### Scenario: Login bằng username
- **WHEN** admin gửi `{username, password}` tới `POST /api/auth/login`
- **THEN** hệ thống authenticate bằng username lookup, trả access token + set refresh token cookie

#### Scenario: Email không được chấp nhận
- **WHEN** admin gửi email format trong field username
- **THEN** hệ thống vẫn xử lý bình thường (username field chấp nhận any string, không validate email format)

---

### Requirement: Real API Integration
Frontend MUST gọi real backend API thay vì mock endpoints.

#### Scenario: Login API call
- **WHEN** admin submit login form
- **THEN** frontend gửi `POST /api/auth/login` tới real backend (không phải `mock/auth/sign-in`)

#### Scenario: Refresh API call
- **WHEN** access token sắp hết hạn
- **THEN** frontend gửi `POST /api/auth/refresh` tới real backend (không phải `mock/auth/refresh`)

---

### Requirement: Token Storage Security
Access token MUST được lưu in-memory, refresh token MUST được gửi qua HttpOnly cookie.

#### Scenario: Login response handling
- **WHEN** backend trả login response thành công
- **THEN** access token lưu trong JavaScript variable (không localStorage/sessionStorage)
- **THEN** refresh token set qua `Set-Cookie` header với flags: HttpOnly, Secure, SameSite=Strict, Path=/api/auth

---

### Requirement: Frontend Error Handling
Frontend MUST xử lý tất cả error states từ backend.

#### Scenario: Account locked (HTTP 423)
- **WHEN** backend trả 423 Locked
- **THEN** frontend hiển thị "Tài khoản đã bị khóa" với countdown timer từ `retryAfter`
- **THEN** disable submit button cho đến hết lock

#### Scenario: CAPTCHA required (HTTP 428)
- **WHEN** backend trả 428 Precondition Required
- **THEN** frontend hiển thị ALTCHA widget, tự giải PoW challenge
- **THEN** gửi lại login request kèm CAPTCHA payload

#### Scenario: Rate limited (HTTP 429)
- **WHEN** backend trả 429 Too Many Requests
- **THEN** frontend hiển thị "Quá nhiều yêu cầu" với countdown từ `Retry-After` header
- **THEN** disable submit button cho đến hết timer

#### Scenario: MFA required
- **WHEN** backend trả `{mfaRequired: true}`
- **THEN** frontend redirect tới MFA verification screen với mfaToken

---

### Requirement: CORS và Security Headers
Backend MUST cấu hình CORS cho cookie-based cross-origin auth.

#### Scenario: CORS preflight
- **WHEN** frontend gửi OPTIONS request
- **THEN** backend trả correct CORS headers: `Access-Control-Allow-Origin` = frontend domain, `Access-Control-Allow-Credentials` = true

#### Scenario: Security headers
- **WHEN** bất kỳ response nào từ auth endpoints
- **THEN** backend bao gồm: `Strict-Transport-Security: max-age=31536000; includeSubDomains`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`

---

### Requirement: Sign Out
Hệ thống MUST xóa tất cả tokens khi admin sign out.

#### Scenario: Sign out flow
- **WHEN** admin click Sign Out
- **THEN** frontend gửi POST `/api/auth/logout`
- **THEN** backend xóa refresh token cookie (`Set-Cookie: refresh_token=; Max-Age=0`)
- **THEN** backend revoke refresh token trong database
- **THEN** frontend xóa in-memory access token, redirect tới login page
