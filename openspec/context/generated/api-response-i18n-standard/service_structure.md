# Service Structure

_Generated: 2026-08-11_

## base-core

### Detected Packages

- `com.ntt.basecore.configuration`: Auto-configuration (Servlet, Reactive)
- `com.ntt.basecore.domain.web`: `BaseController`, `BaseControllerAdvice`
- `com.ntt.basecore.domain.web.payload`: `ApiResponse<T>`
- `com.ntt.basecore.exception`: `BusinessException`, `NotFoundException`
- `com.ntt.basecore.exception.base`: `ErrorCodeBase`, `ErrorService`
- `com.ntt.basecore.exception.error`: `ApiErrorCode`

### Not Found (relevant to this feature)

- `com.ntt.basecore.i18n` — NOT FOUND (needs to be created)
- `messages.properties` — NOT FOUND (needs to be created)

### Naming Convention

- Config classes: `*AutoConfiguration`, `*Config`
- Payload classes: `ApiResponse`
- Exception base: `ErrorCodeBase`, `ErrorService` interface

## auth-service

### Detected Packages

- `com.ntt.authservice.auth.adapter.in.web`: Controllers (`CqrsAuthController`)
- `com.ntt.authservice.auth.adapter.in.web.dto`: Request/Response DTOs
- `com.ntt.authservice.auth.application.command`: CQRS command handlers
- `com.ntt.authservice.auth.application.query`: CQRS query handlers
- `com.ntt.authservice.shared.exception`: `AuthControllerAdvice`, `AuthException`, `AuthErrorCode`
- `com.ntt.authservice.shared.config`: `SecurityConfig`, `SecurityProperties`

### Not Found (relevant to this feature)

- `messages.properties` — NOT FOUND (needs to be created)
- `messages_vi.properties` — NOT FOUND (needs to be created)

### Naming Convention

- Controllers: `Cqrs*Controller`
- Handlers: `*Handler`
- DTOs: `*RequestDto`, `*Response`
- Exceptions: `*Exception`
- Error codes: `AuthErrorCode` enum

## admindashboard (frontend)

### Detected Packages

- `src/utils/`: `api.ts` (ky HTTP client)
- `src/@i18n/`: `i18n.ts`, `I18nProvider.tsx`
- `src/@auth/services/jwt/`: `authService.ts`, `JwtAuthProvider.tsx`, `JwtSignInForm.tsx`
- `src/app/(public)/(auth)/components/forms/`: `SignInPageForm.tsx`
