# Technical Specification: FR-016-domain-config

> Detailed technical specification for Domain Configuration Management implementation.

## Keywords

Domain Configuration Management, Technical Specification, API Design, Database Design, Redis, Spring Boot, Kotlin

## 1. Overview

### 1.1 Feature Description
Hệ thống phải cấu hình đa domain/tenant cho IAM. Domain isolation, config inheritance, runtime reload.

### 1.2 Architecture Decision
Implement within auth-service following Clean Architecture:
- **Adapter In (Web)**: Controllers, DTOs, Filters
- **Application**: Services, Schedulers
- **Domain**: Entities, Enums, Value Objects
- **Adapter Out (Persistence)**: Repositories, Redis Stores

## 2. Component Design

### 2.1 New Components
| Component | Type | Description |
|-----------|------|-------------|
| `DomainConfigEntity` | Entity | Domain configuration entity |
| `DomainConfigRepository` | Repository | JPA repository |
| `DomainConfigService` | Service | Config resolution with inheritance |
| `DomainConfigCacheService` | Service | Redis cache for configs |
| `DomainConfigController` | Controller | Config management APIs |

### 2.2 Modified Components
| Component | Change |
|-----------|--------|
| SecurityConfig | Register new filters |
| application.yml | Add configuration block |

## 3. Database Design

### 3.1 Tables
Primary table: `domain_configs`

### 3.2 Indexes
- Composite indexes on frequently queried columns
- Partial indexes for active records

## 4. Redis Design

### 4.1 Key Patterns
domain:config:{domainId} (Hash, TTL=3600s), domain:config:global (Hash, TTL=3600s)

### 4.2 Cache Strategy
- Cache-Aside pattern with configurable TTL
- Cache invalidation on write operations
- Graceful degradation when Redis unavailable

## 5. API Design

### 5.1 Endpoints
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/admin/domains` | List domains |
| POST | `/api/v1/admin/domains` | Create domain config |
| PUT | `/api/v1/admin/domains/{id}` | Update domain config |
| GET | `/api/v1/admin/domains/{id}/effective` | Get effective config (inherited) |
| POST | `/api/v1/admin/domains/{id}/reload` | Reload config from DB |

### 5.2 Error Handling
| Error | HTTP Code | Error Code |
|-------|-----------|------------|
| Not Found | 404 | RESOURCE_NOT_FOUND |
| Validation Error | 400 | VALIDATION_ERROR |
| Unauthorized | 401 | UNAUTHORIZED |
| Forbidden | 403 | INSUFFICIENT_PERMISSION |
| Conflict | 409 | RESOURCE_CONFLICT |

## 6. Security Considerations

- All admin endpoints require specific permissions
- Rate limiting on public endpoints
- Audit logging for all write operations
- Input validation via Bean Validation annotations

## 7. Performance Requirements

| Operation | Target |
|-----------|--------|
| Cached read | < 5ms |
| Write operation | < 50ms |
| Batch operation | < 500ms |

## 8. Testing Strategy

- Unit tests for service layer (80%+ coverage)
- Integration tests for controller layer
- Redis integration tests with embedded Redis
- Database tests with Testcontainers
