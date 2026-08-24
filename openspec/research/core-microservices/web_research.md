# Web Research

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
