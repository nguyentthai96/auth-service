# Open Source Findings: Two-Level Cache + Encryption

## 1. Projects Evaluated

### 1.1 JetCache (Alibaba) — Score: 7.2/10

| Tiêu chí | Score | Weight | Weighted |
|----------|-------|--------|----------|
| Feature completeness | 8 | 20% | 1.6 |
| Applicability | 6 | 15% | 0.9 |
| Activity | 8 | 15% | 1.2 |
| Documentation | 7 | 15% | 1.05 |
| Code quality | 7 | 15% | 1.05 |
| Community | 8 | 10% | 0.8 |
| Popularity | 7 | 10% | 0.7 |

- **GitHub**: https://github.com/alibaba/jetcache
- **Stars**: 4.3K+
- **Features**: Two-level cache (Caffeine L1 + Redis L2), `@Cached` annotation, auto-refresh, distributed lock
- **Gap**: Không có encryption support. Alibaba-centric API, hơi khác Spring Cache standard. Không có @CacheEncrypt annotation.
- **Verdict**: Tham khảo pattern, không dùng trực tiếp

### 1.2 J2Cache (Oschina) — Score: 5.8/10

| Tiêu chí | Score | Weight | Weighted |
|----------|-------|--------|----------|
| Feature completeness | 7 | 20% | 1.4 |
| Applicability | 5 | 15% | 0.75 |
| Activity | 4 | 15% | 0.6 |
| Documentation | 5 | 15% | 0.75 |
| Code quality | 5 | 15% | 0.75 |
| Community | 7 | 10% | 0.7 |
| Popularity | 6 | 10% | 0.6 |

- **GitHub**: https://github.com/oschina/J2Cache
- **Features**: Two-level cache, Redis/Memcached L2, JGroups/Redis Pub/Sub sync
- **Gap**: Java-only, không Kotlin. Ít active development. Không có encryption. Không có per-cache config.
- **Verdict**: Bỏ qua — outdated

### 1.3 Spring Boot Multi-Layer Cache (Community) — Score: 5.0/10

- **GitHub**: https://github.com/GaetanoPiazzolla/spring-boot-multi-layer-cache
- **Features**: Custom CacheManager wrapping Caffeine + Redis
- **Gap**: Demo project, không production-ready. Không có invalidation broadcast, encryption, metrics.
- **Verdict**: Tham khảo code pattern

### 1.4 base-cache-starter (Internal — com.ntt) — Score: 8.5/10

| Tiêu chí | Score | Weight | Weighted |
|----------|-------|--------|----------|
| Feature completeness | 8 | 20% | 1.6 |
| Applicability | 10 | 15% | 1.5 |
| Activity | 9 | 15% | 1.35 |
| Documentation | 8 | 15% | 1.2 |
| Code quality | 9 | 15% | 1.35 |
| Community | 6 | 10% | 0.6 |
| Popularity | 9 | 10% | 0.9 |

- **Source**: `components/base-core/starters/base-cache-starter/`
- **Features**: TwoLevelCache, TwoLevelCacheManager, Redis Pub/Sub invalidation, Micrometer metrics, per-cache config, graceful degradation
- **Gap**: ❌ No encryption, ❌ No smart serialization, ❌ No @CacheEncrypt
- **Verdict**: **DÙNG VÀ NÂNG CẤP** — best fit vì cùng tech stack, cùng team, cùng convention

## 2. Gap Analysis Summary

| Feature | JetCache | J2Cache | base-cache-starter | Cần cho project? |
|---------|:--------:|:-------:|:------------------:|:----------------:|
| Two-level cache (L1+L2) | ✅ | ✅ | ✅ | ⭐ Must |
| Spring @Cacheable compatible | ⚠️ partial | ⚠️ | ✅ | ⭐ Must |
| Cross-instance invalidation | ✅ | ✅ | ✅ | ⭐ Must |
| Micrometer metrics | ❌ | ❌ | ✅ | ⭐ Must |
| Per-cache config | ✅ | ❌ | ✅ | ⭐ Must |
| Cache encryption (FULL) | ❌ | ❌ | ❌ → **BUILD** | ⭐ Must |
| Partial field encryption | ❌ | ❌ | ❌ → **BUILD** | Nice to have |
| Smart serialization | ❌ | ❌ | ❌ → **BUILD** | Nice to have |
| Kotlin-first | ❌ | ❌ | ✅ | Nice to have |

## 3. Recommendation

**BUILD on top of `base-cache-starter`** — extend existing internal library rather than adopting external dependencies.

Reasons:
1. Same tech stack (Kotlin, Spring Boot 4.x, JDK 25)
2. Same team owns the code — full control over roadmap
3. Already has 80% of features needed
4. External alternatives lack encryption support anyway
5. No external dependency risk
