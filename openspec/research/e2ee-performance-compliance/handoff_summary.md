---
type: research_handoff
feature: E2EE Performance & Compliance
date: 2026-08-15
recommendation: build
research_dir: openspec/research/e2ee-performance-compliance/
status: complete
---

# Research Handoff: E2EE Performance & Compliance

## Recommendation

**Build** (custom middleware) sử dụng **Google Tink** làm crypto engine chính, tích hợp **Cloud KMS** cho envelope encryption. Tink cung cấp Streaming AEAD, KMS envelope, và AAD context binding out-of-the-box — giảm thiểu rủi ro crypto implementation bugs.

## Key Findings

- **Open Source**: Google Tink (8.6/10) — top choice cho streaming AEAD + KMS envelope + automatic nonce management
- **Web Research**: 15+ sources xác nhận patterns cho frame-based encryption, AAD binding, break-glass procedure
- **Gap Coverage**: 8/8 critical gaps đã được giải quyết, 30 business rules documented

## Critical Architecture Decisions

| # | Decision | Answer |
|---|----------|--------|
| 1 | Fail-fast hay Fail-open? | **Fail-fast** — KMS unreachable + no cache = reject 503 |
| 2 | Stateless hay Stateful? | **Stateless (JWT)** — E2EE metadata trong JWT claims |
| 3 | Multi-tenancy? | **Có** — TenantID trong AAD context, per-tenant DEK |
| 4 | CI/CD Cipher Refresh? | **Versioned Payload** — `X-Cipher-Version` header, sunset 3 tháng |

## Use Cases Identified

| UC | Name | Priority |
|----|------|----------|
| UC-001 | Frame-based Encryption cho gRPC Streaming | P2 |
| UC-002 | Selective Field Encryption với AAD Context Binding | P1 |
| UC-003 | Client Time Skew Correction | P1 |
| UC-004 | KMS Envelope Encryption cho Key Management | **P0** |
| UC-005 | Audit Log Decryption Vault | P1 |
| UC-006 | Cipher Version Negotiation | P2 |

## Implementation Priority

```
P0 (Critical, Immediate):
  → UC-004: KMS Envelope Encryption — replace plaintext key storage

P1 (High, Next Sprint):
  → UC-002: Partial Encryption + AAD
  → UC-003: Time Skew + Anti-Replay
  → UC-005: Audit Vault Architecture

P2 (Medium, Planned):
  → UC-001: gRPC Streaming (when gRPC added)
  → UC-006: Cipher Version Negotiation (before first algorithm change)
```

## Tech Stack Additions

| Dependency | Version | Purpose |
|-----------|---------|---------|
| `com.google.crypto.tink:tink` | 1.15+ | Core crypto engine |
| `com.google.crypto.tink:tink-awskms` | 1.15+ | AWS KMS integration (or tink-gcpkms) |
| Promote `org.bouncycastle:bcprov-jdk18on` | Runtime | Advanced crypto primitives (if needed) |

## New Database Tables

| Table | Purpose |
|-------|---------|
| `encryption_key` | Encrypted DEK storage + metadata |
| `audit_log_encrypted` | Encrypted audit log entries |
| `vault_access_log` | Decryption Vault access tracking |
| `cipher_version` | Cipher suite version registry |

## Ready for

- `/wf_brainstorm_openspec e2ee-performance-compliance --from-research` — deep thinking with research context
- `/wf_pre_openspec openspec/research/e2ee-performance-compliance/business_analysis.md` — formal URD analysis
- `/wf_openspec e2ee-performance-compliance` — generate implementation artifacts

## Research Artifacts

| File | Content |
|------|---------|
| [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| [opensource_findings.md](./opensource_findings.md) | 5 open source projects evaluated (Tink ⭐) |
| [web_research.md](./web_research.md) | 15+ internet sources, 9 topic findings |
| [comparison_analysis.md](./comparison_analysis.md) | Feature comparison + gap analysis + decisions |
| [business_analysis.md](./business_analysis.md) | 6 use cases, 30 business rules |
| [technical_spec.md](./technical_spec.md) | Architecture, ERD, DDL, API spec, implementation notes |
| [validation_report.md](./validation_report.md) | 5/5 validation checks PASSED |
