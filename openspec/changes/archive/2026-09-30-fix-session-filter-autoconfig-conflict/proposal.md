# Proposal: fix-session-filter-autoconfig-conflict

## Why

Khi người dùng/frontend gửi request đăng nhập tới endpoint `POST /auth/login` (hoặc truy cập các public endpoint khác như `/auth/register`, `/actuator/health`), hệ thống trả về lỗi `HTTP 401 Unauthorized` chỉ trong 1ms:
```json
{"error":"Unauthorized","message":"Missing or invalid Authorization header"}
```

Nguyên nhân do starter `base-security-starter` đăng ký servlet filter `DefaultSessionValidationFilter` ở order `-1700` một cách vô điều kiện. Vì thứ tự này chạy trước Spring Security filter chain (`-100`), filter chặn đứng mọi request không kèm Bearer token trên các path không thuộc `publicPaths` mặc định (`["/actuator/health", "/health"]`), vô hiệu hóa hoàn toàn cơ chế `.permitAll()` của `auth-service`.

## What Changes

- **MODIFY** `SecurityAutoConfiguration.kt` (`base-security-starter`): Bổ sung `@ConditionalOnProperty(prefix = "app.security.session-validation", name = ["enabled"], havingValue = "true", matchIfMissing = true)` trên bean `defaultSessionValidationFilter`.
- **MODIFY** `SecurityProperties.kt` (`base-security-starter`): Bổ sung nested data class `SessionValidationProperties(var enabled: Boolean = true)` và property `var sessionValidation: SessionValidationProperties = SessionValidationProperties()`.
- **MODIFY** `application-security.yml` (`auth-service`): Cấu hình `app.security.session-validation.enabled: false` để vô hiệu hóa filter này, đồng thời bổ sung whitelist `app.security.public-paths` cho `auth-service` để phòng thủ chiều sâu.
- **PUBLISH** `base-security-starter` sang Maven local repository để `auth-service` nhận phiên bản mới nhất.

## Capabilities

### New Capabilities
- `session-filter-autoconfig`: Cung cấp cơ chế bật/tắt linh hoạt cho `DefaultSessionValidationFilter` trong `base-security-starter` thông qua thuộc tính `app.security.session-validation.enabled`, bảo toàn hành vi cho Resource Servers và cho phép Identity Provider (`auth-service`) tự quản lý chuỗi lọc bảo mật riêng.

### Modified Capabilities
_Không có_

## Impact

- **Components Affected**:
  - `components/base-core/starters/base-security-starter`: Thêm toggle `@ConditionalOnProperty` và property mapping.
- **Services Affected**:
  - `services/auth-service`: Cập nhật cấu hình YAML để tắt session filter thừa thãi.
- **Downstream Services**:
  - `account-service`, `system-admin-service`, `notification-service`: Không bị ảnh hưởng nhờ `matchIfMissing = true` (Zero breaking changes).
- **APIs**:
  - `POST /auth/login` và các endpoint public trong `auth-service` hoạt động bình thường, không còn bị 401 do filter Servlet cấp thấp.
