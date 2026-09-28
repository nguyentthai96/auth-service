---
type: brainstorm_notes
change: user-identity-dual-key
date: 2026-09-28
selected_direction: "Native PostgreSQL Index + App-level Routing + Covering Index"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: User Identity Dual-Key Architecture

## Date
2026-09-28

## Context
User cần thêm cơ chế Dual-Key (Snowflake PK + UUIDv7 publicId) vào `UserEntity`, đồng thời tối ưu performance cho luồng login/identity resolution ở quy mô triệu users trên PostgreSQL 17.

Xuất phát từ `/wf_feature_research` trước đó (spec đầu tiên đề xuất bảng `user_credential_lookups`), user đặt câu hỏi trade-off nghiêm túc và yêu cầu debate chi tiết.

## Questions Asked & Answers

### Session 1 — Architecture Decisions (đã chốt)

- **Q1**: Một tài khoản User có cần liên kết đồng thời NHIỀU email hoặc NHIỀU số điện thoại không?
  → **A**: Chỉ cần duy nhất 1 username, 1 email chính và 1 phone.
  → **Impact**: Loại bỏ hoàn toàn nhu cầu bảng `user_credential_lookups`.

- **Q2**: Khi người dùng đăng nhập, phân giải danh tính theo cách nào? (App-level Routing vs. Single Query OR)
  → **A**: Cần tối ưu performance cho người dùng trong việc query.
  → **Impact**: Chọn App-level Routing (Single Index Scan < 0.1ms) thay vì OR query (BitmapOr 0.8-2.5ms).

- **Q3**: Có muốn áp dụng Covering Index (INCLUDE) để đạt Index-Only Scan?
  → **A**: (Hướng tới tối ưu performance) → Chọn Covering Index.
  → **Impact**: Luồng auth đạt < 0.04ms, 0 heap fetch.

### Session 2 — Open Questions Resolution (chốt 2026-09-28)

- **OQ-1**: JWT `sub` claim migration — khi nào chuyển từ `id` (Long) sang `uuid` (UUID string)?
  → **A**: Chuyển sang `uuid` ngay trong phase này.
  → **Decision**: JWT `sub = uuid` (UUIDv7 string). Không chứa `id` (Long) trong token.
  → **Impact**: Internal services phải resolve `uuid → id` qua cache/DB khi cần internal ID.

- **OQ-2**: API response DTO — breaking change khi đổi `id` → `uuid`?
  → **A**: Đồng ý breaking change.
  → **Decision**: API response sẽ trả `uuid` thay vì `id`. Client phải update.
  → **Impact**: Cần coordinate với frontend team (Admin Dashboard, Mobile App).

- **OQ-3**: Inter-service event contracts — reference user bằng `id` hay `uuid`?
  → **A**: Internal service (auth↔account↔notification) dùng `id` (Long) cho dễ. Public service (client/partner internet-facing) dùng `uuid`.
  → **Decision**: Dual exposure — internal boundary dùng `id`, public boundary dùng `uuid`.
  → **Impact**: Kafka events giữ `userId: Long`; REST API public trả `userId: UUID`.

- **OQ-4**: JWT chứa cả `id` và `uuid` hay chỉ `uuid`?
  → **A**: JWT chỉ chứa `sub = uuid` — internal services tự resolve `uuid → id` qua cache/DB.
  → **Decision**: Clean token, không leak internal ID. Services cần internal ID phải query/cache.
  → **Impact**: Cần UUID → ID resolution service hoặc cache layer.

## Approaches Considered

### Approach 1: Lookup Table (`user_credential_lookups`)
- **Mô tả**: Tạo bảng mapping riêng (lookup_type, lookup_key → user_uuid) với covering index
- **Pros**:
  - Extensible: thêm Zalo/Apple ID không cần ALTER TABLE
  - Multi-identity per type (nhiều email/phone cho 1 user)
  - Single-point lookup API
- **Cons**:
  - ❌ 2-query login flow (lookup → users JOIN)
  - ❌ Dual-write transaction overhead + data drift risk
  - ❌ Thêm entity, repository, adapter, migration mới
  - ❌ Over-engineering cho use case 1:1 (1 user = 1 email, 1 phone)
  - ❌ ~500MB-1GB RAM thêm cho index bảng lookup ở 10M rows

### Approach 2: Native PostgreSQL Index on `users` (✅ CHỌN)
- **Mô tả**: Giữ nguyên schema `users`, thêm `uuid` column, tạo covering index + partial index
- **Pros**:
  - ✅ 1-query login (Single Index Scan)
  - ✅ 0% data drift (single source of truth)
  - ✅ Tận dụng 100% schema + entity hiện tại
  - ✅ PostgreSQL 17 B-tree tối ưu cực tốt cho 10M+ rows
  - ✅ Covering index → Index-Only Scan (< 0.04ms)
- **Cons**:
  - Nếu tương lai cần multi-email/phone (1:N), phải thêm bảng phụ
  - Cần discipline về validation (username constraints)

