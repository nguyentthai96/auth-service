## Why

Currently, the `auth-service` manages password policies via a standalone, single-row table `password_policies` in its own database. This approach causes configuration fragmentation, lacks a unified UI for administrators, and is inconsistent with the centralized configuration management architecture (`system_configs` in `system-admin-service`). Centralizing this configuration under the `AUTH_LOGIN` group will provide a Single Source of Truth, enable audit logging, and allow for a 4-tier fallback architecture to ensure high availability and extremely low latency during authentication.

## What Changes

- **Migration**: Drop the legacy `password_policies` table in `auth-service` (keeping `password_history`).
- **Centralization**: Move all password policy parameters to the `system_configs` table in `system-admin-service` under the `AUTH_LOGIN` group.
- **Event Sync**: Implement Redis hash (`system:config:AUTH_LOGIN`) synchronization and publish Kafka events (`system.config.changed`) from `system-admin-service` when configurations are updated.
- **Multi-tier Caching**: Refactor `auth-service` to use a 4-tier provider mechanism (`PasswordPolicyConfigProvider`) consisting of L1 In-Memory Cache (Caffeine), L2 Redis Hash, L3 HTTP/gRPC Fallback to sys-admin, and L4 Static YAML Fallback.
- **Refactoring**: Replace `PasswordPolicyRepository` and `PasswordPolicyEntity` with the new configuration provider in `PasswordPolicyService`.

## Capabilities

### New Capabilities
- `password-policy-config`: Centralized management, event-driven synchronization, and multi-tier resilient consumption of password policy configurations.

### Modified Capabilities

## Impact

- **Affected Systems**: `auth-service` (consumer) and `system-admin-service` (publisher).
- **Database**: `auth_db` (dropping `password_policies` table), `system_admin_db` (inserting seed data for `AUTH_LOGIN` group).
- **Code**: Removal of `PasswordPolicyEntity` and repository in `auth-service`. Modifications to `PasswordPolicyService` and related controllers. Additions of sync services and event publishers in `system-admin-service`.
- **Infrastructure**: Heavier reliance on Redis and Kafka for real-time configuration invalidation, which is gracefully mitigated by the L3/L4 fallback architecture.
