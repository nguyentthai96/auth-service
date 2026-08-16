# SRS: FR-012-api-partner

> **Change**: FR-012-api-partner | **Type**: NEWBUILD | **Version**: 1.0

## 1. Functional Requirements

### FR-012: API Partner Management [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cấp phát và quản lý API key cho partner
- **Validation**: Rate limiting, API key rotation, usage tracking

#### Sub-requirements:
- FR-012.1: API key generation (SHA-256 hashed storage)
- FR-012.2: API key rotation with grace period
- FR-012.3: Rate limiting per partner (Redis sliding window)
- FR-012.4: API usage tracking and analytics
- FR-012.5: Partner onboarding/offboarding workflow
- FR-012.6: API key scope/permission management

## 2. Non-Functional Requirements

| NFR | Requirement | Target |
|-----|------------|--------|
| Performance | Cached read | < 5ms |
| Performance | Write operation | < 50ms |
| Availability | Feature uptime | 99.9% |
| Security | Permission enforcement | Per-endpoint |

## 3. API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/admin/partners` | Register partner |
| GET | `/api/v1/admin/partners` | List partners |
| POST | `/api/v1/admin/partners/{id}/keys` | Generate API key |
| POST | `/api/v1/admin/partners/{id}/keys/rotate` | Rotate API key |
| DELETE | `/api/v1/admin/partners/{id}/keys/{keyId}` | Revoke API key |
| GET | `/api/v1/admin/partners/{id}/usage` | Usage analytics |

## 4. Data Model

### Table: `api_partners, api_keys, api_usage_logs`
- Standard audit columns (created_at, updated_at)
- Snowflake ID primary key

## 5. Error Codes

| Code | Description |
|------|-------------|
| RESOURCE_NOT_FOUND | Requested resource does not exist |
| VALIDATION_ERROR | Request validation failed |
| INSUFFICIENT_PERMISSION | User lacks required permission |


## 6. Acceptance Criteria

- [ ] All sub-requirements implemented with unit tests
- [ ] Performance targets met (< 50ms write, < 5ms cached read)
- [ ] Audit trail complete for all state change operations
- [ ] Integration tests passing with Testcontainers
- [ ] API documentation generated and verified
- [ ] Redis cache invalidation working correctly
- [ ] Error responses follow RFC 7807 ProblemDetail format
- [ ] Security permissions enforced per endpoint
- [ ] Flyway migration runs successfully on empty and existing databases
- [ ] Graceful degradation when Redis is unavailable

## 7. Glossary

| Term | Definition |
|------|-----------|
| SSoT | Single Source of Truth |
| TTL | Time To Live — cache expiration duration |
| GDPR | General Data Protection Regulation |
| RFC 7807 | Problem Details for HTTP APIs |
| CQRS | Command Query Responsibility Segregation |
| Cache-Aside | Pattern where app checks cache first, falls back to DB |


## 8. Traceability Matrix

| Requirement | Component | Test Case |
|-------------|-----------|-----------|
| Sub-req 1 | Service | TC-001 |
| Sub-req 2 | Service | TC-002 |
| Sub-req 3 | Controller | TC-003 |
| Sub-req 4 | Repository | TC-004 |

## 9. Interface Requirements

### 9.1 Internal APIs
- All internal APIs use JSON format
- Content-Type: application/json
- Accept: application/json

### 9.2 Error Response Format (RFC 7807)
```json
{
  "type": "about:blank",
  "title": "Error Title",
  "status": 400,
  "detail": "Detailed error message",
  "instance": "/api/v1/resource/123"
}
```

## 10. Deployment Requirements

- Zero-downtime deployment supported
- Database migration runs before application startup
- Redis cache warming on first access
- Health check endpoint available at /actuator/health
