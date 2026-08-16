# Technical Specification: FR-009-account-lifecycle

> Detailed technical specification for Account Lifecycle Management implementation.

## Keywords

Account Lifecycle Management, Technical Specification, API Design, Database Design, Redis, Spring Boot, Kotlin

## 1. Overview

### 1.1 Feature Description
Hệ thống phải cho phép deactivate, request delete (GDPR), export data. Đảm bảo compliance GDPR.

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
| `AccountLifecycleEntity` | Entity | Account status tracking entity |
| `AccountLifecycleRepository` | Repository | JPA repository |
| `AccountLifecycleService` | Service | Core lifecycle logic |
| `DataExportService` | Service | GDPR data export |
| `AccountDeletionScheduler` | Scheduler | Scheduled deletion after grace period |
| `AccountLifecycleController` | Controller | REST APIs for lifecycle |

### 2.2 Modified Components
| Component | Change |
|-----------|--------|
| SecurityConfig | Register new filters |
| application.yml | Add configuration block |

## 3. Database Design

### 3.1 Tables
Primary table: `account_lifecycle_events`

### 3.2 Indexes
- Composite indexes on frequently queried columns
- Partial indexes for active records

## 4. Redis Design

### 4.1 Key Patterns
account:deletion:{userId} (String, TTL=30d)

### 4.2 Cache Strategy
- Cache-Aside pattern with configurable TTL
- Cache invalidation on write operations
- Graceful degradation when Redis unavailable

## 5. API Design

### 5.1 Endpoints
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/account/deactivate` | Deactivate account |
| POST | `/api/v1/account/reactivate` | Reactivate within grace period |
| POST | `/api/v1/account/delete-request` | Request GDPR deletion |
| GET | `/api/v1/account/export` | Export personal data |
| GET | `/api/v1/admin/accounts/lifecycle` | Admin view lifecycle events |

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
