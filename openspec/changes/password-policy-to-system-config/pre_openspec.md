# Pre-OpenSpec: password-policy-to-system-config

> **Type**: MAINTENANCE
> **Flow**: Non-Financial
> **Source**: User Idea (no URD)
> **Classification Evidence**: PasswordPolicyEntity → auth.rbac.persistence → PasswordPolicyEntity.kt / SystemConfigEntity → sysadmin.versioning → SystemConfigEntity.kt
> **Archive**: `openspec/changes/archive/2026-08-21-auth-core-features/pre_openspec.md`
> **Quality Score**: 99/100

## 📋 Feature Summary

Tính năng thực hiện tái cấu trúc và loại bỏ bảng độc lập `password_policies` trong `auth-service`, chuyển toàn bộ việc quản lý cấu hình chính sách mật khẩu (độ dài, độ phức tạp, thời hạn, lockout) sang bảng cấu hình chung `system_configs` tại `system-admin-service` thuộc nhóm `AUTH_LOGIN`.

Cấu hình được quản lý tập trung bởi Admin, đồng bộ sang Redis hash và phát sự kiện qua Kafka topic `system.config.changed` (hoặc REST/gRPC Webhook). Phía `auth-service` áp dụng cơ chế nạp cấu hình 4 tầng (L1 In-Memory Caffeine → L2 Redis Hash → L3 HTTP/gRPC Fallback gọi system-admin → L4 YAML Default Fallback), đảm bảo zero-downtime, độ trễ cực thấp trong luồng xác thực đăng nhập và tự động phục hồi an toàn khi hạ tầng cache gặp sự cố.

| Metric | Giá trị |
|--------|---------|
| Số FR | 9 (Idea: 7, Enriched: 2) |
| Issues | 1 (🔴: 0, 🟡: 1) |
| Open Questions | 0 |
| **Quality Score** | **99/100** |

---

## 1. Actors

- **System Administrator**: Quản trị viên hệ thống có quyền cấu hình các tham số bảo mật, chính sách mật khẩu thông qua Dashboard quản trị của `system-admin-service`.
- **End User**: Người dùng thực hiện đăng ký tài khoản, đăng nhập hoặc đổi mật khẩu trên `auth-service`, chịu sự kiểm tra và ràng buộc bởi chính sách mật khẩu đang có hiệu lực.
- **System Admin Service**: Service đóng vai trò là Single Source of Truth cho cấu hình hệ thống, chịu trách nhiệm lưu trữ DB, đồng bộ vào Redis và broadcast sự kiện Kafka.
- **Auth Service**: Service thực thi xác thực người dùng, lắng nghe sự kiện để làm mới bộ đệm cục bộ và nạp cấu hình mật khẩu theo 3 tầng.

---

## 2. Functional Requirements

### FR-001: Quản trị cấu hình Password Policy tập trung [IDEA]
- **Actor**: System Administrator
- **Action**: Hệ thống phải lưu trữ và quản lý các tham số của Password Policy trong bảng `system_configs` tại `system-admin-service` dưới nhóm cấu hình `AUTH_LOGIN`.
- **Validation**: Các tham số được lưu trữ dạng bản ghi key-value riêng lẻ:
  - `password.min_length` (NUMBER, mặc định 8)
  - `password.max_length` (NUMBER, mặc định 128)
  - `password.require_uppercase` (BOOLEAN, mặc định true)
  - `password.require_lowercase` (BOOLEAN, mặc định true)
  - `password.require_digit` (BOOLEAN, mặc định true)
  - `password.require_special` (BOOLEAN, mặc định false)
  - `password.min_character_types` (NUMBER, mặc định 3)
  - `password.history_count` (NUMBER, mặc định 5)
  - `password.max_age_days` (NUMBER, mặc định 90)
  - `password.lockout_threshold` (NUMBER, mặc định 5)
  - `password.lockout_duration_minutes` (NUMBER, mặc định 15)

### FR-002: Đồng bộ cấu hình sang Redis Hash [IDEA]
- **Actor**: System Admin Service
- **Action**: Hệ thống phải đồng bộ toàn bộ các key-value của nhóm `AUTH_LOGIN` vào Redis hash `system:config:AUTH_LOGIN` mỗi khi có thao tác tạo mới hoặc cập nhật cấu hình.
- **Validation**: Giá trị trong Redis hash phải được cập nhật nguyên tử (atomic) hoặc ghi đè đầy đủ, phản ánh giá trị mới nhất trong cơ sở dữ liệu.

