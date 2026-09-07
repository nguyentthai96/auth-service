---
type: research_handoff
feature: jwt-security-redesign
date: 2026-09-07
recommendation: build
research_dir: openspec/research/jwt-security-redesign/
status: complete
---

# Research Handoff: JWT OAuth2 Security Redesign

## Recommendation

**BUILD** — Enhance hệ thống auth hiện tại thay vì adopt giải pháp bên ngoài. Codebase đã có 80% foundation (JWT service, blacklist 3-tier, session management, CQRS handlers). Chỉ cần bổ sung 5 components: FingerprintService, MailQueue, DeviceController, enhanced JwtAuthFilter, và session-token lifecycle sync.

## Key Findings

### Internet Research (15+ sources)
- **JWT Blacklist**: Industry standard = Redis denylist by JTI with TTL = remaining token lifetime. Project đã có implementation 3-tier rất tốt (Caffeine L1 + Redis L2 + DB L3)
- **Device Fingerprint**: SHA-256 hash from client signals (UA, Accept-Language, IP prefix, Client Hints). DPoP (RFC 9449) available natively từ Spring Security 6.5+ nhưng over-engineering cho phase 1
- **Token Binding**: Embed `device_fingerprint` claim trong JWT, validate mỗi request. Strategy pattern cho strict/lenient mode
- **Mail Queue**: Transactional outbox pattern — INSERT mail_queue trong cùng transaction → @Scheduled poller → SMTP. PostgreSQL `FOR UPDATE SKIP LOCKED` cho multi-instance safety
- **Device Management**: Centralized session store + device metadata + kick out = revoke session + blacklist JTI + revoke refresh token

### Current System Gaps
| Component | Hiện trạng | Target |
|-----------|----------|--------|
| Fingerprint | Field tùy chọn, không validate | Server-computed + JWT claim + validation |
| Token-Session sync | Revoke session ≠ blacklist token | Unified lifecycle (session ↔ token ↔ blacklist) |
| Device API | SessionController basic | Full CRUD + kick out + kick all |
| New device email | Event emitted, no consumer | Event → MailQueue → Job → SMTP |
| Mail system | Không có | mail_queue table + mail_templates + MailJobScheduler |

## Use Cases Identified

| UC | Description |
|-----|-------------|
| UC-001 | JWT Token Lifecycle Management (issue → validate → refresh → revoke → blacklist) |
| UC-002 | Device Fingerprint Binding (SHA-256 hash, JWT claim, per-request validation) |
| UC-003 | Active Device Management (list, kick out, clear all, admin override) |
| UC-004 | New Device Login Email Notification (event-driven, async) |
| UC-005 | Mail Queue & Template Job (outbox pattern, @Scheduled, exponential backoff) |
| UC-006 | Session-Token Lifecycle Synchronization (revoke session = blacklist token = revoke refresh) |

## Implementation Scope

| Phase | Components | Effort |
|-------|-----------|--------|
| Phase 1 | FingerprintService, Mail entities, Flyway migrations | ~2 days |
| Phase 2 | MailQueueService, MailJobScheduler, NewDeviceMailHandler | ~2 days |
| Phase 3 | Enhanced JwtService, JwtAuthFilter, LoginSessionService, LoginHandler | ~3 days |
| Phase 4 | DeviceController, SecurityConfig, DTOs | ~1 day |
| Phase 5 | Unit + Integration tests | ~2 days |
| **Total** | 15 new files + 10 modified files | **~10 days** |

## Ready for

- `/wf_brainstorm_openspec jwt-security-redesign --from-research` — deep thinking with research context
- `/wf_pre_openspec openspec/research/jwt-security-redesign/business_analysis.md` — formal URD analysis
- `/wf_openspec jwt-security-redesign` — generate implementation artifacts

## Research Artifacts

| File | Content |
|------|---------|
| [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis (4 sections) |
| [business_analysis.md](./business_analysis.md) | 6 use cases, 23 business rules, traceability matrix |
| [technical_spec.md](./technical_spec.md) | Architecture diagrams, ERD, API spec, implementation notes |
| [handoff_summary.md](./handoff_summary.md) | This file — summary + next steps |
