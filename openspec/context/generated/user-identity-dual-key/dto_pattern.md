# DTO Pattern — user-identity-dual-key

## Existing Domain Models

### User (Domain Model)
- **File**: [`User.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/model/User.kt)
- **Fields**: `id: UserId`, `username: String`, `email: Email`, `passwordHash: PasswordHash`, `fullName: String`, `phone: String?`, `avatarUrl: String?`, `status: UserStatus`, `failedLoginCount: Int`, `lockedUntilAt: Instant?`, `mfaEnabled: Boolean`, `mfaMethod: String`, `trustedDeviceHash: String?`, `trustedDeviceSetAt: Instant?`, `passwordChangedAt: Instant?`, `createdAt: Instant?`, `updatedAt: Instant?`
- **Change**: Add `publicId: UUID` field

### UserId (Value Object)
- **File**: `auth/domain/model/vo/UserId.kt`
- **Type**: `data class UserId(val value: Long)`
- **Change**: No change — internal ID remains Long

## Existing Mappers

### UserPersistenceAdapter.toDomain()
- **File**: [`UserPersistenceAdapter.kt:66`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/UserPersistenceAdapter.kt#L66)
- **Pattern**: Extension function `UserEntity.toDomain(): User`
- **Change**: Map `this.uuid` → `publicId`

### UserEntityMapper
- **File**: [`UserEntityMapper.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/mapper/UserEntityMapper.kt)
- **Pattern**: Top-level extension functions `UserEntity.toDomain()` and `User.toEntity()`
- **Change**: Map `uuid` ↔ `publicId` in both directions

## Naming Convention
- Request DTO: `<Entity><Action>Request`
- Response DTO: `<Entity><Action>Response`
- Domain model: Plain name (`User`, not `UserDTO`)
- Value Object: Semantic name (`UserId`, `Email`, `PasswordHash`)

## Annotations
- `@Valid` on controller method parameters
- `@NotNull`, `@NotBlank`, `@Size` on DTO fields
- `@Column` on entity fields
