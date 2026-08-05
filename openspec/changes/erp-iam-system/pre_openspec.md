# Pre-OpenSpec: erp-iam-system

> **Type**: NEWBUILD
> **Flow**: Non-Financial
> **Source**: URD (File)
> **Classification Evidence**: ERP IAM System -> modules: auth-service, account-service, system-admin-service -> business_analysis.md
> **Archive**: N/A
> **Quality Score**: 95/100

## 📋 Feature Summary

Hệ thống Identity & Access Management cho ERP bao gồm 3 module chính: `auth-service` (quản lý xác thực, MFA, SSO, Token, Password Policy), `account-service` (quản lý thông tin cá nhân, preferences, device, session) và `system-admin-service` (quản lý menu động, cơ cấu tổ chức, workflow phê duyệt, cấu hình hệ thống, API Partner). Đây là nền tảng cốt lõi (Base-Core) để tích hợp các module nghiệp vụ khác.

| Metric | Giá trị |
|--------|---------|
| Số FR | 16 (URD: 16, Enriched: 0) |
| Issues | 0 (🔴: 0, 🟡: 0) |
| Open Questions | 0 |
| **Quality Score** | **95/100** |

---

## 1. Actors

- End User: Người dùng hệ thống (nhân viên, quản lý) sử dụng các chức năng nghiệp vụ, đăng nhập, quản lý profile.
- System Administrator: Quản trị viên hệ thống quản lý phân quyền, cấu hình hệ thống, workflow, API keys.
- API Partner: Hệ thống bên ngoài tích hợp vào ERP thông qua API key.

## 2. Functional Requirements

### FR-001: Quản lý Multi-Factor Authentication [URD]
- **Actor**: End User
- **Action**: Hệ thống phải hỗ trợ xác thực nhiều lớp (OTP SMS, Email, TOTP, CAPTCHA) khi đăng nhập và các thao tác nhạy cảm.
- **Validation**: OTP expires sau 5 phút, tối đa 3 lần sai; TOTP window 30s.

### FR-002: Hỗ trợ SSO và OAuth2 [URD]
- **Actor**: End User
- **Action**: Hệ thống phải hỗ trợ đăng nhập qua external IdP (Google, Microsoft, Keycloak).
- **Validation**: Keycloak là optional downstream, auto-provision user nếu config cho phép.

### FR-003: Quản lý Token Nâng Cao [URD]
- **Actor**: System
- **Action**: Hệ thống phải sử dụng RS256, hỗ trợ token introspection và session binding.
- **Validation**: Đảm bảo an toàn JWT.

### FR-004: Quản lý Password Policy [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cho phép cấu hình policy mật khẩu theo từng domain (độ dài, ký tự, lịch sử).
- **Validation**: Không reuse N passwords gần nhất, force change khi expired.

### FR-005: Quản lý User Profile [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép CRUD thông tin cá nhân (tách biệt khỏi auth data).
- **Validation**: Đổi email/phone cần verify.

### FR-006: Quản lý Preferences & Settings [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép user lưu cài đặt UI, Notification, Privacy.
- **Validation**: Cấu hình lưu trữ linh hoạt theo key-value/category.

### FR-007: Quản lý Device [URD]
- **Actor**: End User
- **Action**: Hệ thống phải track các thiết bị đăng nhập, hỗ trợ trust device và remote logout.
- **Validation**: Trust device skip 2FA.

### FR-008: Quản lý Session [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải track active sessions, cấu hình max concurrent sessions.
- **Validation**: Auto-expire session khi inactive.

### FR-009: Quản lý Account Lifecycle [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép deactivate, request delete (GDPR), export data.
- **Validation**: Đảm bảo compliance GDPR.

### FR-010: Quản lý Phân Quyền Menu Động [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cấu hình cây menu và button permission linh hoạt theo role, department.
- **Validation**: User override ưu tiên cao hơn role permission, dùng Redis cache.

### FR-011: Quản lý Cơ Cấu Tổ Chức [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải quản lý department tree và position, phân bổ user vào các vị trí.
- **Validation**: Department tree tối đa 10 level.

