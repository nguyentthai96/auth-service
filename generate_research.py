import os

base_dir = "/home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/core-microservices"
os.makedirs(base_dir, exist_ok=True)

files = {
    "research_brief.md": """# Research Brief: Core Microservices (Auth, Account, System-Admin)

## 1. Feature Description
Phân tích tính năng lõi cần thiết cho 3 microservice cơ bản trong hệ thống:
- **auth-service**: Xác thực, quản lý token, SSO.
- **account-service**: Quản lý thông tin người dùng, KYC, thiết bị.
- **system-admin-service**: Quản trị hệ thống, RBAC, audit log, monitoring.

## 2. Goals & Objectives
- Xác định scope tính năng ranh giới rõ ràng cho từng service.
- Đảm bảo tính độc lập (loose coupling) và giao tiếp hiệu quả (event-driven/REST).
- Đưa ra best practices về Security (Zero Trust, JWT) và Database per service.

## 3. Keywords & Search Queries
- Keywords: Microservices Auth, IAM, Keycloak, OIDC, RBAC, User Profile Management, Audit Logging.
- Queries: "microservices auth architecture", "system admin dashboard open source", "account service domain driven design".

## 4. Current System Analysis
### 4.1 Related Features
| Feature | Module | Relevance | Notes |
|---------|--------|-----------|-------|
| Auth | auth-service | High | Đang khởi tạo boilerplate |
| Account | account-service | High | Đang khởi tạo boilerplate |
| Admin | system-admin-service | High | Đang khởi tạo boilerplate |

### 4.2 Existing Code Patterns
- Clean Architecture (dự kiến áp dụng).
- Java/Kotlin + Spring Boot.

### 4.3 Tech Stack Constraints
- Spring Boot framework.
- Cơ sở dữ liệu quan hệ (PostgreSQL/Oracle) và Redis (cache).

### 4.4 Integration Points
- `auth-service` cấp JWT token (IdP local).
- `account-service` và `system-admin-service` đóng vai trò là Resource Servers, validate token qua JWK/Public Key.
- Liên kết ID người dùng (UUID/ID) qua các bảng của các service.
""",

    "opensource_findings.md": """# Open Source Findings

## 1. Keycloak (Reference for Auth Service)
- URL: https://github.com/keycloak/keycloak
- Focus: Identity and Access Management

### Scoring
- Feature completeness: 10/10
- Applicability: 8/10
- Activity: 9/10
- Documentation: 9/10
- Code quality: 8/10
- Community: 10/10
- Popularity: 10/10

### Gap Analysis
| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| OIDC/SAML | ✅ | | Standard compliant | Heavyweight, overkill cho startup |
| User Federation | ✅ | | Hỗ trợ LDAP/AD tốt | Khó config custom flows phức tạp |

**Verdict**: Có thể dùng như Standalone Server hoặc tham khảo pattern cho Auth Service tự build.
**Recommendation**: Tham khảo luồng OAuth2/OIDC của Keycloak để thiết kế Custom Auth Service.

## 2. ZITADEL (Reference for Multi-tenant IAM)
- URL: https://github.com/zitadel/zitadel
- Focus: Cloud-native IAM for B2B SaaS

### Gap Analysis
| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Multi-tenancy | ✅ | | Native B2B support | Build bằng Go (khác tech stack Java) |
| Audit Trail | ✅ | | Event-sourcing core | Learning curve cao |

**Verdict**: Tham khảo cách thiết kế Audit Trail cho system-admin-service.

## 3. Refine (Reference for Admin Panel)
- URL: https://github.com/refinedev/refine
- Focus: React-based headless admin framework
**Verdict**: Công cụ Frontend hữu ích, giúp định hình các API CRUD mà system-admin-service cần cung cấp.
""",

    "web_research.md": """# Web Research

## Iteration 1: IAM & Authentication Architecture
- Source: [startwithidentity.com Open Source IAM Comparison](https://startwithidentity.com)
- Insight: Có xu hướng tách biệt IdP (như Keycloak, Authentik) khỏi Application code. Với microservices, sử dụng JWT là chuẩn mực. Cần phân tách `credentials` ra khỏi bảng `users` thông thường.
- Relevance: 9/10
- Pros: Bảo mật cao khi auth tách riêng biệt.
- Cons: Overhead quản lý hệ thống phân tán.

## Iteration 2: Account Service & Domain Driven Design
- Source: [microservices.io - Database per service](https://microservices.io/patterns/data/database-per-service.html)
- Insight: Account Service nên là owner của bảng `users` chứa thông tin profile. Auth Service chỉ nên giữ `user_id`, `username`, `password_hash`. Giao tiếp giữa 2 service có thể thông qua gRPC hoặc Message Queue khi có sự kiện (ví dụ: đổi pass).
- Relevance: 10/10

## Iteration 3: System Admin & RBAC
- Source: [casdoor.org (AI-native IAM)](https://casdoor.org/)
- Insight: Việc quản lý phân quyền (RBAC) thường đi liền với Admin Portal. Cần một module riêng (như system-admin-service) để quản lý `roles`, `permissions` và mapping user-role.
- Relevance: 8/10
""",

    "comparison_analysis.md": """# Comparison & Gap Analysis

## 1. Product/Tool Comparison
| Approach | Phân bổ Database | Ưu điểm | Nhược điểm |
|----------|-----------------|---------|------------|
| **Monolithic Auth+Account** | 1 Database chung cho Auth và Account | Dễ join data, transaction ACID dễ dàng | Vi phạm nguyên tắc Microservices, khó scale riêng biệt |
| **Separated Services (Recommended)** | `auth_db`, `account_db`, `admin_db` riêng | Scalability cao, Loose coupling, Security tốt (Auth bị hack không lộ profile, hoặc ngược lại) | Distributed Transaction, Data sync phức tạp (Eventual Consistency) |
| **Outsourced IdP (Keycloak)** | Dùng Keycloak cho Auth, build Account/Admin | Giảm code Auth, chuẩn OIDC | Dữ liệu user phân tán giữa Keycloak DB và Account DB |

## 2. Recommendation
- Dựa trên context 3 module riêng rẽ đã tồn tại (`auth-service`, `account-service`, `system-admin-service`), khuyến nghị tiếp cận **Separated Services**.
- Tự build Auth Service sử dụng Spring Security OAuth2 (tạo Custom Authorization Server).
- Sử dụng Event-driven (Kafka/RabbitMQ) để sync data giữa các service khi có thay đổi quan trọng (vd: UserCreated event từ AccountService -> AuthService).
""",

    "business_analysis.md": """# Business Analysis: Core Microservices Features

## 1. Auth Service (`auth-service`)
### Tính năng cần thiết:
1. **Đăng nhập (Login)**: Hỗ trợ username/password, OTP, hoặc OAuth2 Social Login. Sinh JWT.
2. **Quản lý Token (Token Management)**: Refresh token, Revoke token (Logout).
3. **Quản lý Credentials**: Đổi mật khẩu, Quên mật khẩu, Khóa tài khoản do brute-force.
4. **MFA (Multi-Factor Auth)**: Hỗ trợ TOTP hoặc SMS OTP.

### UC-AUTH-01: Đăng nhập
- **Basic Flow**: User gửi credentials -> Auth kiểm tra hash -> Sinh Access Token & Refresh Token -> Trả về client.
- **Exception Flow**: Sai mật khẩu 5 lần -> Cập nhật trạng thái `locked` -> Thông báo tài khoản bị khóa.

## 2. Account Service (`account-service`)
### Tính năng cần thiết:
1. **Quản lý Hồ Sơ (Profile Management)**: Xem/Sửa thông tin cá nhân (Avatar, tên, ngày sinh).
2. **Định danh (KYC)**: Flow upload giấy tờ tùy thân, duyệt KYC.
3. **Quản lý thiết bị (Device Management)**: Xem lịch sử đăng nhập, các thiết bị đang active, remote logout.
4. **Cài đặt người dùng**: Tùy chọn thông báo, ngôn ngữ, timezone.

### UC-ACC-01: Quản lý thiết bị đăng nhập
- **Basic Flow**: Lấy danh sách device_id đã login -> User chọn "Đăng xuất thiết bị X" -> Call tới auth-service để revoke token của device đó -> Update status device.

## 3. System Admin Service (`system-admin-service`)
### Tính năng cần thiết:
1. **Quản lý phân quyền (RBAC)**: Tạo/Sửa/Xóa Role. Gán Permission cho Role. Gán Role cho User (Admin).
2. **User Support (Quản lý người dùng)**: Xem danh sách user, block/unblock, force password reset.
3. **Audit Log**: Ghi log mọi hành động nhạy cảm của Admin (Ai làm gì, lúc nào).
4. **System Configuration**: Quản lý các tham số hệ thống (Feature flags, maintenance mode).

### UC-ADM-01: Gán quyền cho người dùng
- **Basic Flow**: Admin chọn User -> Chọn Role (vd: SUPPORT_STAFF) -> Lưu vào bảng `user_roles`. Khi user login, auth-service call API hoặc đọc DB chung để lấy Roles đưa vào JWT claims.
""",

    "technical_spec.md": """# Technical Specification

## 1. Architecture Flow
```mermaid
sequenceDiagram
    participant Client
    participant API_Gateway
    participant Auth_Service
    participant Account_Service
    
    Client->>API_Gateway: POST /auth/login
    API_Gateway->>Auth_Service: Forward
    Auth_Service-->>API_Gateway: Return JWT
    API_Gateway-->>Client: JWT Tokens
    
    Client->>API_Gateway: GET /account/profile (Header: Bearer JWT)
    API_Gateway->>Account_Service: Forward (Validate JWT)
    Account_Service-->>API_Gateway: Profile Data
    API_Gateway-->>Client: Return Profile Data
```

## 2. Database Schema Design (Phân tán)

### `auth-service` DB
- `credentials`: id, user_id (UUID), username, password_hash, status (ACTIVE, LOCKED).
- `refresh_tokens`: id, user_id, token_hash, device_id, expires_at.

### `account-service` DB
- `users`: id (UUID), email, phone, full_name, created_at.
- `user_devices`: id, user_id, device_name, ip_address, last_active.

### `system-admin-service` DB
- `roles`: id, name, description.
- `permissions`: id, action, resource.
- `role_permissions`: role_id, permission_id.
- `user_roles`: user_id, role_id.
- `audit_logs`: id, admin_id, action, target_id, details, created_at.

## 3. Core API Endpoints
**Auth Service**
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

**Account Service**
- `GET /api/v1/account/me`
- `PUT /api/v1/account/profile`
- `GET /api/v1/account/devices`

**Admin Service**
- `GET /api/v1/admin/users`
- `POST /api/v1/admin/users/{id}/block`
- `POST /api/v1/admin/roles`

## 4. Agent Implementation Notes
- Cả 3 service cần sử dụng chung một cơ chế xử lý JWT (có thể tạo module `common-security` chứa JwtDecoder filter).
- Các action ảnh hưởng chéo (như Admin block user) có thể dùng Kafka: `AdminService` publish event `UserBlockedEvent`, `AuthService` listen và revoke hết token của user đó.
""",

    "validation_report.md": """# Validation Report

| Category | Status | Notes |
|----------|--------|-------|
| 1. Source Verification | ✅ PASS | Các URLs open source (Keycloak, Zitadel, Refine) đều chuẩn xác và tồn tại. |
| 2. Consistency | ✅ PASS | Business Analysis và Technical Spec đồng nhất (VD: Role/Permission xuất hiện cả ở BA và ERD của Tech Spec). |
| 3. Completeness | ✅ PASS | Đã cover đầy đủ tính năng lõi cho cả 3 modules theo yêu cầu phân tách microservices. |
| 4. Feasibility | ✅ PASS | Thiết kế dựa trên Spring Boot & JWT là industry standard, hoàn toàn khả thi để implement. |
| 5. Gap Coverage | ✅ PASS | Các rủi ro về distributed data (block user) đã được recommend xử lý bằng Event (Kafka/RMQ). |
""",

    "handoff_summary.md": """---
type: research_handoff
feature: core-microservices-features
date: 2026-08-24
recommendation: build
research_dir: openspec/research/core-microservices/
status: complete
---

# Research Handoff: Core Microservices Features (Auth, Account, Admin)

## Recommendation
Triển khai giải pháp **Custom Microservices (Build)** thay vì phụ thuộc hoàn toàn vào một External IdP (như Keycloak) để dễ dàng kiểm soát luồng nghiệp vụ đặc thù (eKYC, quản lý thiết bị). Tách biệt cơ sở dữ liệu để đảm bảo kiến trúc loosely coupled.

## Key Findings
- **Data Segregation**: Việc tách `credentials` (auth-service), `profile` (account-service) và `RBAC` (system-admin-service) là best practice, nhưng cần xử lý Eventual Consistency khi một user bị khóa (Block).
- **Authentication**: JWT (JSON Web Token) ký bằng RSA (Asymmetric encryption) để các service khác có thể tự verify mà không cần call lại auth-service.

## Use Cases Identified
- **UC-AUTH**: Login, Refresh Token, Đổi mật khẩu.
- **UC-ACC**: Quản lý Profile, Định danh KYC, Quản lý thiết bị đăng nhập.
- **UC-ADM**: Quản lý phân quyền RBAC, Audit Log hệ thống, Khóa/Mở khóa User.

## Ready for
- `/wf_brainstorm_openspec core-microservices --from-research` — để đào sâu về giải pháp messaging giữa 3 service.
- `/wf_pre_openspec openspec/research/core-microservices/business_analysis.md` — để tiến hành formal URD analysis cho phase code tiếp theo.

## Research Artifacts
| File | Content |
|------|---------|
| [research_brief.md](./research_brief.md) | Scope, keywords, current system |
| [opensource_findings.md](./opensource_findings.md) | Open source evaluation |
| [web_research.md](./web_research.md) | Internet research |
| [comparison_analysis.md](./comparison_analysis.md) | Comparison + gap analysis |
| [business_analysis.md](./business_analysis.md) | Business analysis (use cases) |
| [technical_spec.md](./technical_spec.md) | Technical specification |
| [validation_report.md](./validation_report.md) | Quality review results |
"""
}

for filename, content in files.items():
    path = os.path.join(base_dir, filename)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

print(f"Successfully generated {len(files)} research artifacts in {base_dir}")
