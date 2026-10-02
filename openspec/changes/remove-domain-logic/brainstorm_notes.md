---
type: brainstorm_notes
change: remove-domain-logic
date: 2026-10-02
selected_direction: "Full Removal + Global RBAC"
pre_flow: "Command"
pre_feature_type: "MAINTENANCE"
status: complete
open_questions_resolved: 4
---

# Brainstorm Notes: Remove Domain Logic

## Date
2026-10-02

## Context
Hệ thống auth-service hiện tại có multi-domain management gây:
- Tăng độ phức tạp hệ thống đáng kể
- Mỗi lần login cần resolve domain → tốn tài nguyên
- Flow SwitchDomain không cần thiết cho use case hiện tại
- Branding per-domain chưa được dùng thực tế

User yêu cầu remove toàn bộ domain logic để clean gọn lại hệ thống.

## Questions Asked & Answers
- Q1: Mức độ removal? → A: Full removal — xóa hoàn toàn concept domain, flat RBAC
- Q2: RBAC/Permissions sau khi remove? → A: Global RBAC — roles/resources/permissions không scope theo domain
- Q3: Frontend admin đang dùng domain APIs? → A: Không chắc — kiểm tra cho thấy frontend có reference nhưng chủ yếu là hardcode mock data

## Approaches Considered

### Approach 1: Full Removal + Global RBAC ✅ SELECTED
- **Pros**: Simplest end state, maximum complexity reduction (~50 files simplified), login flow nhanh hơn đáng kể, dễ maintain
- **Cons**: Breaking change lớn, cần migration window cho JWT tokens, ~50 files affected

### Approach 2: Flatten to Single-Domain
- **Pros**: Ít thay đổi code hơn (giữ structure, hardcode domain), backward compatible hơn
- **Cons**: Vẫn giữ complexity trong code, "dead code" tồn tại, confusing cho developers mới

### Approach 3: Remove Domain Switching Only
- **Pros**: Ít risk nhất, chỉ bỏ SwitchDomainHandler + DomainLookupService
- **Cons**: Chưa giải quyết root cause — domain vẫn gắn sâu vào RBAC chain

## Selected Direction
**Approach 1: Full Removal + Global RBAC** — Lý do:
- User muốn clean gọn triệt để
- Multi-domain chưa mang lại giá trị thực tế
- Giảm complexity là goal chính
- Better to do it properly once than patch incrementally

## Pre-classifications (preliminary)
- Feature type: MAINTENANCE
- Flow type: Command (modifies entities, auth flow, DB schema)
- Affected modules:
  - auth.application (handlers, services, ports)
  - auth.adapter (controllers, persistence, kafka)
  - auth.domain (events, models)
  - rbac.adapter (entities, repositories, controllers)
  - rbac.application (RbacEngine, query handlers)
  - pbac.adapter (PolicyController)
  - Frontend admindashboard (types, services, pages)
  - Database (migration scripts)

## GitNexus Findings
- Related processes: proc_52_register, proc_59_switchdomain, proc_46_changepassword
- Key symbols: DomainEntity, UserDomainEntity, DomainPort, DomainLookupService, SwitchDomainHandler, TokenGenerator, RegisterHandler, RbacEngine, PolicyEnforcementStep, PasswordPolicyService
- Architecture insights:
  - Domain concept touches ALL layers (entity → repository → port → service → controller → JWT → frontend)
  - RBAC chain: User → UserGroup → Group(domainId) → GroupRole → DomainRole(domainId) → RolePermission → Permission → DomainResource(domainId)
  - After removal: User → UserGroup → Group → GroupRole → Role → RolePermission → Permission → Resource

## Blast Radius Summary
| Category | Count |
|----------|-------|
| Files to DELETE | ~6 |
| Entities to modify | ~5 |
| Application layer files | ~10 |
| Controller files | ~5 |
| Events/DTOs | ~8 |
| DB migrations | 1 |
| Frontend files | ~10 |
| Test files | ~5+ |
| **TOTAL** | **~50 files** |

## Phased Approach
1. Phase 1: Core Backend (auth-service) — DB, entities, RBAC, auth flow, events, controllers
2. Phase 2: Frontend (admindashboard) — types, services, pages
3. Phase 3: Seed data + cleanup

## Risks
- 🔴 JWT token migration (old tokens have domain claims)
- 🔴 DB migration on production
- 🟡 RBAC permission chain changes
- 🟡 Frontend/backend deploy sync
- 🟡 PBAC policies also use domainId
- 🟡 External event consumers reading domainId

## Open Questions for Design Phase
- [RESOLVED] Password policy: **1 Global Policy** (Option A)
  - Debate: 3 options analyzed (Global Single / Per-Role / Hybrid Config)
  - Decision: Single global policy — simplest, phù hợp mục tiêu giảm complexity, YAGNI
  - Change: PasswordPolicyEntity bỏ domainId, getPolicy() không param, validatorCache → single volatile
  - Extend path: thêm scope_type+scope_id nếu sau cần granular

- [RESOLVED] Config versioning domain — **KHÔNG liên quan** multi-domain nghiệp vụ
  - Checked: config-versioning.service.ts → domainName = "menu", "config" (config grouping)
  - API: /api/v1/configs/domains/{domainName} — chỉ là phân nhóm config, không phải business domain
  - Action: Không cần sửa config versioning

- [RESOLVED] Backward compatibility: **Cut-over** — hệ thống đang phát triển, chưa production
  - Không cần transitional phase, deploy trực tiếp

- [RESOLVED] Notification/account service consumers: **Không dùng domainId**
  - Scanned: notification-service → 0 domainId references
  - Scanned: account-service → 0 domainId references
  - An toàn để remove từ events mà không ảnh hưởng downstream
