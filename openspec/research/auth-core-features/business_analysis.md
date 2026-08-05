# Business Analysis: Auth Core Features (FR-001 → FR-004)

## 1. Business Context

Hệ thống ERP IAM cần bảo vệ tài khoản người dùng và dữ liệu nghiệp vụ bằng nhiều lớp xác thực, đồng thời đảm bảo trải nghiệm đăng nhập liền mạch thông qua SSO. Password policy cần linh hoạt theo từng business domain (booking, payment, loyalty) với mức độ nghiêm ngặt khác nhau.

---

## 2. Use Cases

### UC-001: Đăng nhập với MFA

**Actors**: End User
**Precondition**: Tài khoản active, MFA đã được enable
**Mô tả ngữ nghĩa**: Người dùng cần xác minh danh tính qua 2 bước để đảm bảo rằng ngay cả khi mật khẩu bị lộ, kẻ tấn công vẫn không thể truy cập hệ thống.

**Basic Flow**:
1. User gửi username/password tới `/api/auth/login`
2. System validate credentials
3. System kiểm tra `mfaEnabled` trên UserEntity
4. Nếu MFA enabled: trả `MFA_REQUIRED` response kèm `mfaToken` (short-lived, Redis-backed)
5. User nhận OTP qua channel đã cấu hình (SMS/Email/TOTP)
6. User gửi OTP + mfaToken tới `/api/auth/mfa/verify`
7. System validate OTP (max 3 attempts, expires 5 min cho SMS/Email; 30s window cho TOTP)
8. System issue JWT (access + refresh)

**Exception Flows**:
- E1: MFA code sai 3 lần → `mfaToken` bị revoke, buộc login lại từ đầu
- E2: MFA code expired → trả lỗi `MFA_TOKEN_EXPIRED`, yêu cầu login lại
- E3: TOTP secret chưa setup → redirect sang `/api/auth/mfa/totp/setup`
- E4: CAPTCHA required (sau N lần login sai) → gửi captcha token kèm login request

**Business Rules**:
- BR-001: OTP SMS/Email TTL = 5 phút, max 3 attempts
- BR-002: TOTP window = 30 giây, drift tolerance = 1 step (±30s)
- BR-003: CAPTCHA trigger threshold = cấu hình per domain
- BR-004: Trusted device skip MFA (cookie-based, 30 ngày)

---

### UC-002: Đăng nhập qua SSO/OAuth2

**Actors**: End User
**Precondition**: External IdP (Keycloak/Google/Microsoft) đã được cấu hình cho domain
**Mô tả ngữ nghĩa**: Cho phép người dùng sử dụng tài khoản doanh nghiệp hiện có (Google Workspace, Microsoft 365, Keycloak) để truy cập ERP mà không cần nhớ thêm mật khẩu.

**Basic Flow**:
1. User click "Login with [Provider]" → frontend redirect tới IdP authorization endpoint
2. User xác thực trên IdP
3. IdP redirect callback tới auth-service với authorization code
4. auth-service exchange code → ID token/Access token từ IdP
5. auth-service extract claims (`sub`, `email`, `name`)
6. System check user tồn tại (by `sub` / linked identity)
7. Nếu chưa có + auto-provision enabled: tạo UserEntity + publish Kafka event `iam.user.sso_provisioned`
8. System issue internal JWT (access + refresh)

**Exception Flows**:
- E1: Auto-provision disabled + user chưa tồn tại → trả lỗi `SSO_USER_NOT_PROVISIONED`
- E2: IdP token invalid/expired → trả lỗi `SSO_TOKEN_INVALID`
- E3: Email conflict (IdP email đã liên kết user khác) → trả lỗi `SSO_IDENTITY_CONFLICT`

**Business Rules**:
- BR-005: Identity linking dùng `sub` claim (KHÔNG dùng email)
- BR-006: Auto-provision là config per domain (`domains.config.sso.autoProvision: true/false`)
- BR-007: SSO users KHÔNG có password trong hệ thống (passwordHash = null, login only via SSO)

---

### UC-003: Token Introspection & Session Management

