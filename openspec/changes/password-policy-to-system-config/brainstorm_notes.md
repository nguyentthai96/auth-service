---
type: brainstorm_notes
change: password-policy-to-system-config
date: 2026-10-02
selected_direction: "Centralized system_configs in system-admin-service with Redis + Kafka sync & L1 Caffeine / L2 Redis caching with YAML fallback"
pre_flow: "Non-Financial"
pre_feature_type: "MAINTENANCE"
status: complete
---

# Brainstorm Notes: Chuyển đổi Password Policies sang Bảng Config Chung (system_configs)

## Ngày thực hiện
2026-10-02

## 1. Bối cảnh & Hiện trạng (Context)
- Hiện tại trong `auth-service`, bảng `password_policies` là một bảng độc lập (single-row table, đã bỏ `domain_id`).
- Bảng này chỉ chứa các quy tắc độ phức tạp mật khẩu, lockout, history count:
  - `min_length`, `max_length`, `require_uppercase`, `require_lowercase`, `require_digit`, `require_special`, `min_character_types`
  - `history_count`, `max_age_days`, `lockout_threshold`, `lockout_duration_minutes`
- Việc duy trì một bảng riêng cho một cấu hình chỉ gồm 1 bản ghi cấu hình tĩnh/bán tĩnh gây phân mảnh, thiếu tính nhất quán với hệ thống quản lý cấu hình chung (`system_configs` bên `system-admin-service`), và làm phức tạp hóa database schema của `auth-service`.
- Người dùng đề xuất: **Không cần bảng riêng `password_policies`**, chuyển toàn bộ sang bảng config chung (`system_configs`) thuộc nhóm (group) `AUTH_LOGIN`.

---

## 2. Các câu hỏi đã thảo luận & Thống nhất (Q&A)

### Q1: Bảng config chung sẽ đặt ở đâu?
- **Lựa chọn**:
  1. Tạo bảng `system_configs` ngay trong `auth_db`.
  2. **[ĐÃ CHỌN]** Sử dụng bảng `system_configs` của `system-admin-service` — quản lý tập trung và đồng bộ sang `auth-service` qua Kafka & Redis.
- **Lý do**:
  - `system-admin-service` được thiết kế làm Single Source of Truth cho toàn bộ cấu hình hệ thống (menu, feature flags, system configs, i18n, audit versioning).
  - Admin thao tác trên Dashboard của `system-admin-service`, thay đổi được audit log và versioning tự động.

### Q2: Cấu trúc lưu trữ dữ liệu Password Policy trong group `AUTH_LOGIN`?
- **Lựa chọn**:
  1. 1 record duy nhất chứa JSON blob (Key: `password_policy`).
  2. **[ĐÃ CHỌN]** Nhiều records dạng key-value riêng lẻ trong Group `AUTH_LOGIN` (hoặc prefix `auth.login.password.*`).
- **Lý do**:
  - Từng tham số (ví dụ `password.min_length`, `password.require_digit`) có thể được view, search, validate type (NUMBER, BOOLEAN) độc lập trên UI admin.
  - Phù hợp với kiến trúc `SystemConfigEntity` hiện tại (`config_key`, `config_value`, `value_type`, `version`).

---

## 3. Các hướng tiếp cận đã xem xét (Approaches Considered)

### Approach 1: Polling / Direct Database Read
- `auth-service` kết nối hoặc gọi API trực tiếp sang `system-admin-service` mỗi khi cần kiểm tra policy.
- **Ưu điểm**: Đơn giản về mặt sync.
- **Nhược điểm**: Tạo điểm nghẽn runtime nghiêm trọng. Login là hot-path, không thể gọi HTTP sync sang service khác mỗi lần user login hay đổi password.

### Approach 2: Full Local Config in YAML (Static)
- Đưa toàn bộ vào `application-security.yml` dưới `app.security.password.*`.
- **Ưu điểm**: Zero latency, không phụ thuộc service ngoài.
- **Nhược điểm**: Mỗi lần đổi policy phải redeploy service, admin không có UI quản trị.

### Approach 3 (Selected): Centralized `system_configs` + Redis Sync + Hybrid Event (Kafka / REST) + Multi-tier Fallback
- `system-admin-service` quản lý DB `system_configs`.
- Khi cập nhật: ghi Redis hash + publish event báo thay đổi.
  - Event mechanism: Kafka (topic `system.config.changed`) hoặc gRPC/REST webhook cho các mô hình nhỏ không dùng Kafka, đảm bảo sync tức thời multi-service.
