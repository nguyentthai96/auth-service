# Research Notes: Auth RBAC+PBAC

## 1. Authorization Models Analyzed

### RBAC (Role-Based Access Control)
- Coarse-grained: User → Role → Permission
- Simple, well-understood, industry standard
- Risk: Role Explosion khi có nhiều domain

### PBAC/ABAC (Policy/Attribute-Based Access Control)
- Fine-grained: Evaluate dynamic conditions (attributes, context)
- Example: "User can edit only if resource.owner_id == user.id"
- Flexible nhưng complex implementation

### ReBAC (Relationship-Based Access Control)
- Google Zanzibar model (SpiceDB, OpenFGA)
- Graph-based permissions
- Overkill cho MVP, nhưng có thể migrate sau

## 2. Industry Solutions Evaluated

| Solution | Type | Pros | Cons | Fit |
|:---|:---|:---|:---|:---:|
| **OPA** | External engine | Industry standard, Rego | Learning curve, infra | ⭐⭐⭐ |
| **Casbin** | Embedded lib | Lightweight, flexible | Hard to scale distributed | ⭐⭐ |
| **Keycloak** | Full IAM | Complete solution | Heavy, overkill | ⭐ |
| **SpiceDB** | ReBAC engine | Scalable relationships | Complex, separate infra | ⭐⭐ |
| **Cerbos** | YAML-based PDP | Developer-friendly | Still external component | ⭐⭐⭐ |
| **Thunx** | Middleware | 2-stage architecture, Spring native | Requires OPA | ⭐⭐⭐⭐ |
| **Custom** | Self-built | Full control, no dependency | More effort | ⭐⭐⭐⭐ |

### Decision: Custom engine lấy cảm hứng từ Thunx

## 3. Thunx Architecture Insights (Tham khảo)

### 2-Stage Authorization
1. **Early Decision (Gateway/Filter)**: JWT validation → check role-based rules → ALLOW/DENY
2. **Postponed Decision (Data Tier)**: Query rewriting → inject authorization predicate vào DB query

### Key Concepts để áp dụng:
- **Authorization Predicate**: Expression tree (thunk) mô tả điều kiện truy cập
- **Query Rewriting**: Modify JPA/SQL query dựa trên predicate
- **Decoupling**: Business logic không biết authorization logic
- **Performance**: Query-level filtering thay vì load-then-filter

## 4. Database Schema Patterns

### Multi-Tenant RBAC Schema (Best Practice)
```
users (global identity)
  └── user_domains (membership per domain)
       └── user_groups (membership per group)
            └── groups (scoped to domain)
                 └── group_roles (role assignment)
                      └── domain_roles (role definition)
                           └── role_permissions (permission grant)
                                └── permissions (resource × action)
                                     ├── domain_resources
                                     └── actions
```

### PostgreSQL-Specific Features
- **JSONB columns** cho policy conditions (flexible schema)
- **Row-Level Security (RLS)** cho tenant isolation
- **Composite indexes** trên (user_id, domain_id, resource_id)
- **UUID primary keys** cho distributed-safe IDs

## 5. Domain Analysis

### Booking Domain
- Multi-property: mỗi khách sạn là 1 unit
- Shift-based: nhân viên tiếp tân theo ca
- Real-time: room status, booking availability
- Roles: HOTEL_OWNER (full), RECEPTIONIST (booking+rooms), HOUSEKEEPER (room status), GUEST (own booking)

### Rental Domain
- Contract-based: hợp đồng thuê trọ
- Billing: tiền thuê hàng tháng
- Roles: LANDLORD (full), TENANT (own room + own payments)

### Customer Loyalty Domain
- Tier-based: Gold/Silver/Bronze
- Points: tích điểm, đổi điểm
- Roles: LOYALTY_ADMIN (full), LOYALTY_MANAGER (manage members), MEMBER (own profile + points)

## 6. Existing Codebase Analysis

### Auth-Service Current State
- **Framework**: Spring Boot + Kotlin, Java 21
- **Security**: JWT-based (JwtRequestFilter, JwtUtil)
- **Entity**: UserInfoEntity extends BaseEntityPersistentAuditable (simple, no roles/groups)
- **Domain model**: UserInfo with `roles: String` (comma-separated — needs redesign)
- **Config**: SecurityConfig with BCrypt + stateless session
- **Dependencies**: Spring Security, OAuth2 Authorization Server, JPA, MyBatis

### Base-Core Components Available
- `BaseEntity<ID>` — simple entity (no audit)
- `PersistentAuditableEntity<ID>` — with audit + soft-delete (active flag)
- `SecurityAutoConfiguration` — auto-config stub
- `base-security-starter` — starter module (mostly placeholder)
- Clean Architecture structure via starters

### Gap Analysis
| Feature | Current | Needed |
|:---|:---|:---|
| User entity | Simple (username, password, email) | + domain membership, group, avatar |
| Roles | Comma-separated string | Relational: User → Group → Role |
| Permissions | None | Resource × Action matrix |
| Policy engine | None | JSONB condition evaluator |
| Multi-domain | None | Domain registration + scoping |
| Groups | None | Group entity with domain scope |