**Actors**: System (Resource Server), System Administrator
**Mô tả ngữ nghĩa**: Cho phép resource servers validate token mà không cần parse JWT locally, và cho phép admin revoke session ngay lập tức.

**Basic Flow — Introspection**:
1. Resource server gửi token tới `/api/auth/introspect`
2. auth-service parse token, check jti blacklist, check user status
3. Trả về `IntrospectionResponse` (active, sub, roles, permissions, exp)

**Basic Flow — Force Logout**:
1. Admin gọi `/api/auth/sessions/{userId}/revoke-all`
2. System blacklist all active jti cho user
3. System delete all refresh tokens
4. System publish session revocation event

**Exception Flows**:
- E1: Token malformed → trả `active: false`
- E2: Token expired → trả `active: false`
- E3: User locked/deleted → trả `active: false`

**Business Rules**:
- BR-008: Access token TTL = 5-15 phút (cấu hình per domain)
- BR-009: Refresh token rotation: mỗi lần refresh → revoke old, issue new
- BR-010: JWKS endpoint (`/.well-known/jwks.json`) expose public key cho resource servers

---

### UC-004: Cấu hình Password Policy per Domain

**Actors**: System Administrator
**Mô tả ngữ nghĩa**: Domain "payment" cần password policy nghiêm ngặt hơn domain "booking" vì liên quan đến giao dịch tài chính.

**Basic Flow**:
1. Admin gọi `PUT /api/admin/domains/{domainId}/password-policy` với body config
2. System validate config (min > 0, historyCount ≤ 24, etc.)
3. System lưu policy vào `password_policies` table
4. System invalidate cached PasswordValidator cho domain đó

**Basic Flow — Enforcement on Password Change**:
1. User gọi `POST /api/auth/change-password`
2. System resolve domain từ user membership
3. System load PasswordPolicy cho domain
4. Passay validate: complexity rules + history check
5. Nếu pass: hash password, update user, prune history
6. Nếu fail: trả danh sách violations

**Exception Flows**:
- E1: Password trùng 1 trong N passwords gần nhất → trả lỗi `PASSWORD_RECENTLY_USED`
- E2: Password expired (quá `maxAgeDays`) → force change on next login
- E3: Policy chưa cấu hình cho domain → dùng default global policy

**Business Rules**:
- BR-011: Default policy: minLength=8, requireUppercase=true, requireDigit=true, historyCount=5
- BR-012: Force change: check `password_changed_at` + `maxAgeDays` on login
- BR-013: Policy áp dụng cho cả register và change-password

---

## 3. Traceability Matrix

| FR | Use Case | Business Rules | Priority |
|---|---|---|---|
| FR-001 | UC-001 | BR-001, BR-002, BR-003, BR-004 | HIGH |
| FR-002 | UC-002 | BR-005, BR-006, BR-007 | HIGH |
| FR-003 | UC-003 | BR-008, BR-009, BR-010 | HIGH |
| FR-004 | UC-004 | BR-011, BR-012, BR-013 | MEDIUM |

## 4. Business Rules Summary

| ID | Rule | Domain | Configurable? |
|---|---|---|---|
| BR-001 | OTP TTL = 5 min, max 3 attempts | MFA | Yes (per domain) |
| BR-002 | TOTP window 30s, drift ±1 step | MFA | Yes (global) |
| BR-003 | CAPTCHA trigger after N failed logins | MFA | Yes (per domain) |
| BR-004 | Trusted device = 30 days | MFA | Yes (per domain) |
| BR-005 | Identity linking by `sub` claim | SSO | No (hard rule) |
| BR-006 | Auto-provision per domain config | SSO | Yes (per domain) |
| BR-007 | SSO users have no password | SSO | No (hard rule) |
| BR-008 | Access token TTL 5-15 min | Token | Yes (per domain) |
| BR-009 | Refresh token rotation | Token | No (always on) |
| BR-010 | JWKS endpoint exposure | Token | No (always on) |
| BR-011 | Default password policy | Password | Yes (global default) |
| BR-012 | Force change on expired password | Password | Yes (per domain) |
| BR-013 | Policy applies to register + change | Password | No (hard rule) |
