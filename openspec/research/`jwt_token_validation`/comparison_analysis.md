# Phân tích so sánh: JWT Token Validation

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | JWT Token Validation |
| **Ngày phân tích** | 2026-08-26 |
| **Recommendation** | **Build from scratch (enhance existing)** |
| **Rationale** | Current JJWT-based validation is solid but needs hardening: add audience validation, migrate blacklist from DB to Redis with Caffeine L1, implement claim validation pipeline, and add JWKS key rotation — all leveraging existing infrastructure. |
| **Confidence** | **HIGH** — existing codebase is well-structured, all required dependencies already in build.gradle.kts, clear patterns to follow (AbstractTwoTierCache, TokenStore port). |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | JJWT (current) | Open Source | JWT library for parsing, signing, validation | Already integrated, zero migration, Kotlin-compatible | No blacklist, no JWKS client, no caching | ✅ | 8.85 |
| 2 | Nimbus JOSE+JWT | Open Source | Full JOSE implementation with JWKS client | Best JWKS support, RemoteJWKSet caching | Migration from JJWT required, more verbose API | ⚠️ | 8.30 |
| 3 | Spring Security OAuth2 RS | Open Source | Framework-level JWT validation | Built-in JWKS, claim validators, introspection | May conflict with custom JwtAuthFilter | ⚠️ | 9.35 |
| 4 | Custom Build (enhance existing) | In-house | Enhance current JwtService + JwtAuthFilter with caching, claim pipeline, JWKS rotation | Perfect fit, no migration, leverages existing patterns | Development effort, maintenance burden | ✅ | N/A |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | JJWT (current) | Nimbus | Spring OAuth2 RS | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| JWT Parsing & Signature Verification | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| RS256 + HMAC Fallback | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Claim Validation (iss, exp, nbf) | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Audience (aud) Claim Validation | ⚠️ | ✅ | ✅ | ✅ | ⭐ Must |
| Clock Skew Tolerance | ⚠️ | ✅ | ✅ | ✅ | ⭐ Must |
| JTI Blacklist Check (Redis) | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Two-Tier Cache (Caffeine + Redis) | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| JWKS Endpoint (serve) | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| JWKS Key Rotation | ❌ | ✅ | ✅ | ✅ | ⭐ Must |
| Token Introspection (RFC 7662) | ❌ | ⚠️ | ✅ | ✅ | ⭐ Must |
| Token Type Validation (access/refresh/mfa/anon) | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Event Sourcing Integration | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Algorithm Allowlist | ⚠️ | ✅ | ✅ | ✅ | Nice to have |
| DPoP / Token Binding | ❌ | ⚠️ | ❌ | ❓ | Optional |
| **Coverage** | **5/12** | **7/12** | **8/12** | **12/12** | |

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 11 | 45% (JJWT) - 100% (Custom) |
| Nice to have | 1 | 0% - 100% |
| Optional | 1 | 0% - 0% |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | JJWT (current) | Spring OAuth2 RS | Custom Build | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| aud claim validation | RFC 8725 | ⚠️ (manual) | ✅ | ✅ | Partial — JJWT supports but not configured |
| Clock skew tolerance | RFC 7519 | ⚠️ (not set) | ✅ (60s default) | ✅ | Partial — JJWT supports but default=0 |
| Redis-backed blacklist | Performance | ❌ | ❌ | ✅ | Yes — all external solutions lack this |
| Two-tier cache for validation | Performance | ❌ | ❌ | ✅ | Yes — application-level concern |
| JWKS key rotation orchestration | Security | ❌ | ✅ | ✅ | Yes — JJWT has no rotation automation |
| Token type discrimination | Domain | ❌ | ❌ | ✅ | Yes — specific to auth-service domain |
| Validation event recording | Event Sourcing | ❌ | ❌ | ✅ | Yes — domain-specific concern |
| Introspection (RFC 7662) | Standards | ❌ | ✅ | ✅ | Partial — current endpoint exists but not fully compliant |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| Blacklist storage | DB (PostgreSQL via JPA) | Redis L2 + Caffeine L1 | DB query per request (~1-5ms) vs Redis (<0.1ms) | HIGH |
| Audience validation | Not implemented | Validate aud claim contains service identifier | Missing per RFC 8725 | MEDIUM |
| Clock skew | 0 seconds tolerance | 30-60 seconds tolerance | Token rejection in clock-skewed distributed env | MEDIUM |
| Key rotation | Manual (restart required) | Dual-key overlap via JWKS, hot reload | No zero-downtime rotation | MEDIUM |
| Introspection format | Custom response shape | RFC 7662 compliant response | Non-standard response format | LOW |
| Claim validation | Basic (exp via JJWT auto) | Full pipeline (iss, aud, exp, nbf, jti, type) | Missing aud, nbf not explicit | MEDIUM |
| Validation caching | None | Caffeine L1 (blacklist, public keys) | Every validation hits DB | HIGH |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build | Reuse Spring OAuth2 RS | Winner |
|--------|:---:|:---:|:---:|
| Time to market | 5-8 dev-days | 3-5 dev-days + migration risk | Custom Build (predictable) |
| Maintenance burden | Medium (own code) | Low (Spring team) | Spring OAuth2 RS |
| Feature coverage | 100% | 67% (missing blacklist, cache, events) | Custom Build |
| Integration effort | Low (existing patterns) | Medium (JwtAuthFilter refactor) | Custom Build |
| Long-term flexibility | High (full control) | Medium (framework constraints) | Custom Build |
| Risk | Low (incremental changes) | Medium (filter chain conflicts) | Custom Build |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | JJWT (keep+enhance) | Spring OAuth2 RS | Nimbus migration |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 9 (custom fills gaps) | 7 (missing blacklist/cache) | 8 (missing blacklist/cache) |
| Integration ease | 25% | 10 (already integrated) | 6 (filter conflict risk) | 4 (full migration) |
| Maintenance | 20% | 7 (own code) | 9 (Spring team) | 6 (new library) |
| Community/Support | 15% | 8 (JJWT community) | 10 (Spring ecosystem) | 7 (Nimbus team) |
| Learning curve | 10% | 10 (team knows JJWT) | 7 (new API patterns) | 5 (different API) |
| **Tổng điểm (weighted)** | | **8.85** | **7.60** | **6.15** |

