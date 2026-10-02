# Delta Spec: Password Policy Refactoring

**Original Behavior:**
- Password policies were stored in the `password_policies` table inside `auth-service` database.
- Updates were handled directly by `PasswordPolicyService` which updated the local database.

**New Behavior:**
- Password policies are stored in the `system_configs` table inside `system-admin-service` database.
- `system-admin-service` acts as the central source of truth.
- Updates to policies emit `system.config.changed` Kafka events and sync to a Redis Hash (`system:config:auth_login`).
- `auth-service` uses `PasswordPolicyConfigProvider` with a 4-tier fallback architecture (Caffeine L1 -> Redis L2 -> API L3 -> Default L4) to resolve policies, and listens to Kafka events to invalidate its L1 cache.