### Approach 3: Hybrid (Fast-Path + Fallback) cho Identity Resolution (✅ CHỌN)
- **Mô tả**: App-level routing (Regex classifier → targeted query) + fallback OR query cho edge case
- **Pros**:
  - ✅ 99.9% requests: Single Index Scan (< 0.1ms)
  - ✅ 0.1% edge cases: vẫn tìm được user qua fallback
  - ✅ Giảm 66% IOPS cho Database (chỉ quét 1/3 index)
  - ✅ App server stateless → dễ scale ngang
- **Cons**:
  - Phụ thuộc vào username validation constraints (phải cấm '@' và toàn số)
  - Thêm ~10 dòng code classifier

## Selected Direction
**Native PostgreSQL Index + App-level Routing + Covering Index** — tổ hợp Approach 2 + 3.

**Lý do**:
1. Phù hợp 100% với constraint nghiệp vụ (1 username, 1 email, 1 phone per user)
2. Performance tối ưu nhất có thể đạt được trên PostgreSQL 17
3. Reuse-first: tận dụng `DualIdPersistentAuditableEntity` đã có sẵn trong base-core
4. KISS: không thêm bảng, không thêm entity, không dual-write

## Pre-classifications (preliminary)
- Feature type: EXTEND (mở rộng entity hiện tại, không tạo feature mới)
- Flow type: Command (entity migration + schema change)
- Affected modules:
  - `rbac/adapter/out/persistence/entity/UserEntity.kt`
  - `auth/domain/model/User.kt`
  - `auth/adapter/out/persistence/UserPersistenceAdapter.kt`
  - `auth/adapter/out/persistence/mapper/UserEntityMapper.kt`
  - `rbac/adapter/out/persistence/repository/Repositories.kt`
  - `auth/application/port/out/UserPort.kt`
  - Flyway migrations (V24, V25)
  - JWT token generation (sub → uuid)
  - API response DTOs

## GitNexus Findings
- **UserEntity**: 23 direct dependents → Risk HIGH
- **Related processes**: Login pipeline (SecurityPreCheckStep → UserPort → UserRepository)
- **Key symbols**: `UserEntity`, `UserRepository`, `UserPersistenceAdapter`, `User`, `UserId`
- **Architecture**: Clean Architecture with hexagonal ports/adapters

## Identifier Exposure Strategy (mới chốt Session 2)

```
┌─────────────────────────────────────────────────────────────┐
│                      JWT Token                               │
│  {                                                           │
│    "sub": "019234ab-7c8d-7e9f-a1b2-c3d4e5f67890",  ← uuid │
│    "roles": [...],                                           │
│    "iat": ..., "exp": ...                                    │
│  }                                                           │
│  ⚠️ Không chứa internal id (Long)                           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│               PUBLIC API RESPONSE (Client/Partner)           │
│  {                                                           │
│    "userId": "019234ab-7c8d-7e9f-a1b2-c3d4e5f67890", ← uuid│
│    "username": "john_doe",                                   │
│    "email": "john@example.com"                               │
│  }                                                           │
│  ⚠️ Breaking change — client phải update                    │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│               INTERNAL EVENTS (Kafka/gRPC)                   │
│  {                                                           │
│    "userId": 7298345123456789,                ← id (Long)    │
│    "eventType": "USER_REGISTERED",                           │
│    "timestamp": "..."                                        │
│  }                                                           │
│  ✅ Giữ nguyên — không breaking change nội bộ               │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│               UUID → ID RESOLUTION (internal services)       │
│                                                              │
│  Service nhận JWT (sub = uuid)                               │
│  → Cần internal id?                                          │
│  → Cache lookup: uuid → id (Redis hoặc local cache)          │
│  → Miss? Query: SELECT id FROM users WHERE uuid = ?          │
│  → Cache TTL: dài (uuid → id mapping immutable)              │
└─────────────────────────────────────────────────────────────┘
```

## Open Questions for Design Phase
- [RESOLVED] JWT `sub` claim: chuyển sang `uuid` (UUIDv7 string)
- [RESOLVED] JWT chỉ chứa `sub = uuid`, không chứa `id` (Long)
- [RESOLVED] API response: breaking change, trả `uuid` thay `id`
- [RESOLVED] Internal events (Kafka): giữ `id` (Long) — không breaking change
- [RESOLVED] Public API: dùng `uuid` — client/partner-facing
- [RESOLVED] Lookup table: BỎ — không cần
- [RESOLVED] Identity resolution: App-level Routing + Fallback
- [RESOLVED] Covering index: CÓ — áp dụng cho email + username auth queries
- [RESOLVED] UUIDv7 generation: Kotlin `Uuid.generateV7()` (đã có trong DualIdPersistentAuditableEntity)
- [RESOLVED] UUID → ID resolution cache: TTL = session duration (cấu hình động), gia hạn theo action user. Key pattern: `user:uuid:{uuid}:id`. Invalidation tự nhiên khi session hết hạn.
- [RESOLVED] API versioning: Cut-over trực tiếp — xóa lịch sử cũ, không cần migration period (môi trường dev, chưa có dữ liệu thật).