- `auth-service`:
  - L1: In-memory cache (Caffeine / AtomicReference với TTL ngắn hoặc invalidate qua event).
  - L2: Redis (đọc nhanh từ Redis hash `system:config:AUTH_LOGIN`).
  - L3: HTTP/gRPC Fallback (gọi API trực tiếp `system-admin-service` lấy real data và push nạp lại vào Redis nếu Redis trống/miss).
  - L4: Local Default Fallback (`SecurityProperties.PasswordProperties` trong YAML) an toàn cuối cùng.

---

## 4. Thiết kế kiến trúc giải pháp (Architecture Design)

### 4.1 Mô hình luồng dữ liệu (Data Flow)

```
      ADMIN UI (Dashboard)
               │
               ▼ [PUT /api/admin/configs]
   ┌────────────────────────────────────────┐
   │         system-admin-service           │
   │  ┌──────────────────────────────────┐  │
   │  │ system_configs (PostgreSQL)      │  │
   │  │ group: AUTH_LOGIN                │  │
   │  │ key: password.min_length         │  │
   │  └──────────────────────────────────┘  │
   │                   │                    │
   │         ┌─────────┴─────────┐          │
   │         ▼                   ▼          │
   │   [Write to Redis]    [Publish Event]  │
   │   key: system:config: Kafka / gRPC /   │
   │        AUTH_LOGIN     REST Webhook     │
   └─────────┬───────────────────┬──────────┘
             │                   │
             │                   │
             ▼                   ▼
     ┌───────────────┐   ┌─────────────────────────────┐
     │  Redis Cache  │   │  Kafka Broker / API Server  │
     └───────┬───────┘   └──────────────┬──────────────┘
             │                          │
             │                          │ @KafkaListener / HTTP Endpoint
             │                          ▼
   ┌─────────┼─────────────────────────────────────────┐
   │         │               auth-service              │
   │         │      ┌───────────────────────────────┐  │
   │         │      │ ConfigChangeListener          │  │
   │         │      │ (Invalidates L1 memory cache) │  │
   │         │      └───────────────┬───────────────┘  │
   │         │                      │                  │
   │         ▼                      ▼                  │
   │  ┌─────────────────────────────────────────────┐  │
   │  │ PasswordPolicyConfigProvider                │  │
   │  │  1. Check L1 Cache (In-Memory)              │  │
   │  │  2. Miss -> Read L2 (Redis Hash)            │  │
   │  │  3. Miss -> Call L3 (HTTP/gRPC to sys-admin)│  │
   │  │             (Save to Redis & L1)            │  │
   │  │  4. Error -> L4 Fallback (YAML/Props)       │  │
   │  └─────────────────────┬───────────────────────┘  │
   │                        │                          │
   │                        ▼                          │
   │           PasswordPolicyService (Passay)          │
   │          (Validate strength, expiry, history)     │
   └───────────────────────────────────────────────────┘
```

### 4.2 Danh sách Config Keys trong Group `AUTH_LOGIN`

| Config Key | Value Type | Default Value | Mô tả |
|------------|------------|---------------|-------|
| `password.min_length` | `NUMBER` | `8` | Độ dài tối thiểu của mật khẩu |
| `password.max_length` | `NUMBER` | `128` | Độ dài tối đa của mật khẩu |
| `password.require_uppercase` | `BOOLEAN` | `true` | Bắt buộc chữ hoa |
| `password.require_lowercase` | `BOOLEAN` | `true` | Bắt buộc chữ thường |
| `password.require_digit` | `BOOLEAN` | `true` | Bắt buộc chữ số |
| `password.require_special` | `BOOLEAN` | `false` | Bắt buộc ký tự đặc biệt |
| `password.min_character_types` | `NUMBER` | `3` | Số loại ký tự tối thiểu |
| `password.history_count` | `NUMBER` | `5` | Số lượng mật khẩu cũ không được tái sử dụng |
| `password.max_age_days` | `NUMBER` | `90` | Thời hạn hết hạn mật khẩu (ngày, 0 = không hết hạn) |
| `password.lockout_threshold` | `NUMBER` | `5` | Số lần login sai trước khi khóa |
| `password.lockout_duration_minutes` | `NUMBER` | `15` | Thời gian khóa tài khoản (phút) |

