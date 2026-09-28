# Handoff Summary: User Identity Dual-Key Architecture

> **Status**: RESEARCH COMPLETE — Ready for `/wf_openspec` or `/wf_brainstorm_openspec` continuation

---

## 1. Recommendation

**BUILD** — Extend `DualIdPersistentAuditableEntity` (already available in base-core) + Native PostgreSQL indexes + App-level Identity Routing.

**Do NOT** create a separate `user_credential_lookups` table (the previous spec's recommendation is superseded).

---

## 2. Key Findings

### Architecture Decision
| Decision | Approach | Rationale |
|----------|----------|-----------|
| Dual-Key | Snowflake (PK) + UUIDv7 (public) | 8-byte FK vs 16-byte, security, reuse base-core |
| No Lookup Table | Native indexes on `users` | 1:1 identity model, 0% data drift, 1-query login |
| Identity Resolution | App-level Routing + Fallback | 99.9% Single Index Scan (< 0.1ms), 0.1% safe fallback |
| Auth Index Strategy | Covering Index (`INCLUDE`) | Index-Only Scan, 0 heap fetch, < 0.04ms |
| Phone Index | Partial UNIQUE (`WHERE NOT NULL`) | 40-60% smaller index, nullable best practice |

### Performance Benchmarks (from web research)
- UUIDv7 insert: **2x-5x faster** than UUIDv4 on PostgreSQL B-tree
- UUIDv7 index size: **~24% smaller** than UUIDv4
- Covering Index: eliminates heap fetch entirely, verified by `EXPLAIN ANALYZE`
- App-level Routing: **~0.05ms** vs OR query **~0.8-2.5ms** (10x-50x faster)

### Codebase Status
- `DualIdPersistentAuditableEntity`: ✅ Available in `base-core/base-model`
- `UserEntity`: Currently extends `SnowflakePersistentAuditableEntity` — needs migration
- `UserRepository`: Has `findByUsername`, `findByEmail` — needs `findByPhone`, `findByUuid`, `findByIdentifierAny`
- `User` domain: No `publicId` field — needs addition
- Flyway: At V23 — V24 (uuid column), V25 (optimized indexes) needed
- Impact: 23 symbols depend on UserEntity — Risk HIGH

---

## 3. Scope

### In Scope
- Add `uuid` (UUIDv7) column to `users` table
- Change `UserEntity` base class → `DualIdPersistentAuditableEntity`
- Add `publicId: UUID` to `User` domain model
- Create covering indexes for email/username auth queries
- Create partial unique index for phone
- Implement Identity Resolver (App-level Routing + Fallback)
- Add new repository methods (`findByPhone`, `findByUuid`, `findByIdentifierAny`)
- Update mappers (`UserPersistenceAdapter`)

### Out of Scope (deferred)
- JWT `sub` claim migration (separate change — HIGH impact on all services)
- API response DTO migration (id → uuid)
- Inter-service event contract migration
- `user_identities` table changes (SSO — unchanged)

---

## 4. Files to Read Before Implementation

| File | Why |
|------|-----|
| `openspec/research/user-identity-dual-key/technical_spec.md` | Full technical specification |
| `openspec/changes/user-identity-dual-key/brainstorm_notes.md` | All decisions & trade-offs |
| `components/base-core/base-model/.../DualIdEntities.kt` | Base class implementation |
| `UserEntity.kt` | Current entity — migration target |
| `User.kt` | Domain model — add publicId |
| `UserPersistenceAdapter.kt` | Mapper — update toDomain() |
| `Repositories.kt` | Repository — add new methods |

---

## 5. Suggested Next Steps

```
/wf_openspec user-identity-dual-key
  → Generates: proposal.md, design.md, tasks.md
  → Phase 1: DB migration (V24, V25)
  → Phase 2: Entity + Domain changes
  → Phase 3: Repository + Identity Resolver
```