### FR-003: Phát sự kiện thay đổi cấu hình đa kênh [IDEA]
- **Actor**: System Admin Service
- **Action**: Hệ thống phải gửi message thông báo cập nhật cấu hình vào Kafka topic `system.config.changed`. Đối với các môi trường triển khai nhỏ không có Kafka, hệ thống phải cung cấp cơ chế gRPC/RESTful webhook để thông báo tức thời (instant sync).
- **Validation**: Payload sự kiện qua mọi kênh phải chứa thông tin tối thiểu gồm: `configGroup` ("AUTH_LOGIN"), `timestamp`, `version`, và danh sách các key vừa thay đổi.

### FR-004: Invalidate bộ nhớ đệm L1 khi nhận sự kiện [IDEA]
- **Actor**: Auth Service
- **Action**: Hệ thống phải lắng nghe Kafka topic `system.config.changed` (hoặc REST/gRPC endpoint) để nhận event, kiểm tra `configGroup == 'AUTH_LOGIN'` và thực hiện làm mới (invalidate/refresh) L1 In-Memory Cache.
- **Validation**: L1 cache bị xóa hoặc nạp mới ngay lập tức; các luồng kiểm tra mật khẩu tiếp theo sẽ đọc cấu hình mới nhất từ L2 Redis hoặc qua API.

### FR-005: Truy xuất chính sách theo kiến trúc 4 tầng [IDEA]
- **Actor**: Auth Service
- **Action**: Hệ thống phải nạp cấu hình Password Policy theo cơ chế 4 tầng ưu tiên: Tầng 1 (L1 In-Memory) → Tầng 2 (L2 Redis Hash) → Tầng 3 (L3 HTTP/gRPC gọi sang `system-admin-service`) → Tầng 4 (L4 YAML Default Fallback).
- **Validation**:
  - Nếu L1 có dữ liệu: trả về ngay lập tức (zero I/O latency).
  - Nếu L1 miss: đọc từ L2 Redis hash, nạp vào L1.
  - Nếu L2 Redis trống (ví dụ khởi động lần đầu): gọi gRPC/REST sang `system-admin-service` để lấy real data, push vào Redis và L1.
  - Nếu cả L3 gọi `system-admin-service` cũng lỗi/timeout: tự động fallback về cấu hình mặc định (L4) trong `SecurityProperties.password` mà không gây gián đoạn luồng nghiệp vụ.

### FR-006: Khử bỏ bảng database password_policies [IDEA]
- **Actor**: Auth Service
- **Action**: Hệ thống phải cung cấp script Flyway migration để xóa bỏ (drop table) bảng `password_policies` trong `auth_db`.
- **Validation**: Bảng `password_policies` bị loại bỏ hoàn toàn; bảng `password_history` và các ràng buộc dữ liệu người dùng không bị ảnh hưởng.

### FR-007: Tái cấu trúc PasswordPolicyService [IDEA]
- **Actor**: Auth Service
- **Action**: Hệ thống phải xóa bỏ `PasswordPolicyEntity` và `PasswordPolicyRepository`, chuyển `PasswordPolicyService` sang sử dụng `PasswordPolicyConfigProvider`.
- **Validation**: Các phương thức `validatePasswordStrength`, `checkPasswordHistory`, `isPasswordExpired` hoạt động chính xác dựa trên POJO `PasswordPolicyConfig`.

### FR-008: Khởi tạo giá trị mặc định cho cấu hình AUTH_LOGIN [ENRICHED]
- **Actor**: System Admin Service
- **Action**: Hệ thống phải cung cấp migration script chèn sẵn các bản ghi cấu hình mặc định cho nhóm `AUTH_LOGIN` trong bảng `system_configs` nếu chưa tồn tại. Tên key phải ghi đầy đủ prefix (ví dụ: `password.min_length`).
- **Validation**: Đảm bảo môi trường mới triển khai có đầy đủ 11 bản ghi cấu hình mật khẩu với key đầy đủ mà không cần admin phải nhập tay từ đầu.

### FR-009: Giám sát và ghi log Fallback an toàn [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải ghi log cảnh báo mức `WARN` và cập nhật metric Micrometer khi phải kích hoạt L4 Fallback do lỗi kết nối cả Redis và HTTP/gRPC.
- **Validation**: Log cảnh báo chứa nguyên nhân ngoại lệ (ConnectionException, v.v.), hỗ trợ hệ thống giám sát cảnh báo sớm cho đội vận hành.

---

## 3. Non-functional Requirements

