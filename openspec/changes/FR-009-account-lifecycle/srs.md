# SRS: FR-009-account-lifecycle

> **Change**: FR-009-account-lifecycle | **Type**: NEWBUILD | **Version**: 1.0

## 1. Functional Requirements

### FR-009: Account Lifecycle Management [URD]
- **Actor**: End User
- **Action**: Hệ thống phải cho phép deactivate, request delete (GDPR), export data
- **Validation**: Đảm bảo compliance GDPR

#### Sub-requirements:
- FR-009.1: Account deactivation (soft delete, reversible within 30 days)
- FR-009.2: GDPR data deletion request (hard delete after grace period)
- FR-009.3: Personal data export (JSON format, GDPR Article 20)
- FR-009.4: Account reactivation within grace period
- FR-009.5: Account status audit trail
- FR-009.6: Admin account status management

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
| POST | `/api/v1/account/deactivate` | Deactivate account |
| POST | `/api/v1/account/reactivate` | Reactivate within grace period |
| POST | `/api/v1/account/delete-request` | Request GDPR deletion |
| GET | `/api/v1/account/export` | Export personal data |
| GET | `/api/v1/admin/accounts/lifecycle` | Admin view lifecycle events |

## 4. Data Model

### Table: `account_lifecycle_events`
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
