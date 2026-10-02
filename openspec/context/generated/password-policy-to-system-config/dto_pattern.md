# DTO Pattern

_Generated: 2026-10-02_

## Request DTO

- `ChangePasswordRequest`: `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
  - Annotations: `@field:NotBlank`, `@field:Size`
- `RegisterRequestDto`: `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`
- `RateLimitConfigRequest`: `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/apipartner/adapter/in/web/ApiUsageController.kt`

## Response DTO

- `ApiResponse<T>`: `services/auth-service/src/main/kotlin/com/ntt/basecore/domain/web/payload/ApiResponse.kt` (Chuẩn phản hồi chung từ base-core)
- `AuthResponse`: `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`

## Configuration POJO (NEW)

- `PasswordPolicyConfig`: Pure data class đại diện cho cấu hình chính sách mật khẩu nạp từ dynamic config provider:
  ```kotlin
  data class PasswordPolicyConfig(
      val minLength: Int = 8,
      val maxLength: Int = 128,
      val requireUppercase: Boolean = true,
      val requireLowercase: Boolean = true,
      val requireDigit: Boolean = true,
      val requireSpecial: Boolean = false,
      val minCharacterTypes: Int = 3,
      val historyCount: Int = 5,
      val maxAgeDays: Int = 90,
      val lockoutThreshold: Int = 5,
      val lockoutDurationMinutes: Int = 15
  )
  ```

## NOT DETECTED

- XML/SOAP DTOs (N/A)
