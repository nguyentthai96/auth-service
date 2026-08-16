# Technical Specification: FR-012-api-partner

> Detailed technical specification for API Partner Management implementation.

## Keywords

API Partner Management, Technical Specification, API Design, Database Design, Redis, Spring Boot, Kotlin

## 1. Overview

### 1.1 Feature Description
Hệ thống phải cấp phát và quản lý API key cho partner. Rate limiting, API key rotation, usage tracking.

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
| `ApiPartnerEntity` | Entity | Partner registration entity |
| `ApiKeyEntity` | Entity | API key tracking entity |
| `ApiPartnerRepository` | Repository | JPA repository |
| `ApiKeyRepository` | Repository | JPA repository for keys |
| `ApiPartnerService` | Service | Partner management logic |
| `ApiKeyService` | Service | Key generation/rotation logic |
| `ApiRateLimitService` | Service | Redis rate limiting |
| `ApiUsageTracker` | Service | Usage tracking |
| `ApiPartnerController` | Controller | Partner management APIs |
| `ApiKeyAuthFilter` | Filter | API key authentication filter |

### 2.2 Modified Components
| Component | Change |
|-----------|--------|
| SecurityConfig | Register new filters |
| application.yml | Add configuration block |

## 3. Database Design

### 3.1 Tables
Primary table: `api_partners, api_keys, api_usage_logs`

### 3.2 Indexes
- Composite indexes on frequently queried columns
- Partial indexes for active records

## 4. Redis Design

### 4.1 Key Patterns
api:rate:{partnerId} (SortedSet, TTL=60s), api:key:{hashedKey} (Hash, TTL=3600s)

### 4.2 Cache Strategy
- Cache-Aside pattern with configurable TTL
- Cache invalidation on write operations
- Graceful degradation when Redis unavailable

## 5. API Design

### 5.1 Endpoints
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/admin/partners` | Register partner |
| GET | `/api/v1/admin/partners` | List partners |
| POST | `/api/v1/admin/partners/{id}/keys` | Generate API key |
| POST | `/api/v1/admin/partners/{id}/keys/rotate` | Rotate API key |
| DELETE | `/api/v1/admin/partners/{id}/keys/{keyId}` | Revoke API key |
| GET | `/api/v1/admin/partners/{id}/usage` | Usage analytics |

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