### 4.3 Thay đổi trong `auth-service`
1. **Database Migration**:
   - Viết Flyway script drop table `password_policies` (hoặc rename/archive nếu cần backup).
   - Bảng `password_history` vẫn **GIỮ NGUYÊN** vì lưu trữ lịch sử hash mật khẩu của từng user (dữ liệu giao dịch, không phải cấu hình hệ thống).
2. **Loại bỏ Code cũ**:
   - Xóa `PasswordPolicyEntity.kt`.
   - Xóa `PasswordPolicyRepository` trong `Repositories.kt`.
3. **Thêm Component mới**:
   - `PasswordPolicyConfig`: Data class thuần túy (POJO) chứa các giá trị cấu hình.
   - `DynamicConfigProvider` / `PasswordPolicyConfigProvider`:
     - Load key-value từ Redis `system:config:AUTH_LOGIN`.
     - Caching L1 in-memory bằng Caffeine (hoặc `AtomicReference` kèm TTL 60s).
     - Miss Redis -> L3 Fallback (gọi API HTTP/gRPC sang `system-admin-service` để lấy real data và update vào Redis).
     - Fallback cuối cùng về L4 default properties nếu mọi kết nối bị lỗi.
   - `SystemConfigEventListener`: `@KafkaListener` hoặc Webhook endpoint lắng nghe event `system.config.changed`, khi group `AUTH_LOGIN` có thay đổi thì trigger refresh L1 cache và build lại Passay validator.
4. **Cập nhật `PasswordPolicyService`**:
   - Inject `PasswordPolicyConfigProvider` thay vì `PasswordPolicyRepository`.
   - `getPolicy()` trả về `PasswordPolicyConfig`.

---

## 5. Phân loại sơ bộ (Pre-classifications)
- **Feature type**: `MAINTENANCE` (Refactoring & Architecture Alignment)
- **Flow type**: `Non-Financial`
- **Affected modules**:
  - `system-admin-service`: Seed data `system_configs` cho `AUTH_LOGIN`, Kafka event publication khi config update.
  - `auth-service`: Flyway drop table `password_policies`, xóa entity/repo, thêm config consumer + L1/L2 cache provider, cập nhật `PasswordPolicyService`.

---

## 6. GitNexus Findings
- **Symbols bị ảnh hưởng trực tiếp**:
  - `PasswordPolicyEntity` (`auth-service`): Class bị xóa hoàn toàn.
  - `PasswordPolicyRepository` (`auth-service`): Interface bị xóa hoàn toàn.
  - `PasswordPolicyService` (`auth-service`): Modify constructor & `getPolicy()`, `updatePolicy()`.
  - Các tests: `PasswordPolicyServiceTest`, `PasswordChangeIntegrationTest`.
- **Processes liên quan**:
  - `proc_47_changepassword` (ChangePassword flow).
  - Login authentication pipeline (`PolicyEnforcementStep` kiểm tra `isPasswordExpired`).

---

## 7. Các câu hỏi mở cho Phase Design (`/wf_openspec`)
- [RESOLVED] Có nên giữ lại `password_policies` table không? → Không, drop bảng sau khi migrate seed data sang `system_configs`.
- [RESOLVED] Topic name cho config sync: Dùng chung `system.config.changed` (Kafka). Với các môi trường không triển khai Kafka, hỗ trợ cơ chế push event qua gRPC/RESTful webhook hoặc Redis Pub/Sub để đảm bảo đồng bộ tức thời (instant sync) cho multi-system/multi-service.
- [RESOLVED] Cơ chế Seed ban đầu: `system-admin-service` sẽ có migration V-x chèn các keys đầy đủ (vd: `password.min_length`) cho group `AUTH_LOGIN` để tránh nhầm lẫn khi mở rộng.
- [RESOLVED] Khi khởi động lần đầu, nếu Redis trống: `auth-service` sẽ gọi API (gRPC/REST) sang `system-admin-service` để lấy dữ liệu thực (real data), sau đó tự push vào Redis. Nếu `system-admin-service` lỗi mới fallback cuối cùng về default `SecurityProperties` để đảm bảo an toàn.
- [RESOLVED] Key format trong Redis hash: Giữ nguyên tên key đầy đủ `password.min_length` trong hash để đảm bảo clear context.