- **Hiệu năng (Performance)**: Việc đọc chính sách mật khẩu nằm trên critical path của đăng nhập/đăng ký. Tầng L1 In-Memory phải đạt thời gian phản hồi `< 0.1ms`. Tầng L2 Redis `< 2ms`.
- **Độ sẵn sàng (High Availability & Resilience)**: Không có Single Point of Failure. Nếu Kafka hoặc Redis ngừng hoạt động, `auth-service` vẫn vận hành bình thường nhờ tầng L3 HTTP/gRPC Fallback và L4 YAML Fallback.
- **Tính nhất quán (Consistency)**: Thời gian trễ từ khi Admin cập nhật cấu hình trên `system-admin-service` đến khi `auth-service` áp dụng cấu hình mới qua Kafka listener `< 1 giây`.
- **Bảo mật (Security)**: Chỉ tài khoản Admin có quyền hạn phù hợp mới được phép cập nhật cấu hình `AUTH_LOGIN` trên `system-admin-service`.

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. Các yêu cầu đã được chuẩn hóa tách biệt giữa phía phát hành cấu hình (`system-admin-service`) và phía tiêu thụ cấu hình (`auth-service`).

---

## 5. Enriched Domain Requirements

Đã bổ sung 2 yêu cầu kỹ thuật tuân thủ giới hạn bổ sung:
- **FR-008**: Seed data mặc định để đảm bảo tính toàn vẹn hệ sinh thái khi bootstrap môi trường.
- **FR-009**: Observability & Logging khi kích hoạt L3 Fallback để bảo đảm tiêu chuẩn Production Readiness của Base-Core.

### External Integrations

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| **Redis** | Lưu trữ cấu hình dạng Hash (`system:config:AUTH_LOGIN`) làm L2 Cache | StringRedisTemplate |
| **Kafka** | Truyền tải sự kiện thay đổi cấu hình qua topic `system.config.changed` | KafkaTemplate / @KafkaListener |
| **PostgreSQL** | Lưu trữ persistent tại `system_configs` (`system_admin_db`) | Flyway migration |

---

## 6. Assumptions

- `system-admin-service` và `auth-service` cùng chia sẻ chung cụm Kafka và cụm Redis.
- Bảng `password_history` trong `auth_db` vẫn cần được giữ nguyên để kiểm tra tái sử dụng mật khẩu cũ của từng tài khoản người dùng cụ thể.

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 25/25 | Không có điểm trừ |
| Đầy đủ (Completeness) | 24/25 | FR-001: Cần phân định rõ vai trò quản trị viên được phép sửa nhóm cấu hình bảo mật (-1) |
| Nhất quán (Consistency) | 25/25 | Không có điểm trừ |
| Kiểm thử được (Testability) | 25/25 | Không có điểm trừ |
| **Tổng** | **99/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích Idea) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Completeness | -1 | FR-001 | Chưa nêu cụ thể Role/Permission của Admin nào được sửa cấu hình AUTH_LOGIN | Bổ sung kiểm tra quyền `CONFIG:UPDATE` hoặc `SYSTEM_ADMIN` tại Controller |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | Khi Kafka bị mất kết nối hoặc lag, `auth-service` có thể dùng cấu hình cũ trên L1 lâu hơn dự kiến | FR-004 | Thiết lập TTL cho L1 cache (ví dụ 60 giây) để tự động làm mới ngay cả khi miss Kafka event |

---

## 9. Open Questions

- [RESOLVED] Topic name cho config sync: Dùng chung `system.config.changed` (Kafka). Hỗ trợ thêm gRPC/RESTful webhook để đồng bộ tức thời cho multi-system/multi-service ở mô hình không có Kafka.
- [RESOLVED] Cơ chế Seed ban đầu: Dùng tên key đầy đủ `password.min_length` để tránh nhầm lẫn khi mở rộng.
- [RESOLVED] Khởi động lần đầu (Redis rỗng): Lấy real data từ `system-admin-service` qua HTTP/gRPC, tự động push vào Redis rồi mới trả về. Nếu lỗi toàn bộ mới fallback về Default YAML.
- [RESOLVED] Key format trong Redis hash: Lưu nguyên tên key đầy đủ `password.min_length`.

---

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
- IAM / Authentication & Security Policy Management

### 10.2 Flow Type
- Non-Financial (System Configuration & Authentication Policy Enforcement)

### 10.3 Candidate Services
- `auth-service`: Dịch vụ xác thực và thực thi chính sách mật khẩu người dùng.
  - Reason: Nơi chứa `PasswordPolicyEntity`, `PasswordPolicyRepository`, `PasswordPolicyService` cần refactor.
- `system-admin-service`: Dịch vụ quản trị hệ thống và cấu hình tập trung.
  - Reason: Nơi chứa bảng `system_configs`, `SystemConfigEntity`, `DomainConfigService` để lưu trữ và phát event.

