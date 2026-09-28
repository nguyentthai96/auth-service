# Pre-OpenSpec: user-identity-dual-key

> **Type**: EXTEND
> **Flow**: Command
> **Source**: User Idea (no URD)
> **Classification Evidence**: `UserEntity` → `rbac/adapter/out/persistence/entity/UserEntity.kt` → extends `SnowflakePersistentAuditableEntity` (đang thay đổi base class)
> **Archive**: N/A
> **Quality Score**: 88/100

## 📋 Feature Summary

Mở rộng `UserEntity` từ `SnowflakePersistentAuditableEntity` sang `DualIdPersistentAuditableEntity` (base-core), thêm cột `uuid` (UUIDv7) vào bảng `users` làm public identifier, tối ưu hóa index strategy cho luồng xác thực (Covering Index, Partial Index), và triển khai Identity Resolver (App-level Routing + Fallback) để phân giải danh tính người dùng từ một ô nhập duy nhất (email/username/phone).

Mục tiêu: đạt latency < 0.1ms cho login flow trên quy mô 10 triệu users trên PostgreSQL 17.

| Metric | Giá trị |
|--------|---------|
| Số FR | 14 (Idea: 11, Enriched: 3) |
| Issues | 2 (🔴: 0, 🟡: 2) |
| Open Questions | 0 (5 resolved in brainstorm sessions) |
| **Quality Score** | **88/100** |

---

## 1. Actors

- **Hệ thống (System)**: Tự động migration schema, tạo UUIDv7 khi persist entity
- **Người dùng cuối (End User)**: Đăng nhập bằng email/username/phone, nhận UUID trong JWT
- **API Consumer (Admin Dashboard / Mobile App)**: Sử dụng UUID để reference user thay vì internal ID

## 2. Functional Requirements

### FR-001: Thêm cột UUID vào bảng users [IDEA]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải thêm cột `uuid` (kiểu UUID, NOT NULL, UNIQUE) vào bảng `users` thông qua Flyway migration V24
- **Validation**: Cột `uuid` phải có constraint UNIQUE; các dòng hiện tại phải được backfill với UUID random

### FR-002: Đổi base class UserEntity [IDEA]
- **Actor**: Hệ thống
- **Action**: `UserEntity` phải kế thừa `DualIdPersistentAuditableEntity` thay vì `SnowflakePersistentAuditableEntity`, tự động sinh `uuid` (UUIDv7) khi tạo mới
- **Validation**: `uuid` phải là UUIDv7 (time-ordered), immutable sau khi tạo (`updatable = false`)

### FR-003: Thêm publicId vào domain model [IDEA]
- **Actor**: Hệ thống
- **Action**: Domain model `User` phải có trường `publicId: UUID` map từ `UserEntity.uuid`
- **Validation**: `publicId` không được null; mapper `UserPersistenceAdapter.toDomain()` phải map `uuid` → `publicId`

### FR-004: Tạo Covering Index cho email [IDEA]
- **Actor**: Hệ thống
- **Action**: Tạo Covering Index `idx_users_email_auth_covering` trên `users(email) INCLUDE (id, uuid, password_hash, status, failed_login_count, locked_until_at, mfa_enabled)` với điều kiện `WHERE active = TRUE`
- **Validation**: `EXPLAIN ANALYZE` phải hiển thị "Index Only Scan" cho login-by-email query

### FR-005: Tạo Covering Index cho username [IDEA]
- **Actor**: Hệ thống
- **Action**: Tạo Covering Index `idx_users_username_auth_covering` tương tự FR-004 cho username
- **Validation**: `EXPLAIN ANALYZE` phải hiển thị "Index Only Scan" cho login-by-username query

### FR-006: Tạo Partial Index cho phone [IDEA]
- **Actor**: Hệ thống
- **Action**: Tạo Partial UNIQUE Index `idx_users_phone_partial ON users(phone) WHERE phone IS NOT NULL AND active = TRUE`
- **Validation**: Cho phép nhiều dòng có `phone = NULL`, nhưng chặn duplicate khi phone có giá trị

### FR-007: Identity Resolver với App-level Routing [IDEA]
- **Actor**: Người dùng cuối
- **Action**: Khi người dùng nhập identifier vào ô đăng nhập, hệ thống phải phân loại tự động (email nếu có `@`, phone nếu bắt đầu `+` hoặc toàn số, còn lại là username) và gọi đúng 1 query chuyên biệt
- **Validation**: Mỗi lần đăng nhập chỉ thực hiện 1 Single Index Scan; latency < 0.1ms tại 10M rows

