# Error Pattern — user-identity-dual-key

## Exception Hierarchy

```
AuthException (open class — base for all auth errors)
├── ResourceNotFoundException
├── DuplicateResourceException
├── PermissionDeniedException
├── WriteNotAllowedException
├── AccountLockedException
├── AccountLockedPermanentException
├── InvalidCredentialsException
├── TokenExpiredException
├── PolicyEvaluationException
├── EventStorePersistException
├── EventNotFoundException
├── ServiceTokenInvalidException
├── ServiceInsufficientScopeException
├── ServiceNotRegisteredException
├── UnlockTokenExpiredException
├── UnlockTokenUsedException
└── UnlockNotAllowedException
```

## Error Code Pattern
- **File**: [`AuthErrorCode.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt)
- **Type**: `enum class AuthErrorCode` with code + i18n message key

## Exceptions Relevant to This Feature

| Exception | When | File |
|-----------|------|------|
| `ResourceNotFoundException` | User not found by identifier | [`AuthExceptions.kt:17`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt#L17) |
| `DuplicateResourceException` | Username/email/phone already exists | [`AuthExceptions.kt:26`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt#L26) |
| `InvalidCredentialsException` | Wrong password after identity resolved | [`AuthExceptions.kt:61`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt#L61) |
| `AccountLockedException` | Account locked after failed attempts | [`AuthExceptions.kt:52`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt#L52) |

## Change Impact
No new exception classes needed for this feature. Existing `ResourceNotFoundException` covers the "user not found" case in Identity Resolver.
