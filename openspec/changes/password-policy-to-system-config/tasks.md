<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Non-Financial", factory: "N/A", feature_type: "MAINTENANCE", transaction_flow: "" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

## 1. Database Migrations

- [x] **1.1 Seed system_configs table**
  - File: `services/system-admin-service/src/main/resources/db/migration/V10__seed_auth_login_configs.sql` | Action: [NEW]
  - FR: FR-008 - Khởi tạo giá trị mặc định cho cấu hình AUTH_LOGIN

- [x] **1.2 Drop password_policies table**
  - File: `services/auth-service/src/main/resources/db/migration/V26__drop_password_policies_table.sql` | Action: [NEW]
  - FR: FR-006 - Khử bỏ bảng database password_policies

## 2. system-admin-service Updates

- [x] **2.1 Add ConfigEventPublisher**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/config/application/ConfigEventPublisher.kt` | Action: [NEW]
  - Base: `KafkaTemplate`
  - FR: FR-003 - Phát sự kiện thay đổi cấu hình đa kênh
  - Dependencies: `KafkaTemplate`

- [x] **2.2 Add SystemConfigSyncService**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/config/application/SystemConfigSyncService.kt` | Action: [NEW]
  - Base: `StringRedisTemplate`
  - FR: FR-002 - Đồng bộ cấu hình sang Redis Hash
  - Dependencies: `StringRedisTemplate`

- [x] **2.3 Modify DomainConfigService**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/config/application/DomainConfigService.kt` | Action: [MODIFY]
  - FR: FR-001 - Quản trị cấu hình Password Policy tập trung
  - Dependencies: `SystemConfigSyncService`, `ConfigEventPublisher`

## 3. auth-service Updates

- [x] **3.1 Add PasswordPolicyConfig POJO**
  - File: `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyConfig.kt` | Action: [NEW]
  - FR: FR-007 - Tái cấu trúc PasswordPolicyService

- [x] **3.2 Add PasswordPolicyConfigProvider**
  - File: `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyConfigProvider.kt` | Action: [NEW]
  - FR: FR-005 - Truy xuất chính sách theo kiến trúc 4 tầng
  - Dependencies: `StringRedisTemplate`, HTTP/gRPC Client, `SecurityProperties`

- [x] **3.3 Add SystemConfigEventListener**
  - File: `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/event/SystemConfigEventListener.kt` | Action: [NEW]
  - FR: FR-004 - Invalidate bộ nhớ đệm L1 khi nhận sự kiện
  - Dependencies: `@KafkaListener`, `PasswordPolicyConfigProvider`

- [x] **3.4 Clean up Legacy Entity & Repository**
  - File: `services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/PasswordPolicyEntity.kt` | Action: [DELETE]
  - File: `services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt` | Action: [MODIFY] (Remove PasswordPolicyRepository)
  - FR: FR-006 - Khử bỏ bảng database password_policies

- [x] **3.5 Refactor PasswordPolicyService**
  - File: `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt` | Action: [MODIFY]
  - FR: FR-007 - Tái cấu trúc PasswordPolicyService
  - Dependencies: `PasswordPolicyConfigProvider`