### FR-008: Fallback query cho edge cases [IDEA]
- **Actor**: Hệ thống
- **Action**: Nếu Fast-Path trả về null, hệ thống phải thực hiện fallback query OR (`WHERE username = :id OR email = :id OR phone = :id AND active = TRUE`)
- **Validation**: Fallback chỉ chạy khi Fast-Path trả null; không chạy fallback nếu Fast-Path có kết quả

### FR-009: Đồng bộ validation username [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Validation đăng ký phải enforce pattern `^[a-z][a-z0-9_]{2,29}$` cho username (bắt buộc chữ cái đầu, cấm ký tự `@`, cấm toàn chữ số) để đảm bảo identifier space rời rạc 100%
- **Validation**: Username `test@user` bị reject; username `12345` bị reject; username `user_123` được accept

### FR-010: Phone normalization E.164 [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Phone number phải được chuẩn hóa thành định dạng E.164 (`^\+[1-9]\d{1,14}$`) trước khi lưu vào database
- **Validation**: Phone `0901234567` phải được chuyển thành `+84901234567`; phone không hợp lệ bị reject

### FR-011: Repository methods mới [ENRICHED]
- **Actor**: Hệ thống
- **Action**: `UserRepository` phải có thêm 3 phương thức: `findByPhoneAndActiveTrue(phone)`, `findByUuidAndActiveTrue(uuid)`, `findByIdentifierAny(identifier)` (JPQL OR query)
- **Validation**: Mỗi phương thức phải return `UserEntity?`; `findByIdentifierAny` dùng `@Query` annotation

### FR-012: JWT sub claim chuyển sang UUID [IDEA]
- **Actor**: Hệ thống
- **Action**: JWT token phải sử dụng `sub = uuid` (UUIDv7 string) thay vì `id` (Long). Token không được chứa internal `id`.
- **Validation**: Decode JWT → `sub` phải là UUID string hợp lệ; không có claim nào chứa internal Long ID

### FR-013: API response trả UUID thay vì ID [IDEA]
- **Actor**: API Consumer (Mobile App / Admin Dashboard / Partner)
- **Action**: Tất cả API response public-facing phải trả `userId` dưới dạng UUID string thay vì Long. Breaking change — client phải update.
- **Validation**: GET /api/users/me response chứa `userId: "019234ab-..."` (UUID), không còn `id: 7298345...` (Long)

### FR-014: Dual exposure internal vs public [IDEA]
- **Actor**: Hệ thống
- **Action**: Internal events (Kafka giữa auth↔account↔notification) giữ `userId: Long` (không breaking change). Public API/JWT/partner dùng `uuid`. Internal services nhận JWT phải resolve `uuid → id` qua cache hoặc DB.
- **Validation**: Kafka event `UserRegisteredEvent.userId` vẫn là `Long`; API response `userId` là UUID string

## 3. Non-functional Requirements

- **NFR-001**: Latency login query < 0.1ms tại 10M rows (Index-Only Scan)
- **NFR-002**: Backward compatibility — existing API endpoints phải hoạt động bình thường sau migration
- **NFR-003**: Zero downtime migration — V24 phải thực hiện được không cần maintenance window

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp.

## 5. Enriched Domain Requirements

### Enriched FRs
- **FR-009** [ENRICHED]: Username validation pattern — cần thiết để đảm bảo App-level Routing phân loại chính xác 100%
- **FR-010** [ENRICHED]: Phone E.164 normalization — cần thiết cho Partial Index hoạt động đúng (tránh duplicate `0901234567` vs `+84901234567`)
- **FR-011** [ENRICHED]: Repository methods — phương thức truy vấn mới cần cho Identity Resolver

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| base-core | `DualIdPersistentAuditableEntity` | Dependency đã có sẵn |
| PostgreSQL 17 | Covering Index, Partial Index | Native features, không cần extension |

## 6. Assumptions

