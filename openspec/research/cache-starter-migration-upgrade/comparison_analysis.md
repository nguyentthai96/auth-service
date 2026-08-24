# Phân tích so sánh: Cache Starter Migration & Upgrade

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Cache Starter Migration & Upgrade |
| **Ngày phân tích** | 2026-08-24 |
| **Recommendation** | **BUILD on top of base-cache-starter** — extend existing internal library with Encryption Engine + Compression + Smart Serialization |
| **Rationale** | No existing solution provides cache encryption. base-cache-starter already has 80% of needed features (TwoLevelCache, Pub/Sub invalidation, metrics, per-cache config). Same tech stack, same team, full control. |
| **Confidence** | **HIGH** — All research questions answered, tech stack validated, gap analysis complete, no external dependency risk |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | JetCache (Alibaba) | Open Source | Annotation-based two-level caching framework | Production-proven at scale, auto-refresh, distributed lock | Alibaba-centric API, custom @Cached not @Cacheable, no encryption | ⚠️ | 7.3 |
| 2 | J2Cache (Oschina) | Open Source | Generic two-level cache with multi-provider support | Flexible L1/L2 provider choices | Inactive (last commit 2023), Java-only, no encryption/metrics | ❌ | 5.3 |
| 3 | Redis Enterprise TDE | Commercial | Transparent storage-layer encryption | Zero code change, managed | Commercial license, vendor lock-in, no field-level encryption | ❌ | N/A |
| 4 | Custom Build (current auth-service) | In-house | AbstractTwoTierCache manual implementation | Full custom control | 300+ LOC duplicate, no cross-instance invalidation, KEYS command usage | ❌ | N/A |
| 5 | **base-cache-starter + Upgrade** ⭐ | Internal Library | Extend existing platform library with encryption + compression + smart serialization | Same stack, internal ownership, 80% features done, reuse-first | Must invest development time (~6-9 days) | ✅ | 8.5 |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | JetCache | J2Cache | Redis TDE | Custom (current) | base-cache-starter (upgraded) | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|:---:|
| Two-level cache (L1+L2) | ✅ | ✅ | N/A | ✅ | ✅ | ⭐ Must |
| Spring @Cacheable compatible | ⚠️ | ⚠️ | N/A | ❌ | ✅ | ⭐ Must |
| Cross-instance L1 invalidation | ✅ | ✅ | N/A | ❌ | ✅ | ⭐ Must |
| Micrometer metrics | ❌ | ❌ | N/A | ❌ | ✅ | ⭐ Must |
| Per-cache named config | ✅ | ❌ | N/A | ❌ | ✅ | ⭐ Must |
| Full cache encryption (FULL) | ❌ | ❌ | ✅ | ❌ | ✅ (BUILD) | ⭐ Must |
| Partial field encryption (PARTIAL) | ❌ | ❌ | ❌ | ❌ | ✅ (BUILD) | Nice to have |
| Smart data type serialization | ❌ | ❌ | N/A | ❌ | ✅ (BUILD) | Nice to have |
| Compression (Zstd/LZ4) | ❌ | ❌ | N/A | ❌ | ✅ (BUILD) | Nice to have |
| Graceful degradation | ⚠️ | ⚠️ | ✅ | ❌ | ✅ | ⭐ Must |
| Kotlin-first | ❌ | ❌ | N/A | ✅ | ✅ | Nice to have |
| Zero external dependency | ❌ | ❌ | ❌ | ✅ | ✅ | ⭐ Must |

