# Initial Idea: Auth RBAC+PBAC Multi-Domain Permission Microservice

## Raw Idea
Build một modular micro auth service RBAC + PBAC permission với user group theo API domain.
Dùng chung cho nhiều domain: booking, customer loyalty, đặt/thuê nhà trọ.
Có nhiều user phân bậc: chủ trọ, người thuê, nhân viên tiếp tân, khách thuê, chủ khách sạn, dọn phòng...
Mỗi role sẽ có quyền hạn truy cập riêng từng module.
Một số user chỉ cho đọc, không được ghi hay xóa.

## Project Name
auth-rbac-pbac

## Target Service
auth-service (existing Spring Boot Kotlin project)

## MVP Features
1. **Multi-Domain Registration** — Domains (booking, rental, loyalty) đăng ký và cấu hình riêng
2. **User Management** — CRUD users với profile, đa domain membership
3. **Group Management** — Users → Groups (scoped by domain)
4. **Role Management** — Domain-specific roles với hierarchy level
5. **Permission Matrix** — Resource × Action permissions gán cho roles
6. **Policy Engine** — Custom PBAC engine đánh giá dynamic conditions (JSONB)
7. **Read-Only Access** — Hỗ trợ users chỉ có quyền READ trên tất cả resources
8. **JWT Integration** — Embed roles/permissions trong JWT claims
9. **API Endpoints** — RESTful admin APIs cho permission management

## Constraints
- Spring Boot + Kotlin (existing tech stack)
- PostgreSQL (confirmed database)
- Custom engine (không dùng OPA/Casbin/Keycloak external)
- Tham khảo thunx architecture: 2-stage decision (early + postponed)
- Docker Compose cho dev environment
- SQL migration scripts (Flyway)
- Clean/Hexagonal Architecture compliance (base-core standards)

## Domains & Roles
| Domain | Roles |
|:---|:---|
| Booking (Khách sạn) | HOTEL_OWNER, RECEPTIONIST, HOUSEKEEPER, GUEST |
| Rental (Nhà trọ) | LANDLORD, TENANT |
| Customer Loyalty | LOYALTY_ADMIN, LOYALTY_MANAGER, MEMBER |

## Key Decisions
- **Engine**: Custom-built (inspired by thunx 2-stage architecture)
- **Scope**: Phase 0-4 only (Research → Code Gen, skip Deploy)
- **Existing code**: Redesign from scratch
- **Database**: PostgreSQL with Flyway migrations + Docker Compose