- `DualIdPersistentAuditableEntity` từ base-core hoạt động tương thích với `SnowflakePersistentAuditableEntity` (cùng PK type `Long`)
- Tất cả existing data trong bảng `users` sẽ được backfill UUID — backfill dùng `gen_random_uuid()` (v4) cho dữ liệu cũ, row mới sẽ là UUIDv7
- Không cần thay đổi JWT `sub` claim trong phase này (deferred)

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 23/25 | FR-010: Quy tắc chuyển đổi prefix quốc gia chưa rõ (VN = +84, nhưng các nước khác?) |
| Đầy đủ (Completeness) | 22/25 | FR-003: Chưa xác định cách expose `publicId` ra API response DTO |
| Nhất quán (Consistency) | 23/25 | FR-002: `@Version var version` cần xử lý khi đổi base class (DualId không kế thừa Version) |
| Kiểm thử được (Testability) | 20/25 | FR-004, FR-005: Covering Index chỉ verify được qua `EXPLAIN ANALYZE` — cần integration test thực |
| **Tổng** | **88/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích idea) | Cách cải thiện |
|---|----------|----------|-----|-------------------|----------------|
| 1 | Clarity | -2 | FR-010 | "Phone normalization E.164" — không nêu rõ default country code | Xác định country code mặc định (VN = +84) |
| 2 | Completeness | -3 | FR-003 | "publicId" — chưa thiết kế DTO response mapping | Thêm FR cho API response DTO |
| 3 | Consistency | -2 | FR-002 | "Đổi base class" — `@Version` hiện tại trên UserEntity sẽ xung đột nếu DualId không support | Kiểm tra DualId có hỗ trợ @Version không |
| 4 | Testability | -5 | FR-004/005 | "Covering Index" — chỉ verify được bằng EXPLAIN ANALYZE trên DB thật | Thêm integration test với Testcontainers PostgreSQL |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | `@Version var version: Int` trên UserEntity hiện tại — khi đổi sang DualIdPersistentAuditableEntity cần xử lý thủ công (DualId không kế thừa VersionedAuditableEntity) | FR-002 | Giữ `@Version` trên UserEntity hoặc tạo DualId + Version variant |
| 2 | Risk | 🟡 | Blast radius HIGH: 23 symbols phụ thuộc UserEntity — thay đổi base class có thể gây compile error nếu code dùng `id` trực tiếp | FR-002 | Phased rollout: DB first → Entity → API |

> Không phát hiện issue Critical (🔴).

## 9. Open Questions

### Resolved (Brainstorm Session 2 — 2026-09-28)
- ~~**OQ-1**: JWT `sub` claim migration~~ → **RESOLVED**: Chuyển sang `sub = uuid` (UUIDv7 string). JWT không chứa internal `id` (Long).
- ~~**OQ-2**: API response DTO breaking change~~ → **RESOLVED**: Đồng ý breaking change. API trả `uuid` thay `id`.
- ~~**OQ-3**: Inter-service event contracts~~ → **RESOLVED**: Internal (Kafka) giữ `id` (Long). Public (API/JWT/partner) dùng `uuid`.

### Resolved (Brainstorm Session 3 — 2026-09-28)
- ~~**OQ-4**: UUID → ID resolution cache~~ → **RESOLVED**: TTL = session duration (cấu hình động). Key pattern: `user:uuid:{uuid}:id`. Gia hạn theo action user. Invalidation tự nhiên khi session hết hạn.
- ~~**OQ-5**: API versioning strategy~~ → **RESOLVED**: Cut-over trực tiếp — xóa lịch sử cũ, không cần migration period v1→v2. Lý do: chỉ mới ở môi trường dev, chưa có dữ liệu thật.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Auth Service — User Identity & Authentication

### 10.2 Flow Type
Command (Schema migration + Entity refactoring)

### 10.3 Candidate Services
- **auth-service**: Primary target — chứa UserEntity, UserRepository, UserPersistenceAdapter, User domain model, login pipeline

### Detection Evidence
- Keyword: `UserEntity` → Module: `rbac/adapter/out/persistence/entity` → File: `UserEntity.kt`
- Keyword: `SnowflakePersistentAuditableEntity` → Module: `base-core/base-model` → File: `DualIdEntities.kt`
- Keyword: `findByUsernameAndActiveTrue` → Module: `rbac/adapter/out/persistence/repository` → File: `Repositories.kt`
- Keyword: `UserPersistenceAdapter` → Module: `auth/adapter/out/persistence` → File: `UserPersistenceAdapter.kt`
- Keyword: `User domain` → Module: `auth/domain/model` → File: `User.kt`

### 10.4 External Integrations
- **base-core (base-model)**: `DualIdPersistentAuditableEntity`, `UuidV7Generator`

