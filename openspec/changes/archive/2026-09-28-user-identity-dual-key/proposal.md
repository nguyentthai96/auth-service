# Proposal: User Identity Dual-Key Architecture

> **Change**: `user-identity-dual-key`
> **Type**: EXTEND
> **Service**: auth-service
> **Date**: 2026-09-28

---

## 1. Mục tiêu

Mở rộng `UserEntity` để hỗ trợ Dual-Key pattern (Snowflake ID internal + UUIDv7 public), tối ưu hóa performance xác thực người dùng cho quy mô 10M+ users bằng Covering Index + App-level Identity Routing.

## 2. Phạm vi

### In Scope
- Thêm cột `uuid` (UUIDv7) vào bảng `users`
- Đổi `UserEntity` base class → `DualIdPersistentAuditableEntity`
- Thêm `publicId: UUID` vào `User` domain model
- Tạo Covering Index cho email/username auth queries
- Tạo Partial UNIQUE Index cho phone
- Implement Identity Resolver (App-level Routing + Fallback)
- Thêm repository methods (`findByPhone`, `findByUuid`, `findByIdentifierAny`)
- JWT `sub` claim chuyển sang `uuid`
- API response trả `uuid` thay `id` (breaking change)
- Internal events (Kafka) giữ `id` (Long)

### Out of Scope
- Bảng `user_credential_lookups` (bỏ — không cần)
- Multi-email / multi-phone per user
- Cross-service migration to uuid (deferred)
- `user_identities` table changes (SSO — unchanged)

## 3. Decisions Log

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | Snowflake PK + UUIDv7 public | 8-byte FK, non-guessable public ID |
| D2 | Bỏ lookup table | 1:1 identity, 0% data drift |
| D3 | App-level Routing + Fallback | 99.9% Single Index Scan < 0.1ms |
| D4 | Covering Index (INCLUDE) | Index-Only Scan, 0 heap fetch |
| D5 | Partial UNIQUE phone | 40-60% smaller index |
| D6 | Reuse DualId from base-core | Reuse-first principle |
| D7 | JWT sub = uuid only | Clean token, no internal ID leak |
| D8 | API breaking change | Dev env, no production data |
| D9 | Dual exposure strategy | Internal: id / Public: uuid |
| D10 | Cache TTL = session-based | Natural invalidation |
| D11 | Cut-over (no migration) | Dev env only, no versioning needed |

## 4. Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Blast radius: 23 symbols depend on UserEntity | 🟡 | Phased rollout: DB → Entity → API |
| `@Version` not in DualId base class | 🟡 | Keep `@Version` directly on UserEntity |
| JWT change breaks existing tokens | 🟡 | Dev env — invalidate all sessions |

## 5. Timeline (Implementation Phases)

| Phase | Scope | Est. |
|-------|-------|------|
| Phase 1 | Flyway V24 (uuid column) + V25 (indexes) | 1 task |
| Phase 2 | Entity + Domain model changes | 3 tasks |
| Phase 3 | Repository + Identity Resolver | 2 tasks |
| Phase 4 | JWT + API response migration | 2 tasks |
