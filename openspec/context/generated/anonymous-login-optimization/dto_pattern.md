# DTO Pattern: anonymous-login-optimization

> _Generated: 2025-01-20_
> Candidate Service: auth-service (`src/main/kotlin/com/ntt/authservice/`)

---

## 1. Request DTOs

### Location: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`

```kotlin
// Pattern: data class with Jakarta validation annotations
data class LoginRequestDto(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String,
    val domainCode: String? = null,          // Optional fields use nullable + default null
    val captchaToken: String? = null,
    val trustedDeviceHash: String? = null,
    val deviceFingerprint: String? = null,
    val captchaPayload: String? = null
)

data class RegisterRequestDto(
    @field:NotBlank val username: String,
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 8, max = 100) val password: String,
    @field:NotBlank val fullName: String,
    val phone: String? = null,
    val domainCode: String = "default"       // Optional with non-null default
)

data class RefreshTokenRequestDto(
    @field:NotBlank val refreshToken: String
)

data class SwitchDomainRequestDto(
    @field:NotBlank val domainCode: String
)
```

### Key Conventions

| Convention | Pattern | Example |
|-----------|---------|---------|
| Required field | `@field:NotBlank` | `val username: String` |
| Optional nullable | `val x: String? = null` | `val captchaToken: String? = null` |
| Optional with default | `val x: String = "default"` | `val domainCode: String = "default"` |
| Email validation | `@field:Email` | `val email: String` |
| Size constraint | `@field:Size(min, max)` | `val password: String` |
| Naming | `{Action}RequestDto` | `LoginRequestDto`, `RegisterRequestDto` |
| Suffix | `Dto` or `RequestDto` | Consistent in project |

---

## 2. Response DTOs

### Location: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`

```kotlin
// Pattern: data class with companion object factory method
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val userId: Long,
    val username: String,
    val activeDomain: String,
    val roles: List<String>,
    val permissions: List<String>
) {
    companion object {
        fun from(token: AuthToken): AuthResponse = AuthResponse(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            tokenType = token.tokenType,
            expiresIn = token.expiresIn,
            userId = token.userId,
            username = token.username,
            activeDomain = token.activeDomain,
            roles = token.roles,
            permissions = token.permissions
        )
    }
}
```

### Key Conventions

| Convention | Pattern | Example |
|-----------|---------|---------|
| Factory method | `companion object { fun from(domain): Dto }` | `AuthResponse.from(authToken)` |
| Token type | `val tokenType: String = "Bearer"` | Always "Bearer" |
| Optional fields | `val x: String? = null` | `refreshToken` (null when cookie-only) |
| Naming | `{Domain}Response` or `{Action}Response` | `AuthResponse` |

---

## 3. Domain Result Types (Sealed Classes)

### Location: `src/main/kotlin/com/ntt/authservice/auth/application/LoginResult.kt`

```kotlin
// Pattern: sealed class for multi-outcome operations
sealed class LoginResult {
    data class Success(val response: AuthResponse) : LoginResult()
    data class MfaRequired(
        val mfaToken: String,
        val method: String,
        val expiresIn: Long
    ) : LoginResult()
}
```

### Key Conventions

| Convention | Pattern |
|-----------|---------|
| Sealed class | `sealed class {Action}Result` |
| Success case | `data class Success(val response: ...)` |
| Alternative case | Named after reason (e.g., `MfaRequired`) |
| Used by handlers | `CommandHandler<Command, LoginResult>` |

---

## 4. Command DTOs (CQRS)

### Location: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt`

```kotlin
// Pattern: data class implementing Command<R>
data class LoginCommand(
    val username: String,
    val password: String,
    val domainCode: String? = null,
    val captchaToken: String? = null,
    val trustedDeviceHash: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val deviceFingerprint: String? = null
) : Command<LoginResult>
```

### Key Conventions

| Convention | Pattern | Example |
|-----------|---------|---------|
| Interface | `Command<R>` from `eventsourcing-utils` | `Command<LoginResult>` |
| Naming | `{Action}Command` | `LoginCommand`, `RegisterCommand` |
| HTTP context fields | `val ipAddress: String? = null` | Added by controller, not by user |
| Immutable | Kotlin `data class` | All fields `val` |

---

## 5. Additional DTO Files

| File | Location | Contents |
|------|----------|----------|
| `MfaDtos.kt` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt` | MFA verification request/response DTOs |
| `SsoDtos.kt` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt` | SSO callback/provider DTOs |
| `TokenDtos.kt` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt` | Token introspection DTOs |

---

## 6. Controller → Command Mapping Pattern

```kotlin
// Pattern in CqrsAuthController:
@PostMapping("/login")
fun login(@Valid @RequestBody request: LoginRequestDto, ...): ResponseEntity<Any> {
    // 1. Map DTO → Command (add HTTP context)
    val command = LoginCommand(
        username = request.username,
        password = request.password,
        domainCode = request.domainCode,
        captchaToken = request.captchaToken,
        ipAddress = extractClientIp(httpRequest),  // Added by controller
        userAgent = httpRequest.getHeader("User-Agent"),
        deviceFingerprint = httpRequest.getHeader("X-Device-Fingerprint")
    )
    // 2. Dispatch to handler
    val result = loginHandler.handle(command)
    // 3. Map result → ResponseEntity
    return when (result) {
        is LoginResult.Success -> ResponseEntity.ok(result.response)
        is LoginResult.MfaRequired -> ResponseEntity.ok(mapOf(...))
    }
}
```

---

## 7. Annotations Used

| Annotation | Package | Purpose |
|-----------|---------|---------|
| `@field:NotBlank` | `jakarta.validation.constraints` | Non-empty string validation |
| `@field:Email` | `jakarta.validation.constraints` | Email format validation |
| `@field:Size(min, max)` | `jakarta.validation.constraints` | Length constraints |
| `@Valid` | `jakarta.validation` | Enable validation on request body |
| `@RequestBody` | `org.springframework.web.bind.annotation` | JSON body binding |
| `@RequestHeader` | `org.springframework.web.bind.annotation` | Header extraction |
