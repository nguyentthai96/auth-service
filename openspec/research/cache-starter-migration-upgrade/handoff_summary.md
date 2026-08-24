# Research Handoff: Cache Starter Migration & Upgrade

> Bridge document — tóm tắt kết quả research để downstream workflows (`/wf_brainstorm_openspec`, `/wf_pre_openspec`) có thể tiếp nhận context nhanh.

---

## Metadata

| Mục | Nội dung |
|-----|----------|
| **Feature** | Cache Starter Migration & Upgrade |
| **Ngày hoàn thành** | 2026-08-24 |
| **Recommendation** | BUILD on top of base-cache-starter |
| **Research directory** | `openspec/research/cache-starter-migration-upgrade/` |
| **Status** | complete |

---

## 1. Recommendation

**BUILD on top of `base-cache-starter`** — extend existing internal library with Encryption Engine (NONE/FULL/PARTIAL modes via AES-256-GCM), Compression (Zstd/LZ4), and Smart Serialization (type-aware). Migrate auth-service from custom `AbstractTwoTierCache` (~300 LOC, 5 files) to standard `@Cacheable` + `base-cache-starter` TwoLevelCacheManager. No external dependency has cache encryption — must build regardless of base choice.

---

## 2. Key Findings

| Category | Finding | Source |
|----------|---------|-------|
| Open Source | JetCache (7.3/10) — tham khảo pattern chỉ, không dùng trực tiếp. J2Cache (5.3/10) — bỏ qua (inactive). base-cache-starter (8.5/10) — **DÙNG + NÂNG CẤP**. | [opensource_findings.md](./opensource_findings.md) |
| Web Research | 7 search iterations, 12 unique sources. AES-256-GCM optimal (AES-NI). Zstd L3 best compression. JCA > Tink for cache (simpler, same perf). | [web_research.md](./web_research.md) |
| Gap Coverage | 100% — all 6 gaps (encryption FULL/PARTIAL, compression, smart serialization, migration, per-cache config) addressed in technical spec. | [comparison_analysis.md](./comparison_analysis.md) |
| Current System | 5 files to delete (~300 LOC), 6 files to modify. @Cacheable already used in GetPermissionsHandler. TwoLevelCacheManager bean exists in SecurityConfig. | [research_brief.md](./research_brief.md) |

---

## 3. Use Cases Identified

| UC ID | Tên | Mô tả ngắn | Priority |
|-------|-----|------------|----------|
| UC-001 | Migrate Permission Cache sang @Cacheable | Xóa ~300 LOC custom code (5 files), dùng @Cacheable + TwoLevelCacheManager | Must |
| UC-002 | Cache Encryption FULL Mode | AES-256-GCM encrypt toàn bộ value cho sensitive caches (sessions, tokens) | Must |
| UC-003 | Cache Encryption PARTIAL Mode | @CacheEncrypt annotation encrypt chỉ PII fields (email, phone) | Should |
| UC-004 | Smart Serialization — Data Type Optimization | Type-aware serialization (String→UTF-8, Number→ASCII, Object→JSON) + compression | Nice |
| UC-005 | Cross-Instance Cache Invalidation | Redis Pub/Sub broadcast L1 eviction across instances (already in base-cache-starter) | Must |

---

## 4. Technical Highlights

| Aspect | Decision/Finding |
|--------|-----------------|
| Architecture | Extend base-cache-starter with encryption/compression/serialization pipeline (write: serialize→compress→encrypt; read: decrypt→decompress→deserialize) |
| Data model | No new DB entities. New CacheProperties sections: encryption, compression, serialization + per-cache overrides |
| APIs | 0 new API endpoints. Infrastructure-only change. Affects existing permission/role/session cache behavior. |
| Key dependencies | JCA (javax.crypto) for AES-256-GCM, zstd-jni:1.5.7-15 for compression, lz4-java:1.8.0 (optional). All other deps already in project. |
| New code | ~464 LOC across 12 new files in base-cache-starter. 5 files deleted, 6 modified in auth-service. |
| Risk areas | 1) SmartSerializer type detection edge cases — mitigated by magic byte headers + comprehensive tests. 2) Key rotation breaks cached data — mitigated by TTL-based expiry. |

---

## 5. Ready for

| Workflow | Command | Khi nào dùng |
|----------|---------|-------------|
| Brainstorm (deep thinking) | `/wf_brainstorm_openspec cache-starter-migration-upgrade --from-research` | Muốn explore thêm design alternatives, discuss encryption patterns |
| URD Analysis | `/wf_pre_openspec openspec/research/cache-starter-migration-upgrade/business_analysis.md` | Đã rõ requirements, muốn formalize URD |
| OpenSpec (direct) | `/wf_openspec cache-starter-migration-upgrade` | Đã rõ mọi thứ, muốn generate implementation artifacts ngay |

---

## 6. Research Artifacts

| File | Phase | Content |
|------|-------|---------|
| [research_brief.md](./research_brief.md) | 1 | Scope, 17 keywords, current system analysis (16 related features, 9 integration points) |
| [opensource_findings.md](./opensource_findings.md) | 2 | 4 projects evaluated with 7-criteria scoring matrix + gap analysis |
| [web_research.md](./web_research.md) | 3 | 7 search iterations, 12 unique sources, 6 patterns/approaches documented |
| [comparison_analysis.md](./comparison_analysis.md) | 4 | 5 solutions compared across 12 features. Weighted decision matrix (9.40 vs 5.05 vs 3.05). Build vs Buy analysis. |
| [business_analysis.md](./business_analysis.md) | 5 | 5 Use Cases with basic + exception flows. 18 business rules. 9 NFRs. Traceability matrix. |
| [technical_spec.md](./technical_spec.md) | 6 | Architecture diagrams, data pipeline, 2 sequence diagrams, CacheProperties schema, 12 classes to create, 17 test cases |
| [validation_report.md](./validation_report.md) | 7 | All 5 checks PASS. 4 informational warnings documented. |

---

## 7. Review Status

| Check | Status | Notes |
|-------|:---:|-------|
| Source Verification | ✅ | All 12 sources verified. 4 GitHub repos + 8 official docs/articles. |
| Consistency | ✅ | BA UCs ↔ Tech Spec APIs aligned. Encryption modes consistent across all docs. |
| Completeness | ✅ | All 5 UCs have basic + exception flows. Scoring matrix complete for 4 projects. |
| Feasibility | ✅ | Feasible with current stack (Kotlin 2.x, Spring Boot 4.1, JDK 25). AES-NI confirmed. |
| Gap Coverage | ✅ | All 6 identified gaps addressed in technical spec with specific implementations. |

---

> **Generated by**: `wf_feature_research` workflow
> **Next step**: Choose a downstream workflow from section 5