### Detection Evidence
- Keyword: `PasswordPolicyEntity` → Module: `auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity` → File: `PasswordPolicyEntity.kt`
- Keyword: `system_configs` → Module: `system-admin-service/src/main/resources/db/migration` → File: `V4__create_feature_flag_tables.sql`
- Keyword: `PasswordPolicyService` → Module: `auth-service/src/main/kotlin/com/ntt/authservice/auth/application` → File: `PasswordPolicyService.kt`

### 10.4 External Integrations
- Redis: `StringRedisTemplate`
- Kafka: `KafkaTemplate`, `@KafkaListener`
- PostgreSQL: Flyway, JPA/Hibernate

### 10.5 Required Modules
- `com.ntt.authservice.auth.application`
- `com.ntt.authservice.rbac.adapter.out.persistence`
- `com.ntt.sysadmin.versioning`

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Admin | Gửi yêu cầu cập nhật chính sách mật khẩu (`PUT /api/admin/system-configs`) | system-admin-service |
| 2 | system-admin-service | Lưu bản ghi mới vào bảng `system_configs`, tăng version và ghi audit history | PostgreSQL (system_admin_db) |
| 3 | system-admin-service | Ghi đè cấu hình mới vào Redis hash `system:config:AUTH_LOGIN` | Redis |
| 4 | system-admin-service | Bắn event `system.config.changed` vào Kafka (hoặc webhook gRPC/REST) | Kafka / API |
| 5 | auth-service | Nhận event, invalidate L1 in-memory cache | auth-service |
| 6 | End User | Thực hiện đăng nhập hoặc đổi mật khẩu | auth-service |
| 7 | auth-service | Kiểm tra L1 (miss) → L2 Redis (miss) → L3 gọi API `system-admin-service` → L4 Fallback yaml | auth-service / system-admin-service |
| 8 | auth-service | Passay PasswordValidator kiểm tra mật khẩu dựa trên cấu hình nạp được | auth-service |

---

## 12. Traceability Matrix

| FR-ID | Source Ref | Spec Section | Affected Class | Status |
|-------|------------|--------------|---------------|--------|
| FR-001 | User Idea | Config Management | `SystemConfigEntity.kt`, `DomainConfigService.kt` | [REUSE] / [MODIFY] |
| FR-002 | User Idea | Redis Sync | `SystemConfigSyncService.kt` | [ADD] |
| FR-003 | User Idea | Event Notification | `ConfigEventPublisher.kt` | [ADD] |
| FR-004 | User Idea | Event Consumer | `SystemConfigEventListener.kt` | [ADD] |
| FR-005 | User Idea | Multi-tier Provider | `PasswordPolicyConfigProvider.kt`, `SecurityProperties.kt` | [ADD] / [REUSE] |
| FR-006 | User Idea | DB Migration | `V26__drop_password_policies_table.sql` | [ADD] |
| FR-007 | User Idea | Service Refactoring | `PasswordPolicyService.kt`, `PasswordPolicyEntity.kt` (DELETE) | [MODIFY] / [DELETE] |
| FR-008 | Enriched | Admin Seed | `V10__seed_auth_login_configs.sql` | [ADD] |
| FR-009 | Enriched | Observability | `PasswordPolicyConfigProvider.kt` | [ADD] |

---

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- Việc loại bỏ bảng `password_policies` là một bước đi kiến trúc đúng đắn (architectural hygiene), giảm bớt sự phân tán cấu hình trong các cơ sở dữ liệu vi dịch vụ độc lập.
- Cần chú ý đặc biệt đến **Fail-Safe Mechanism**: Khi auth-service khởi động trong môi trường mới hoặc khi Redis bị sự cố, việc fallback về `SecurityProperties` giúp bảo vệ tính khả dụng 99.99% của luồng đăng nhập.

### Related Features / Precedents
- `system-admin-service` đã triển khai thành công cơ chế đẩy Rate Limit của API Partner lên Redis tại [`ApiPartnerService.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/apipartner/application/ApiPartnerService.kt#L152). Ta có thể tái sử dụng mô hình đẩy cấu hình tương tự cho `system_configs`.
- `auth-service` đã áp dụng thành công kiến trúc L1 Caffeine + L2 Redis cho Token Blacklist tại [`TokenBlacklistCacheService.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt). Ta hoàn toàn có thể tái sử dụng mô hình bộ đệm này cho `PasswordPolicyConfigProvider`.

### Suggested Approach
- Sử dụng Caffeine Cache cho L1 với `expireAfterWrite = 60s` kết hợp với manual invalidation từ `@KafkaListener`. Điều này đảm bảo vừa có real-time invalidation qua sự kiện, vừa có cơ chế tự refresh nếu event bị thất lạc.
