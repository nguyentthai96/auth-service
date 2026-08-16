# SRS: FR-016-domain-config

> **Change**: FR-016-domain-config | **Type**: NEWBUILD | **Version**: 1.0

## 1. Functional Requirements

### FR-016: Domain Configuration Management [URD]
- **Actor**: System Administrator
- **Action**: Hệ thống phải cấu hình đa domain/tenant cho IAM
- **Validation**: Domain isolation, config inheritance, runtime reload

#### Sub-requirements:
- FR-016.1: Multi-domain/tenant configuration
- FR-016.2: Domain-specific auth policies (password policy, MFA, session timeout)
- FR-016.3: Config inheritance (global → domain → override)
- FR-016.4: Runtime config reload without restart
- FR-016.5: Domain branding/customization settings
- FR-016.6: Domain admin delegation

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
| GET | `/api/v1/admin/domains` | List domains |
| POST | `/api/v1/admin/domains` | Create domain config |
| PUT | `/api/v1/admin/domains/{id}` | Update domain config |
| GET | `/api/v1/admin/domains/{id}/effective` | Get effective config (inherited) |
| POST | `/api/v1/admin/domains/{id}/reload` | Reload config from DB |

## 4. Data Model

### Table: `domain_configs`
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
