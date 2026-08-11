# Business Analysis — Auth Login Admin Dashboard

## 1. Business Context

Admin Dashboard là giao diện quản trị cho hệ thống backend, cần xác thực người dùng admin trước khi cho phép truy cập các chức năng quản lý. Yêu cầu bảo mật cao vì admin có quyền truy cập dữ liệu nhạy cảm và thao tác hệ thống.

### Business Rules

| # | Rule | Description |
|---|------|-------------|
| BR-01 | Admin phải đăng nhập bằng **username + password** | Không dùng email vì admin account được tạo bởi system admin |
| BR-02 | Session timeout sau 30 phút không hoạt động | Access token TTL = 15 min, silent refresh tự động |
| BR-03 | Lock account sau 3 lần nhập sai password | Lock duration = 15 phút |
| BR-04 | CAPTCHA bắt buộc khi đạt threshold failed login | Hiển thị CAPTCHA widget khi backend trả về CaptchaRequired |
| BR-05 | Mỗi admin chỉ có 1 active session tại 1 thời điểm | Revoke sessions cũ khi login mới (optional, P2) |
| BR-06 | Credentials phải được bảo mật trong quá trình truyền tải | Enforce HTTPS + HSTS |
| BR-07 | Hệ thống phải chống brute force / password guessing | Rate limiting 5 req/min per IP cho login endpoint |
| BR-08 | Hệ thống phải chống DDoS | Application-level rate limiting + recommend edge WAF |

---

## 2. Use Cases

### UC-01: Admin Login (Happy Path)

**Actors:** Admin User, Admin Dashboard (Frontend), Auth Service (Backend)

**Preconditions:**
- Admin account đã được tạo và ACTIVE
- Network connection available
- Backend auth-service running

**Semantic Description:**
Admin cần đăng nhập vào Dashboard để thực hiện các tác vụ quản trị. Hệ thống xác thực danh tính admin thông qua username/password, sau đó cấp JWT access token + refresh token. Frontend lưu tokens an toàn và sử dụng access token cho mọi API call tiếp theo.

**Basic Flow:**
1. Admin mở Admin Dashboard URL
2. System hiển thị trang Sign-In với form username/password
3. Admin nhập username và password
4. Admin click "Sign In"
5. Frontend validate input (username required, password min 8 chars)
6. Frontend gửi POST `/api/auth/login` với `{ username, password }` qua HTTPS
7. Backend validate credentials (username lookup → password match)
8. Backend reset failed login count
9. Backend check MFA requirement → skip nếu không bật
10. Backend generate access token (JWT RS256, 15 min TTL) + refresh token (7 days)
11. Backend trả về `AuthResponse` (access_token, refresh_token, user info, roles, permissions)
12. Backend set refresh token vào `HttpOnly` + `Secure` + `SameSite=Strict` cookie
13. Frontend lưu access token trong memory (React state)
14. Frontend redirect admin vào Dashboard home

**Postconditions:**
- Admin đã authenticated
- Access token valid trong memory
- Refresh token stored trong HttpOnly cookie
- SecurityContext set trên backend

---

### UC-02: Login Failed — Invalid Credentials

**Exception Flow (từ bước 7 UC-01):**
1. Backend không tìm thấy username hoặc password không match
2. Backend increment failed login count cho user
3. Backend trả về `401 Unauthorized` với error code `INVALID_CREDENTIALS`
4. Frontend hiển thị error message: "Tên đăng nhập hoặc mật khẩu không chính xác"
5. Frontend clear password field, giữ username
6. Admin có thể thử lại

---

### UC-03: Account Locked (Brute Force Detected)

**Exception Flow (từ bước 7 UC-01):**
1. Backend detect failed login count ≥ 3
2. Backend lock account với `lockedUntilAt = now + 15 min`
3. Backend trả về `423 Locked` với error code `ACCOUNT_LOCKED` + `retryAfter` timestamp
4. Frontend hiển thị: "Tài khoản đã bị khóa. Vui lòng thử lại sau [countdown timer]"
5. Frontend disable submit button + hiển thị countdown timer
6. Sau khi countdown hết → enable submit button

---

### UC-04: CAPTCHA Required

**Exception Flow (từ bước 7 UC-01):**
1. Backend detect failed login count ≥ 2 (threshold - 1)
2. Backend trả về `428 Precondition Required` với error code `CAPTCHA_REQUIRED`
3. Frontend hiển thị CAPTCHA widget (reCAPTCHA / hCaptcha)
4. Admin hoàn thành CAPTCHA
5. Frontend re-send login request với `captchaToken` included
6. Quay lại bước 7 UC-01

---

### UC-05: Silent Token Refresh

**Actors:** Frontend (tự động), Auth Service

**Trigger:** Access token sắp hết hạn (< 2 min remaining)

**Basic Flow:**
1. Frontend detect access token sắp expire (decode JWT, check `exp`)
2. Frontend gửi POST `/api/auth/refresh` (refresh token tự động gửi qua HttpOnly cookie)
3. Backend validate refresh token hash
4. Backend revoke old refresh token (rotation)
5. Backend generate new access token + new refresh token
6. Backend set new refresh token cookie
7. Frontend update in-memory access token
8. API calls tiếp tục bình thường

**Exception Flow:**
- Refresh token expired/invalid → Backend trả 401 → Frontend redirect đến login page

---

### UC-06: Rate Limit Exceeded (DDoS / Brute Force)

**Exception Flow:**
1. IP/Client gửi > 5 login requests trong 1 phút
2. Bucket4j reject request trước khi vào login handler
3. Backend trả về `429 Too Many Requests` với `Retry-After` header
4. Frontend hiển thị: "Quá nhiều yêu cầu. Vui lòng thử lại sau [X giây]"
5. Frontend disable form + hiển thị countdown

---

### UC-07: MFA Checkpoint (Nếu MFA enabled)

**Alternative Flow (từ bước 9 UC-01):**
1. Backend detect user has MFA enabled + device not trusted
2. Backend generate MFA token (5 min TTL)
3. Backend trả về `LoginResult.MfaRequired` với mfa_token + method
4. Frontend redirect đến MFA verification screen
5. Admin nhập OTP code
6. Frontend gửi POST `/api/auth/mfa/verify` với mfa_token + otp_code
7. Backend verify OTP → generate full auth tokens
8. Quay lại bước 11 UC-01

---

## 3. Traceability Matrix

| Use Case | Business Rule | Gap Addressed |
|----------|--------------|---------------|
| UC-01 | BR-01, BR-02, BR-06 | G-01, G-02, G-03 |
| UC-02 | BR-01 | G-07 |
| UC-03 | BR-03 | G-07 |
| UC-04 | BR-04 | G-07 |
| UC-05 | BR-02 | G-03 |
| UC-06 | BR-07, BR-08 | G-04, G-08 |
| UC-07 | (MFA - existing) | — |

---

## 4. Non-Functional Requirements

| # | Requirement | Target |
|---|-------------|--------|
| NFR-01 | Login response time | < 500ms (P95) |
| NFR-02 | Token refresh time | < 200ms (P95) |
| NFR-03 | Rate limit throughput | Handle 1000+ concurrent login attempts without degradation |
| NFR-04 | Uptime | 99.9% availability |
| NFR-05 | Password hashing time | 400-800ms (BCrypt strength 12) |
| NFR-06 | HTTPS enforcement | 100% traffic over TLS 1.3 |
