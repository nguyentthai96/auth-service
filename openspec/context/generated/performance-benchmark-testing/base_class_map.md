# Base Class Map

_Generated: 2026-09-08 | Services: auth-service_

## Controller

- TokenController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`
- SessionController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt`
- MfaController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt`
- SsoController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt`
- CaptchaController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CaptchaController.kt`
- KeyExchangeController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/KeyExchangeController.kt`
- AnonymousAuthController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt`
- AccountLifecycleController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt`
- DeviceController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/DeviceController.kt`
- AdminSessionController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt`
- InternalApiController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/InternalApiController.kt`
- RateLimitAdminController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt`
- EventStoreController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/EventStoreController.kt`
- CqrsAuthController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
- PolicyController — `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt`
- RolePermissionController — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RolePermissionController.kt`
- RbacControllers (5 sub-controllers) — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`

## Handler

NOT DETECTED — no abstract base handler class

## Factory

NOT DETECTED

## Client / Gateway

- SsoProviderClient (@HttpExchange) — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`
- CaptchaClient (@HttpExchange) — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
- HttpSsoGateway — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt`
- HttpCaptchaGateway — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
- HttpClientConfig — `src/main/kotlin/com/ntt/authservice/shared/config/HttpClientConfig.kt`

## Configuration (Performance-relevant)

- RedisConfig — `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
- HttpClientConfig — `src/main/kotlin/com/ntt/authservice/shared/config/HttpClientConfig.kt`

## Benchmark (Existing)

- EncryptionBenchmark (JMH) — `src/jmh/kotlin/com/ntt/authservice/benchmark/EncryptionBenchmark.kt`
- SerializationBenchmark (JMH) — `src/jmh/kotlin/com/ntt/authservice/benchmark/SerializationBenchmark.kt`

## NOT DETECTED

- Abstract base controller class (controllers are standalone)
- Abstract factory pattern
- Base handler hierarchy
