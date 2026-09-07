<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
# Tasks: jwt-security-redesign

> **Type**: EXTEND | **Flow**: Command | **FRs**: 23

## 1. Foundation — Database Migrations

- [x] 1.1 Create Flyway migration `V16__create_mail_queue.sql`
  - File: `src/main/resources/db/migration/V16__create_mail_queue.sql` | Action: [NEW]
  - FR: FR-014

- [x] 1.2 Create Flyway migration `V17__create_mail_templates.sql` with seed data
  - File: `src/main/resources/db/migration/V17__create_mail_templates.sql` | Action: [NEW]
  - FR: FR-015

- [x] 1.3 Create Flyway migration `V18__alter_login_sessions_add_device_fields.sql`
  - File: `src/main/resources/db/migration/V18__alter_login_sessions_add_device_fields.sql` | Action: [NEW]
  - FR: FR-012

- [x] 1.4 Create Flyway migration `V19__alter_refresh_tokens_add_fingerprint.sql`
  - File: `src/main/resources/db/migration/V19__alter_refresh_tokens_add_fingerprint.sql` | Action: [NEW]
  - FR: FR-002

## 2. Foundation — Entities & Repositories

- [x] 2.1 Create `MailQueueEntity`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/MailQueueEntity.kt` | Action: [NEW]

- [x] 2.2 Create `MailTemplateEntity`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/MailTemplateEntity.kt` | Action: [NEW]

- [x] 2.3 Create `MailStatus` enum
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/MailStatus.kt` | Action: [NEW]

- [x] 2.4 Create `MailQueueRepository`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/repository/MailQueueRepository.kt` | Action: [NEW]

- [x] 2.5 Create `MailTemplateRepository`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/repository/MailTemplateRepository.kt` | Action: [NEW]

- [x] 2.6 Modify `LoginSessionEntity` — add fields
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/LoginSessionEntity.kt` | Action: [MODIFY]

## 3. Foundation — Configuration

- [x] 3.1 Add `FingerprintProperties` to `SecurityProperties`
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt` | Action: [MODIFY]

- [x] 3.2 Add `MailProperties` to `SecurityProperties`
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt` | Action: [MODIFY]

## 4. Core — FingerprintService

- [x] 4.1 Create `FingerprintService`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/FingerprintService.kt` | Action: [NEW]

## 5. Core — Mail System

- [x] 5.1 Create `MailQueueService`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/MailQueueService.kt` | Action: [NEW]

- [x] 5.2 Create `MailJobScheduler`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/MailJobScheduler.kt` | Action: [NEW]

- [x] 5.3 Create `NewDeviceMailHandler`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/NewDeviceMailHandler.kt` | Action: [NEW]

## 6. Core — Auth Enhancement

- [x] 6.1 Modify `JwtService` — add fingerprint claim
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` | Action: [MODIFY]

- [x] 6.2 Modify `JwtAuthFilter` — add fingerprint validation step
  - File: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` | Action: [MODIFY]

- [x] 6.3 Modify `LoginSessionService` — add device management methods
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt` | Action: [MODIFY]

- [x] 6.4 Modify `LoginHandler` — integrate fingerprint
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` | Action: [MODIFY]

- [x] 6.5 Modify `RefreshTokenHandler` — blacklist old JTI
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt` | Action: [MODIFY]

## 7. API Layer

- [x] 7.1 Create `DeviceController`
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/DeviceController.kt` | Action: [NEW]

- [x] 7.2 Create `DeviceResponse` DTO
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/DeviceDtos.kt` | Action: [NEW]

- [x] 7.3 Modify `SecurityConfig` — add device endpoints
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt` | Action: [MODIFY]

- [x] 7.4 Add `AuthErrorCode` entries
  - File: `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt` | Action: [MODIFY]

## 8. Build Configuration

- [x] 8.1 Add `spring-boot-starter-mail` dependency
  - File: `build.gradle.kts` | Action: [MODIFY]

- [x] 8.2 Add mail configuration to `application-security.yml`
  - File: `src/main/resources/application-security.yml` | Action: [MODIFY]

## 9. Tests

- [x] 9.1 Unit test `FingerprintService`
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/FingerprintServiceTest.kt` | Action: [NEW]

- [x] 9.2 Unit test `MailQueueService`
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/MailQueueServiceTest.kt` | Action: [NEW]

- [x] 9.3 Unit test `MailJobScheduler`
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/MailJobSchedulerTest.kt` | Action: [NEW]

- [x] 9.4 Unit test `DeviceController` (standalone)
  - File: `src/test/kotlin/com/ntt/authservice/auth/adapter/in/web/DeviceControllerTest.kt` | Action: [NEW]

- [x] 9.5 Unit test `LoginSessionService` device management
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/LoginSessionServiceDeviceTest.kt` | Action: [NEW]

- [x] 9.6 Unit test `NewDeviceMailHandler`
  - File: `src/test/kotlin/com/ntt/authservice/auth/application/NewDeviceMailHandlerTest.kt` | Action: [NEW]

## Extra Tasks (added during implementation)

- [x] E.1 Fix `MailQueueEntity.createdBy` — remove duplicate field (inherited from SnowflakePersistentAuditableEntity)
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/MailQueueEntity.kt` | Action: [MODIFY] | Lý do: Compilation error — `createdBy` hides supertype member

- [x] E.2 Fix `MailTemplateEntity.active` — remove duplicate field (inherited from PersistentAuditableEntity)
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/MailTemplateEntity.kt` | Action: [MODIFY] | Lý do: Compilation error — `active` hides supertype member

- [x] E.3 Fix `MailQueueService.createdBy` type mismatch — superclass uses String? not Long?
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/MailQueueService.kt` | Action: [MODIFY] | Lý do: Type mismatch

- [x] E.4 Add `TokenIssuanceMetadata.deviceFingerprint` field
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/model/TokenIssuanceMetadata.kt` | Action: [MODIFY]

- [x] E.5 Modify `TokenGenerator` — pass deviceFingerprint to JwtService
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt` | Action: [MODIFY]

- [x] E.6 Enhance `NewDeviceLoginEvent` — add username, email, deviceName, loginAt fields
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/event/NewDeviceLoginEvent.kt` | Action: [MODIFY]

- [x] E.7 Add `FINGERPRINT_MISMATCH` to `ValidationFailureReason`
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/ValidationFailureReason.kt` | Action: [MODIFY]
