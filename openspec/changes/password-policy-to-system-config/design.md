## Context

The system requires password policies to be managed centrally while maintaining the high availability and extremely low latency necessary for the authentication critical path. As analyzed in the proposal, moving the configuration to the centralized `system_configs` requires a resilient delivery mechanism to the `auth-service`. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Provide zero-latency local caching for the authentication flow.
- Ensure automated fallback mechanisms (In-Memory -> Redis -> API -> Local YAML) in case of system component failures.
- Enable immediate configuration propagation when changes are made.

**Non-Goals:**
- Modifying or dropping the `password_history` table (it stores transaction-like data, not system configuration).
- Modifying the underlying Passay password validation logic; only the configuration source changes.

## Decisions

1. **4-Tier Resilient Configuration Provider**:
   - **Rationale**: To guarantee that authentication never goes down due to config unavailability while keeping latency at zero when possible.
   - **L1 (In-Memory)**: Caffeine cache with short TTL (e.g., 60s). It provides zero-latency reads.
   - **L2 (Redis Hash)**: `system:config:AUTH_LOGIN` acts as a shared, fast-access cache populated by `system-admin-service`.
   - **L3 (HTTP/gRPC API)**: Synchronous fallback to `system-admin-service` if Redis is empty (e.g., initial startup or Redis wipe). Populates L2 and L1 upon success.
   - **L4 (Static YAML)**: Final fallback to `SecurityProperties.PasswordProperties` if all networked services fail.

2. **Event-driven Cache Invalidation**:
   - **Rationale**: While L1 cache has a TTL, waiting up to 60s for a critical security policy change is undesirable.
   - `auth-service` will listen to the `system.config.changed` Kafka topic (with webhook fallback) and invalidate the L1 cache for the `AUTH_LOGIN` group immediately upon receipt.

3. **Data Structure in Redis**:
   - **Rationale**: Using a Redis Hash (`HGETALL system:config:AUTH_LOGIN`) allows fetching the entire group's configuration in a single atomic operation without scanning keys. Keys will retain their full names (e.g., `password.min_length`).

4. **Deprecation of PasswordPolicyEntity**:
   - **Rationale**: The database table and associated entity/repository in `auth-service` are fully deprecated in favor of `PasswordPolicyConfig`, a pure POJO mapped from the multi-tier provider.

## Risks / Trade-offs

- **[Risk]** Kafka event lag or missed delivery causes `auth-service` to use stale configuration.
  - **Mitigation**: The L1 Caffeine cache uses a 60s TTL (`expireAfterWrite = 60s`). Even if an event is missed, the cache naturally refreshes from Redis within a minute.
- **[Risk]** Redis and Kafka dependency in critical path.
  - **Mitigation**: Addressed by L3 API fallback and L4 YAML static fallback.

## Migration Plan

1. Deploy the new configuration seeding script (`V10__seed_auth_login_configs.sql` or similar) to `system-admin-service`.
2. Deploy the updated `system-admin-service` to enable Redis sync and Kafka event publishing for configurations.
3. Deploy the updated `auth-service` which reads from the new provider instead of the local DB.
4. Run Flyway migration (`V26__drop_password_policies_table.sql`) in `auth_db` to drop the old table.
