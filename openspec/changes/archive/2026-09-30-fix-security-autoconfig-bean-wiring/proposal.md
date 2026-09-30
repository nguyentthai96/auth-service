# Proposal: fix-security-autoconfig-bean-wiring

## Why

auth-service không thể khởi động do chuỗi lỗi bean wiring trong `base-security-starter`:

1. **`DynamicAuthorizationManager` bean not found**: `SecurityAutoConfiguration.DynamicAuthorizationJpaConfiguration` (nested `@Configuration` class) sử dụng `@ConditionalOnBean(name = ["entityManagerFactory"])` — condition này bị evaluate **cùng phase** với outer `@AutoConfiguration`, dẫn đến bean chưa được registered tại thời điểm đánh giá.

2. **`I18nMessageRepository` not found** (cascading): Sau khi fix lỗi 1, `@EnableJpaRepositories(basePackages = ["com.ntt.basecore..."])` trong `DynamicAuthorizationJpaConfiguration` **override** Spring Boot auto-scan → chỉ scan base-core packages → auth-service repos bị bỏ qua.

3. **`I18nMessageEntity` not a managed type** (cascading): `@EntityScan(basePackages = ["com.ntt.basecore..."])` cũng **override** entity auto-scan → auth-service entities không được Hibernate quản lý.

Đây là **3 lỗi liên tiếp** cùng root cause: nested `@Configuration` trong `@AutoConfiguration` không tương thích tốt với Spring Boot condition evaluation + scan override behavior.

## What Changes

- **MODIFY**: `SecurityAutoConfiguration.kt` (base-core) — bỏ `@ConditionalOnBean`, `@EnableJpaRepositories`, `@EntityScan` từ nested class + thêm `afterName` cho `HibernateJpaAutoConfiguration`
- **MODIFY**: `AuthServiceApplication.kt` (auth-service) — thêm `@EntityScan` + `@EnableJpaRepositories` include cả base-core và auth-service packages

## Capabilities

### Modified Capabilities

- Không thay đổi business logic hoặc security behavior
- `DynamicAuthorizationManager` hoạt động chính xác như trước — chỉ sửa cách Spring Boot tạo bean

## Impact

- **Components Affected**:
  - `components/base-core/starters/base-security-starter`: 1 file sửa (`SecurityAutoConfiguration.kt`)
- **Services Affected**:
  - `services/auth-service`: 1 file sửa (`AuthServiceApplication.kt`)
  - Các consumer khác của `base-security-starter`: cần thêm `@EntityScan` + `@EnableJpaRepositories` nếu chưa có
- **Breaking Changes**: Consumer services cần explicitly declare JPA scan packages (không còn auto-scan từ base-core)
- **Dependencies**: Không thêm dependency mới