### 10.5 Required Modules
- `rbac/adapter/out/persistence/entity` — Entity layer
- `rbac/adapter/out/persistence/repository` — Repository layer
- `auth/adapter/out/persistence` — Persistence adapter (mapper)
- `auth/domain/model` — Domain model
- `auth/application/port/out` — Port interfaces
- `resources/db/migration` — Flyway migrations

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | DBA/CI | Chạy Flyway V24: ALTER TABLE users ADD COLUMN uuid | PostgreSQL |
| 2 | DBA/CI | Chạy Flyway V25: CREATE covering indexes + partial index | PostgreSQL |
| 3 | Developer | Đổi UserEntity base class → DualIdPersistentAuditableEntity | Spring Boot |
| 4 | Developer | Thêm publicId vào User domain model + update mappers | Spring Boot |
| 5 | Developer | Thêm repository methods + Identity Resolver | Spring Boot |
| 6 | Developer | Update username validation + phone normalization | Spring Boot |

## 12. Traceability Matrix

| FR-ID | Idea Section | Spec Section | Affected Class | Status | Tag |
|-------|-------------|-------------|---------------|--------|-----|
| FR-001 | UUID column | V24 migration | Flyway SQL | Mapped | [MODIFY] |
| FR-002 | Base class change | Entity layer | `UserEntity.kt` | Mapped | [MODIFY] |
| FR-003 | publicId domain | Domain model | `User.kt`, `UserPersistenceAdapter.kt` | Mapped | [MODIFY] |
| FR-004 | Covering Index email | V25 migration | Flyway SQL | Mapped | [ADD] |
| FR-005 | Covering Index username | V25 migration | Flyway SQL | Mapped | [ADD] |
| FR-006 | Partial Index phone | V25 migration | Flyway SQL | Mapped | [ADD] |
| FR-007 | Identity Resolver | Application layer | NEW class (IdentityResolver) | Mapped | [ADD] |
| FR-008 | Fallback query | Repository | `Repositories.kt` | Mapped | [MODIFY] |
| FR-009 | Username validation | Validation layer | TBD (RegisterUserCommand) | Pending | [MODIFY] |
| FR-010 | Phone normalization | Validation layer | TBD (PhoneNormalizer) | Pending | [ADD] |
| FR-011 | Repository methods | Repository | `Repositories.kt` | Mapped | [MODIFY] |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- Feature này có **blast radius HIGH** (23 symbols) nhưng thay đổi thực tế là **minimal** — chỉ đổi base class + thêm column. Hầu hết 23 symbols chỉ bị ảnh hưởng gián tiếp (import path không đổi).
- `VersionedAuditableEntity` hiện tại extends `SnowflakePersistentAuditableEntity`. Khi chuyển sang DualId, cần tạo variant `DualIdVersionedAuditableEntity` hoặc giữ `@Version` trực tiếp trên `UserEntity`.
- Phased rollout (DB first → Entity → API) giảm thiểu rủi ro deployment.

### Related Features / Precedents
- `VersionedAuditableEntity` — [`shared/persistence/VersionedAuditableEntity.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/persistence/VersionedAuditableEntity.kt) — Pattern hiện tại cho optimistic locking
- `UserIdentityEntity` — [`rbac/.../UserIdentityEntity.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/UserIdentityEntity.kt) — SSO identity linking (giữ nguyên, không thay đổi)
- `DualIdPersistentAuditableEntity` — [`base-core/.../DualIdEntities.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/base-model/src/main/kotlin/com/ntt/basecore/model/id/DualIdEntities.kt#L108-L122) — Base class đích

### Integration Notes
- **base-core dependency**: `DualIdPersistentAuditableEntity` đã available, PK type vẫn là `Long` → tương thích với `AbstractCrudService`
- **Flyway**: Hiện tại ở V23 → V24, V25 là migrations tiếp theo
- **PostgreSQL 17**: Native support cho `UUID` type, `gen_random_uuid()`, partial index, covering index (`INCLUDE`)

### Suggested Approach
1. Reuse `DualIdPersistentAuditableEntity` từ base-core (KHÔNG tạo mới)
2. Giữ `@Version var version: Int` trực tiếp trên `UserEntity` (override, không cần tạo DualId+Version variant)
3. Phased migration: V24 (column) → V25 (indexes) → Entity change → Domain change → Repository additions
4. Identity Resolver: tạo utility function trong `UserPersistenceAdapter` hoặc tạo class `IdentityResolver` mới trong application layer

### Context from Confluence Images
N/A — source là User Idea, không có Confluence.
