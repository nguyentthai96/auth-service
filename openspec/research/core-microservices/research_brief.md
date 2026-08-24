# Research Brief: Core Microservices (Auth, Account, System-Admin)

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