<!-- Legend: ✅ Có đầy đủ | ⚠️ Có nhưng hạn chế | ❌ Không có | ❓ Cần build thêm -->

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 7 | 29% (JetCache) — 100% (base-cache-starter upgraded) |
| Nice to have | 5 | 0% (all external) — 100% (base-cache-starter upgraded) |
| Optional | 0 | N/A |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | JetCache | J2Cache | base-cache-starter (current) | base-cache-starter (upgraded) | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|:---:|
| Two-level cache L1+L2 | UC-001 | ✅ | ✅ | ✅ | ✅ | None |
| Spring @Cacheable | UC-001 | ⚠️ | ⚠️ | ✅ | ✅ | JetCache/J2Cache use custom APIs |
| Encryption FULL mode | UC-002 | ❌ | ❌ | ❌ | ✅ (BUILD) | All existing solutions — must build |
| Encryption PARTIAL mode | UC-003 | ❌ | ❌ | ❌ | ✅ (BUILD) | All existing solutions — must build |
| Smart serialization | UC-004 | ❌ | ❌ | ❌ | ✅ (BUILD) | All existing solutions — must build |
| Cross-instance invalidation | UC-005 | ✅ | ✅ | ✅ | ✅ | None |
| Compression support | NFR | ❌ | ❌ | ❌ | ✅ (BUILD) | All existing solutions — must build |
| Micrometer metrics | NFR | ❌ | ❌ | ✅ | ✅ | JetCache/J2Cache lack metrics |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| Cache implementation | Custom `AbstractTwoTierCache` (~300 LOC) + 3 implementations | `@Cacheable` + `base-cache-starter` TwoLevelCacheManager | Delete custom code, adopt starter | **HIGH** — eliminates code duplication |
| Cache encryption | None — PII stored plaintext in Redis | AES-256-GCM encryption (FULL/PARTIAL/NONE per cache) | Must build encryption engine | **HIGH** — security compliance |
| Cross-instance sync | None — no L1 invalidation broadcast | Redis Pub/Sub via CacheInvalidationPublisher | Already in base-cache-starter | **LOW** — feature exists |
| Cache metrics | None | Micrometer integration via CacheMetricsAutoConfiguration | Already in base-cache-starter | **LOW** — feature exists |
| Serialization | Jackson JSON for everything | Smart serialization (type-aware + compression) | Must build SmartCacheSerializer | **MEDIUM** — performance optimization |
| Cache config | Hardcoded in code | `application.yml` per-cache config | CacheProperties already supports this | **LOW** — config exists |
| PermissionCache port | 3 implementations, @Primary conflict | Single @Cacheable annotation on handler | Delete port + implementations | **HIGH** — simplification |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build (upgrade base-cache-starter) | Reuse JetCache | Winner |
|--------|:---:|:---:|:---:|
| Time to market | ~6-9 days total (encryption + migration) | ~2 days integration + must still build encryption | Custom Build |
| Maintenance burden | Internal team owns all code | External dependency updates + shim layer maintenance | Custom Build |
| Feature coverage | 100% — all Must + Nice features | 57% — only Must features, no encryption | Custom Build |
| Integration effort | Zero — same tech stack, same conventions | Medium — different annotation model, different config | Custom Build |
| Long-term flexibility | Full control over roadmap and features | Limited by JetCache roadmap | Custom Build |
| Risk | Low — additive changes to existing stable library | Medium — API differences, Spring Boot 4.x compatibility uncertain | Custom Build |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | JetCache | J2Cache | base-cache-starter (upgraded) |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 5 (1.50) | 3 (0.90) | 10 (3.00) |
| Integration ease | 25% | 4 (1.00) | 3 (0.75) | 10 (2.50) |
| Maintenance | 20% | 5 (1.00) | 2 (0.40) | 9 (1.80) |
| Community/Support | 15% | 7 (1.05) | 4 (0.60) | 8 (1.20) |
| Learning curve | 10% | 5 (0.50) | 4 (0.40) | 9 (0.90) |
| **Tổng điểm (weighted)** | | **5.05** | **3.05** | **9.40** |

### Reasoning

**Recommended approach**: BUILD on top of base-cache-starter — extend existing internal library

**Lý do**:
1. **No encryption exists anywhere** — JetCache, J2Cache, all community projects lack cache encryption. Must build encryption engine regardless of which base is chosen. Building on base-cache-starter means building on 80% complete foundation.
2. **Same tech stack, zero friction** — base-cache-starter is Kotlin, Spring Boot 4.x, JDK 25 — identical to auth-service. No translation layer, no compatibility shim, no version conflicts.
3. **Internal ownership eliminates dependency risk** — Same team controls the library. Can fix bugs immediately, add features on demand, align with platform roadmap. No external dependency lifecycle to manage.

**Trade-offs chấp nhận**:
- Must invest ~6-9 development days for encryption + compression + migration — chấp nhận vì no shortcut exists (encryption must be built in any approach)
- SmartCacheSerializer adds complexity to codebase — chấp nhận vì complexity is well-isolated in starter library, not leaked to service code

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| Encryption latency overhead | LOW | LOW | AES-NI hardware acceleration (~0.5μs/KB). L1 serves plaintext. Only L2 encrypted. |
| Zstd JNI native library loading failure | LOW | LOW | `@ConditionalOnClass` — graceful fallback to no compression. Runtime detection. |
| Key rotation breaks existing cached data | MEDIUM | MEDIUM | TTL-based expiry — old entries expire naturally. New key prefix prevents read of old data. |
| SmartSerializer type detection edge cases | MEDIUM | LOW | Comprehensive unit tests. Magic byte header protocol. Fallback to JSON on detection failure. |
| Breaking existing base-cache-starter consumers | LOW | HIGH | All new features are opt-in (disabled by default). No breaking changes to existing API. |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| JetCache adoption | 2 + encryption build (~4) = ~6 | HIGH (two codebases to maintain) | HIGH (external dep + custom encryption) |
| base-cache-starter upgrade | ~6-9 (all-inclusive) | MEDIUM (additive, well-scoped) | LOW (single codebase, internal team) |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation |

---

> **Next step**: Business Analysis (business_analysis.md)
