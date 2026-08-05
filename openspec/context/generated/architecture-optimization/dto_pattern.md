# DTO Pattern

_Generated: 2026-08-05_

## Request DTO

- `MfaDtos.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - Contains MFA-related request/response DTOs
  - Annotations: data class (Kotlin)

- `SsoDtos.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
  - Contains SSO-related request/response DTOs
  - Annotations: data class (Kotlin)

- `TokenDtos.kt` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - Contains token-related request/response DTOs
  - Annotations: data class (Kotlin)

- **Co-located DTOs (anti-pattern):**
  - `RegisterRequest` — `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt` (INLINE in service file!)
  - `LoginRequest` — `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt` (INLINE in service file!)
  - `AuthResponse` — `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt` (INLINE in service file!)
  - `LoginResult` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginResult.kt`

## Response DTO

- Same files as Request DTOs (Kotlin data classes serve dual purpose)
- `LoginResult.kt` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginResult.kt` (sealed class with MfaRequired/Success variants)

## DTO Base Classes (base-core)

- `DtoConvertible` — `components/base-core/base-core/src/main/kotlin/.../DtoConvertible.kt`
  - Interface for domain ↔ DTO conversion
  - NOT USED in auth-service

- `BaseMapper` — `components/base-core/base-core/src/main/kotlin/.../BaseMapper.kt`
  - MapStruct base interface
  - NOT USED in auth-service

- `CrudMapper` — `components/base-core/base-core/src/main/kotlin/.../CrudMapper.kt`
  - CRUD-oriented mapper interface
  - NOT USED in auth-service

## NOT DETECTED

- `@SuperBuilder` — N/A (Kotlin, not Java)
- `@Builder` — N/A (Kotlin data classes)
- Base Request/Response extending base-core classes — NOT USED
- MapStruct `@Mapper` — NOT USED (available in version catalog: mapstruct 1.6.3)
- Bean Validation annotations (`@NotBlank`, `@Size`, `@Valid`) — detected in some DTOs
