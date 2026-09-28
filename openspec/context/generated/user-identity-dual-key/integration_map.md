# Integration Map — user-identity-dual-key

## Internal Integrations

| Component | Protocol | Direction | File |
|-----------|----------|-----------|------|
| base-core (base-model) | Maven dependency | Inbound | `DualIdPersistentAuditableEntity` — [`DualIdEntities.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/base-model/src/main/kotlin/com/ntt/basecore/model/id/DualIdEntities.kt) |
| PostgreSQL 17 | JDBC/JPA | Outbound | `application.yml` → datasource config |
| Flyway | Migration | Internal | `resources/db/migration/V24_*.sql`, `V25_*.sql` |

## External Integrations

NOT DETECTED — feature is internal to auth-service + base-core.

## Cross-Service Communication

| Service | Current Reference | Impact |
|---------|-------------------|--------|
| account-service | References user by `id` (Long) via Kafka events | 🟡 Future: may need to switch to `uuid` |
| notification-service | References user by `id` (Long) via Kafka events | 🟡 Future: may need to switch to `uuid` |
| system-admin-service | References user by `id` (Long) via REST | 🟡 Future: may need to switch to `uuid` |

> **Note**: Cross-service migration to `uuid` is OUT OF SCOPE for this change. Deferred to separate change.
