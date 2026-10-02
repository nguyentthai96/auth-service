# Service Structure

_Generated: 2026-10-02_

## auth-service

### Detected Packages

- `com.ntt.authservice.auth.application`: Chứa các use case và services nghiệp vụ xác thực (`PasswordPolicyService.kt`, `TokenBlacklistCacheService.kt`, v.v.)
- `com.ntt.authservice.auth.application.command`: Chứa các Command Handlers theo mô hình CQRS (`LoginHandler.kt`, `RegisterHandler.kt`)
- `com.ntt.authservice.auth.adapter.in.web`: Chứa REST controllers (`AuthController.kt`, `CqrsAuthController.kt`)
- `com.ntt.authservice.auth.adapter.out.cache`: Chứa các adapter tương tác với Redis cache (`RedisLockoutAdapter.kt`)
- `com.ntt.authservice.auth.adapter.out.event`: Chứa event publishing logic (`KafkaEventPublisher.kt`, `OutboxPoller.kt`)
- `com.ntt.authservice.rbac.adapter.out.persistence.entity`: Chứa JPA entities (`PasswordPolicyEntity.kt`, `PasswordHistoryEntity.kt`)
- `com.ntt.authservice.rbac.adapter.out.persistence.repository`: Chứa Spring Data JPA repositories (`Repositories.kt`)
- `com.ntt.authservice.shared.config`: Chứa Spring configuration properties (`SecurityProperties.kt`, `RedisConfig.kt`)
- `com.ntt.authservice.shared.exception`: Chứa exception hierarchy và error codes (`AuthCoreExceptions.kt`, `AuthErrorCode.kt`)

### Not Found

- `factory/`: Kiến trúc Clean Architecture / Hexagonal không dùng pattern abstract factory

### Naming Convention

- Entity: `*Entity.kt` (ví dụ `PasswordPolicyEntity.kt`)
- Repository: `*Repository.kt` (ví dụ `PasswordPolicyRepository`)
- Service: `*Service.kt` (ví dụ `PasswordPolicyService.kt`)
- Handler: `*Handler.kt` (ví dụ `LoginHandler.kt`)
- Controller: `*Controller.kt` (ví dụ `AuthController.kt`)

---

## system-admin-service

### Detected Packages

- `com.ntt.sysadmin.versioning.domain.entity`: Chứa `SystemConfigEntity.kt`
- `com.ntt.sysadminservice.config.application`: Chứa `DomainConfigService.kt`
- `com.ntt.sysadminservice.apipartner.application`: Chứa `ApiPartnerService.kt`
- `com.ntt.sysadminservice.apipartner.adapter.in.web`: Chứa `ApiUsageController.kt`

### Naming Convention

- Entity: `*Entity.kt` (ví dụ `SystemConfigEntity.kt`)
- Service: `*Service.kt` (ví dụ `DomainConfigService.kt`)