### Reasoning

**Recommended approach**: Build from scratch (enhance existing JJWT-based implementation)

**Lý do**:
1. **Zero migration risk** — JJWT is already integrated and well-understood by the team. All gaps (blacklist caching, aud validation, JWKS rotation) are application-level concerns that no library solves out of the box.
2. **Existing patterns** — `AbstractTwoTierCache`, `TokenStore` port, `SecurityProperties` configuration all provide proven patterns to follow. The enhancement is incremental, not a rewrite.
3. **Domain-specific requirements** — Token type discrimination (access/refresh/mfa/anonymous), event sourcing integration, and two-tier caching are unique to this auth-service. No external library addresses these.

**Trade-offs chấp nhận**:
- Maintaining custom validation code — chấp nhận vì team already maintains JwtService, JwtAuthFilter, TokenController
- Not using Spring Security's built-in BearerTokenAuthenticationFilter — chấp nhận vì custom JwtAuthFilter provides domain-specific features (anonymous tokens, blacklist, type discrimination) that Spring's filter doesn't support

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| Redis unavailability breaks all validation | LOW | HIGH | L1 Caffeine cache as fallback, circuit breaker on Redis calls |
| Cache inconsistency allows revoked token use | MEDIUM | MEDIUM | Short L1 TTL (15-30s), write-through on revocation |
| JWKS key rotation race condition | LOW | MEDIUM | Dual-key overlap period > max token lifetime |
| Clock skew rejects valid tokens | LOW | LOW | Configure 60s clock skew tolerance in JJWT parser |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| Custom Build (enhance existing) | 5-8 days | MEDIUM | LOW |
| Spring OAuth2 RS migration | 8-12 days | HIGH | LOW |
| Nimbus migration | 10-15 days | HIGH | MEDIUM |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation |

---

> **Next step**: Business Analysis (business_analysis.md)
