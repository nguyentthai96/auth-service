# Research Brief: FR-010-menu-permission

> Research findings for Menu Permission Management feature implementation.

## Keywords

Menu Permission Management, Enterprise IAM, Spring Boot, Kotlin, Redis, PostgreSQL, Clean Architecture

## 1. Problem Statement

Hệ thống phải cấu hình cây menu và button permission linh hoạt theo role, department. User override ưu tiên cao hơn role permission, dùng Redis cache.

## 2. Industry Analysis

### 2.1 Current Landscape
Enterprise IAM systems require robust menu permission management capabilities. Leading solutions include Keycloak, Auth0, and Okta — each providing configurable menu permission management workflows.

### 2.2 Best Practices
- Audit trail for all state changes
- Configurable policies per domain/tenant
- Redis caching for performance-critical paths
- Event-driven architecture for cross-service notifications

## 3. Technical Analysis

### 3.1 Architecture Patterns
- **CQRS**: Separate command/query for menu permission management operations
- **Event Sourcing**: Track all state transitions for compliance
- **Cache-Aside**: Redis for frequently accessed configurations

### 3.2 Technology Stack
| Component | Technology | Rationale |
|-----------|-----------|-----------|
| Backend | Spring Boot + Kotlin | Existing stack |
| Database | PostgreSQL | ACID compliance, JSONB support |
| Cache | Redis | Sub-millisecond lookups |
| Migration | Flyway | Version-controlled schema |

## 4. Key Findings

### 4.1 Functional Requirements
- FR-010.1: Menu tree CRUD (hierarchical menu configuration)
- FR-010.2: Role-based menu permission assignment
- FR-010.3: User-level permission override (higher priority than role)
- FR-010.4: Department-based menu filtering
- FR-010.5: Button/action-level permission control
- FR-010.6: Redis-cached permission resolution

### 4.2 Non-Functional Requirements
| NFR | Target |
|-----|--------|
| Response time | < 50ms for cached operations |
| Availability | 99.9% uptime |
| Audit | Complete audit trail |

## 5. Recommendations

Implement using existing Clean Architecture patterns in auth-service. Use Redis for caching with configurable TTL. Follow existing entity patterns (SnowflakePersistentAuditableEntity).

## 6. References

- OWASP IAM Guidelines
- GDPR Compliance Framework
- Spring Security Reference Documentation
