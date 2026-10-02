# Proposal: remove-domain-logic

> **Change Type**: MAINTENANCE
> **Priority**: HIGH — simplifies core architecture
> **Estimated Scope**: ~50 files (backend + frontend)
> **Risk**: 🟡 MEDIUM (mitigated by cut-over strategy)

---

## Mục tiêu

Xóa hoàn toàn concept multi-domain khỏi auth-service, chuyển sang flat/global RBAC system.

## Lý do

1. Domain management tăng độ phức tạp hệ thống đáng kể
2. Mỗi lần login cần resolve domain membership → tốn tài nguyên
3. SwitchDomain flow không cần thiết cho use case hiện tại
4. Hệ thống chưa production → cơ hội tốt để clean up trước khi scale

## Scope

### In Scope
- ✅ Remove `DomainEntity`, `UserDomainEntity`, `DomainResourceEntity` → `ResourceEntity`, `DomainRoleEntity` → `RoleEntity`
- ✅ Remove `SwitchDomainHandler`, `DomainLookupService`, `DomainPort`, `DomainPersistenceAdapter`
- ✅ Simplify `TokenGenerator`, `RegisterHandler`, `RbacEngine`, `PasswordPolicyService`
- ✅ Remove domain claims from JWT tokens
- ✅ Flatten RBAC API paths (remove `{domainId}` path variables)
- ✅ Remove `domainId`/`domainCode` from events
- ✅ Update frontend types/services/pages
- ✅ Database migration (DROP/ALTER tables)

### Out of Scope
- ❌ Config versioning domain (`/api/v1/configs/domains/{domainName}`) — khác concept, không liên quan
- ❌ Base controller hierarchy — giữ nguyên
- ❌ Authentication pipeline (ngoài PolicyEnforcementStep domain lookup)
- ❌ MFA, SSO, Anonymous session logic

## Acceptance Criteria

| # | Criterion | Verify |
|---|-----------|--------|
| AC-1 | Không còn reference `DomainEntity`/`UserDomainEntity` trong codebase | `grep -r "DomainEntity" src/` → 0 results |
| AC-2 | JWT tokens không chứa `domains`/`active_domain` claims | Login → decode JWT → verify |
| AC-3 | RBAC methods hoạt động không cần `domainId` | `RbacEngine.hasPermission(userId, permCode)` → OK |
| AC-4 | Password policy global (1 row, no domainId) | `PasswordPolicyService.getPolicy()` → returns global |
| AC-5 | All tests pass | `./gradlew test` → GREEN |
| AC-6 | Frontend compile thành công | `npm run build` → OK |
| AC-7 | Login flow hoạt động end-to-end | Manual test hoặc integration test |

## Breaking Changes

- JWT token format thay đổi (bỏ `domains`, `active_domain` claims)
- API endpoint `/auth/switch-domain` bị xóa
- RBAC admin APIs bỏ `{domainId}` path variable
- Events bỏ `domainId`/`domainCode` fields

## Migration Strategy

**Cut-over** (user confirmed):
- Hệ thống chưa production → không cần transitional phase
- Deploy database migration → backend → frontend cùng lúc
- Invalidate all existing JWT tokens khi deploy