### FR-012: Quản lý API Partner [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cấp phát và quản lý API keys, rate limit, quota cho partner.
- **Validation**: API key chỉ hiển thị 1 lần, rate limit tại Gateway level.

### FR-013: Dynamic Approval Workflow [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cho phép cấu hình quy trình phê duyệt động, multi-step, routing.
- **Validation**: Hỗ trợ timeout escalation và delegation.

### FR-014: Quản lý Cấu Hình Hệ Thống [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải lưu trữ system configs và feature flags.
- **Validation**: Hỗ trợ nhiều config_type (STRING, JSON).

### FR-015: Quản lý Audit Trail [URD]
- **Actor**: System
- **Action**: Hệ thống phải ghi nhận toàn bộ thao tác admin (immutable audit log).
- **Validation**: Không cho UPDATE/DELETE log, ẩn sensitive data.

### FR-016: Quản lý Domain/Tenant Config [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải lưu cấu hình nâng cao per domain (branding, login config).
- **Validation**: Tách biệt dữ liệu theo domain.

## 3. Non-functional Requirements
- Hiệu năng: Đảm bảo độ trễ xác thực token dưới 50ms, cache API rate limiting trên Redis.
- Kiến trúc: 3 modules tách biệt, giao tiếp qua HTTP/Event.

---

## 4. Deduplicated & Consolidated
Không phát hiện trùng lặp.

## 5. Enriched Domain Requirements
Không bổ sung thêm.

### External Integrations
| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Keycloak (Optional)| SSO Downstream | Chạy song song nếu domain config yêu cầu |
| Identity Providers| Google/MS OAuth2 | Social login |

## 6. Assumptions
- Giả định hệ thống API Gateway đã xử lý routing cơ bản, rate limit config từ admin sẽ được Gateway đọc (qua Redis sync).

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 25/25 | |
| Đầy đủ (Completeness) | 20/25 | Thiếu chi tiết schema JSON cho Audit Log |
| Nhất quán (Consistency) | 25/25 | |
| Kiểm thử được (Testability) | 25/25 | |
| **Tổng** | **95/100** | |

### Chi tiết trừ điểm
| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Completeness | -5 | FR-015 | Chưa define rõ old_value_json/new_value_json format | Có thể chuẩn hóa sau tại bước Design |

---

## 8. Issues & Risks
Không phát hiện vấn đề rủi ro cao.

## 9. Open Questions
Không có câu hỏi mở.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
IAM, System Administration, Account Management

### 10.2 Flow Type
Non-Financial, Query, Command

### 10.3 Candidate Services
- auth-service: Chứa các tính năng JWT, SSO, MFA, Password Policy.
- account-service: Profile, Device, Session, Preferences.
- system-admin-service: Menu, Workflow, API Partner, Organization, Audit, Config.

### Detection Evidence
- Keyword: auth, account, system-admin -> Module: 3 services -> File: business_analysis.md

### 10.4 External Integrations
- Keycloak (SSO)
- Redis (Cache, Rate Limiting)
- Kafka (Event Streaming)

### 10.5 Required Modules
- base-security-starter
- base-data-starter
- base-messaging-starter

---
## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | End User | Submit Login credentials | auth-service |
| 2 | System | Validate & Return MFA requirement | auth-service |
| 3 | End User | Submit OTP | auth-service |
| 4 | System | Verify OTP & Return full JWT | auth-service |
| 5 | System | Load and cache menu permissions | system-admin-service |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | AUTH-F01 | TBD | TBD | Pending |
| FR-010 | SYS-F01 | TBD | TBD | Pending |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
Đây là hệ thống có quy mô lớn (3 microservices mới/mở rộng). Kiến trúc đề xuất Base-Core Plugin rất quan trọng để đảm bảo 3 modules này không bị phình to.

### Related Features / Precedents
Đã có `base-core-plugin-architecture` research.

### Integration Notes
Giao tiếp giữa Gateway và system-admin-service cho Rate Limiting rất quan trọng. Cần thiết kế Redis data structure phù hợp để Gateway không bị bottleneck.

### Suggested Approach
Bắt đầu với `system-admin-service` (Menu, API Partner) và `auth-service` (MFA, SSO). `account-service` có thể phát triển song song.
