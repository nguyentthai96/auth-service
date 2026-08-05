---
type: brainstorm_notes
change: erp-iam-system
date: 2026-08-05
selected_direction: "Event-Driven & Redis-Backed Decentralized Authorization"
pre_flow: "Non-Financial"
pre_feature_type: "NEWBUILD"
status: complete
---

# Brainstorm Notes: ERP IAM System Architecture

## Date
2026-08-05

## Context
Phân tích chuyên sâu cho hệ thống ERP IAM gồm 3 services: `auth-service`, `account-service`, và `system-admin-service`. Mục tiêu là giải quyết các vấn đề về hiệu năng (API Gateway rate limiting), độ phình to của Token (JWT Bloat khi chứa quá nhiều permission), và tính nhất quán dữ liệu giữa Profile và Auth.

## Questions Asked & Answers

- **Q1:** Làm sao để API Gateway (Spring Cloud Gateway) kiểm tra API Key Rate Limiting mà không bị thắt cổ chai (bottleneck) khi gọi liên tục vào `system-admin-service`?
  - **A:** Sử dụng cơ chế Decentralized. `system-admin-service` chỉ làm nhiệm vụ quản lý (CRUD) và đẩy cấu hình Rate Limit của từng API Key xuống Redis. Gateway đọc trực tiếp từ Redis bằng Bucket4j.

- **Q2:** Quyền hạn (Permissions & PBAC) nên được nhúng vào JWT hay kiểm tra động?
  - **A:** Không nên nhúng vào JWT vì hệ thống ERP có số lượng permission rất lớn, làm token phình to và vượt quá giới hạn header HTTP. JWT chỉ chứa `userId`, `tenantId`, và list `roleIds`. Việc resolve cụ thể quyền (như Menu, Button) được thực hiện tại `system-admin-service` (kết hợp cache Redis).

- **Q3:** Khi một user được Auto-provision qua SSO (Keycloak), làm sao để đồng bộ Profile?
  - **A:** Dùng Event-driven (Kafka). `auth-service` phát event `iam.user.sso_provisioned`, `account-service` consume để tạo profile mặc định.

## Approaches Considered

### Vấn đề 1: Rate Limiting cho API Partner tại Gateway
#### Approach 1: Synchronous Check (Gateway -> SysAdmin)
- **Pros:** Dễ cài đặt, dữ liệu realtime tuyệt đối.
- **Cons:** Tăng độ trễ cho mọi request, `system-admin-service` trở thành Single Point of Failure.

#### Approach 2: Redis-Backed Bucket4j (Decentralized) 👈 *Selected*
- **Pros:** Cực kỳ nhanh (< 5ms overhead), độc lập hoàn toàn với `system-admin-service` lúc runtime.
- **Cons:** Cần đồng bộ trạng thái khi API Key bị revoke đột xuất (dùng Redis Pub/Sub để invalidate local cache).

### Vấn đề 2: Giao tiếp giữa Auth-Service và Account-Service
#### Approach 1: REST API (Synchronous)
- **Pros:** Đơn giản, response ngay lập tức.
- **Cons:** Coupling cao. Khi `account-service` down, luồng đăng ký user bên `auth-service` sẽ fail.

#### Approach 2: Event-Driven qua Kafka 👈 *Selected*
- **Pros:** Loose coupling, chịu tải tốt, retry dễ dàng thông qua dead-letter queue.
- **Cons:** Eventually consistent (Profile có thể xuất hiện sau vài giây). Trong ngữ cảnh đăng ký tài khoản ERP, điều này hoàn toàn chấp nhận được.

## Selected Direction
**Event-Driven & Redis-Backed Decentralized Authorization**: 
- Sử dụng Redis làm xương sống cho việc chia sẻ Context (Rate Limit, Menu Permissions, Active Sessions) giữa Gateway và các Services.
- Sử dụng Kafka cho các luồng thay đổi state không yêu cầu synchronous (User Provisioning, Audit Logging).
- JWT (RS256) được giữ siêu mỏng (lean JWT), chỉ chứa identity và vai trò.

## Pre-classifications (preliminary)
- Feature type: NEWBUILD (3 core modules)
- Flow type: Non-Financial (IAM, Profile, Config)
- Affected modules: auth-service, account-service, system-admin-service, gateway

## Open Questions for Design Phase
- [RESOLVED] Có nên dùng SPI/ServiceLoader cho Base-Core không? -> Đã quyết định ở một nhánh research khác là dùng Spring Boot AutoConfiguration (Hybrid approach).
- [OPEN] Cơ chế invalidate Menu Cache trên Redis khi Role Permissions bị thay đổi ở `system-admin-service` sẽ dùng Redis Pub/Sub hay Kafka?
- [OPEN] Mật khẩu/Secret Key cho API Partner sẽ được mã hóa bằng AES-256 hay hash một chiều (Argon2)? (Khuyến nghị: Hash một chiều nếu không cần show lại, AES nếu bắt buộc).

## Visualization (Kiến trúc tương tác)

```text
    [ API Partner ]           [ End User ]
          │                         │
          ▼                         ▼
  ┌───────────────┐         ┌───────────────┐
  │ API Gateway   │◄──(1)───│ auth-service  │ (Issues Lean JWT RS256)
  │ (Bucket4j)    │         └───────┬───────┘
  └───────┬───────┘                 │ (Kafka Event: user.created)
          │ (2) Read Rate Limit     ▼
          │     & Token Check       │
          ▼                 ┌───────┴───────┐
    [( Redis )] ◄──(3)──────│ account-service│ (Profile, Devices)
          ▲                 └───────────────┘
          │ (4) Sync config
  ┌───────┴───────┐
  │ system-admin  │ (Menus, API Keys, Audit, Workflow)
  └───────────────┘
```
