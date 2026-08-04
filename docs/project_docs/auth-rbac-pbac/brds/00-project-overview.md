# BRD-00: Project Overview

## Tên dự án
**Auth RBAC+PBAC Multi-Domain Permission Microservice**

## Tầm nhìn (Vision)
Xây dựng một hệ thống phân quyền modular, tái sử dụng được cho nhiều domain business khác nhau (booking, rental, loyalty...) với kiến trúc RBAC+PBAC, hỗ trợ phân bậc user qua Group → Role → Permission, và tích hợp policy engine cho dynamic access control.

## Mục tiêu (Goals)

### Business Goals
1. **Reusability**: Một auth service dùng chung cho N domain business khác nhau
2. **Flexibility**: Mỗi domain tự định nghĩa roles, resources, permissions riêng
3. **Security**: Fine-grained access control đến từng module/resource
4. **Scalability**: Hỗ trợ mở rộng thêm domain mới mà không sửa code engine

### Technical Goals
1. **Clean Architecture**: Tách biệt domain logic, infrastructure, presentation
2. **Custom Engine**: Tự build PBAC engine, không phụ thuộc external service
3. **2-Stage Authorization**: Early decision (JWT/role) + Postponed decision (query-level)
4. **Database-driven**: Roles, permissions, policies lưu DB, cấu hình qua API
5. **PostgreSQL**: Tận dụng JSONB cho policy conditions, RLS cho tenant isolation

## Success Criteria

| # | Metric | Target |
|:--|:-------|:-------|
| SC-01 | Đăng ký domain mới | < 5 phút qua API |
| SC-02 | Thêm role mới cho domain | < 2 phút qua API |
| SC-03 | Permission check latency | < 10ms (cached), < 50ms (uncached) |
| SC-04 | Support domains | ≥ 3 domains (booking, rental, loyalty) |
| SC-05 | Read-only enforcement | 100% enforcement — no write/delete cho read-only users |
| SC-06 | JWT claim size | < 2KB (roles + permissions) |
| SC-07 | Test coverage | > 80% |

## Phạm vi MVP

### In Scope (MVP)
- Domain registration & management
- User CRUD + multi-domain membership
- Group management (domain-scoped)
- Role management with hierarchy
- Permission matrix (Resource × Action)
- Policy engine (JSONB conditions)
- Read-only access enforcement
- JWT token generation with embedded permissions
- RESTful Admin APIs
- PostgreSQL + Flyway migrations
- Docker Compose dev environment

### Out of Scope (Post-MVP)
- UI Admin Dashboard (chỉ có API)
- OAuth2 social login (Google, GitHub)
- API Gateway integration (Spring Cloud Gateway)
- Query rewriting (thunx-style postponed decisions)
- Redis caching layer
- Event-driven audit logging
- Multi-tenancy with RLS isolation
