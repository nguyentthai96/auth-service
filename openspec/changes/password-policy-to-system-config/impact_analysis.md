# Impact Analysis: password-policy-to-system-config

## 1. Core Files
- `PasswordPolicyEntity.kt` (auth-service) -> DELETE
- `PasswordPolicyRepository.kt` (auth-service) -> DELETE
- `PasswordPolicyService.kt` (auth-service) -> MODIFY
- `CqrsAuthController.kt` (auth-service) -> REUSE (Caller of PasswordPolicyService)
- `AuthController.kt` (auth-service) -> REUSE (Caller of PasswordPolicyService)
- `DomainConfigService.kt` (system-admin-service) -> MODIFY
- `SystemConfigEntity.kt` (system-admin-service) -> REUSE

## 2. Call Tree
### auth-service
- `CqrsAuthController` -> `PasswordPolicyService`
- `AuthController` -> `PasswordPolicyService`

### system-admin-service
- `DomainConfigController` -> `DomainConfigService`

## 3. Blast Radius
- **PasswordPolicyService**: 2 callers (`CqrsAuthController`, `AuthController`). Impact: 🟢 LOW.
- **DomainConfigService**: Modification to add event publishing and Redis sync for `AUTH_LOGIN` config group. Impact: 🟢 LOW (Adding functionality).
- **GitNexus Impact Analysis**: Confirmed LOW risk (2 callers, depth 1) for `PasswordPolicyService`. `DomainConfigService` index was not available, evaluated manually as LOW.

## 4. Reuse Map
- **[REUSE]** `SystemConfigEntity` for storing configuration.
- **[REUSE]** `StringRedisTemplate` in both services for caching.
- **[REUSE]** `KafkaTemplate` for publishing events.
- **[EXTRACT/MODIFY]** `PasswordPolicyService` to use new `PasswordPolicyConfigProvider` instead of `PasswordPolicyRepository`.

## 5. Context Snapshot
- Flow: Non-Financial
- Type: MAINTENANCE
- No new BaseDataFactory since this relies on microservice Spring Boot architecture and not Netty MID pattern.
- The change involves removing legacy single-row table in `auth-service` and using the centralized config management in `system-admin-service`.
