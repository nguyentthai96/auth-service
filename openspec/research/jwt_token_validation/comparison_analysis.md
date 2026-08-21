# Phân tích so sánh: JWT Token Validation

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | JWT Token Validation |
| **Ngày phân tích** | 2025-07-11 |
| **Recommendation** | **Build from scratch (augment existing jjwt-based implementation)** |
| **Rationale** | jjwt is already deeply integrated; the project needs a structured validation pipeline, not a library swap. Gaps (JWKS caching, algorithm whitelist, unified type validators, event-sourced audit) are application-level concerns best addressed by custom code layered on top of jjwt. |
| **Confidence** | **HIGH** — existing codebase is well-structured, gaps are clearly defined, and no external solution covers all needs without significant integration overhead. |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | jjwt (current) | Open Source Library | Low-level JWT signing/parsing; validation built manually on top | Already integrated, fluent API, well-tested | No JWKS fetch, no pipeline, no blacklist | ⚠️ (needs augmentation) | 8.85 |
| 2 | Spring Security OAuth2 RS | Framework | NimbusJwtDecoder + JWKS auto-fetch + custom validators | Native Spring, well-maintained | Opinionated, conflicts with custom filter chain | ⚠️ (partial adoption) | 9.30 |
| 3 | Keycloak | Full Platform | Complete IAM with built-in token validation | Feature-complete | Too heavy, replaces entire auth-service | ❌ | 7.80 |
| 4 | Custom Build (augment jjwt) | In-house | Structured validation pipeline on top of existing jjwt | Full feature coverage, fits architecture | Maintenance responsibility | ✅ | N/A |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | jjwt (current) | Spring Security RS | Keycloak | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| Signature Verification (RS256) | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Algorithm Whitelist Enforcement | ⚠️ (manual) | ✅ | ✅ | ✅ | ⭐ Must |
| Claims Validation (exp/iss/aud) | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| JWKS Key Fetch + Cache | ❌ | ✅ | ✅ | ✅ (build) | ⭐ Must |
| Token Blacklist (JTI-based) | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Token Type Discrimination (5 types) | ⚠️ | ❌ | ❌ | ✅ | ⭐ Must |
| Token Introspection (RFC 7662) | ❌ | ✅ | ✅ | ✅ (built) | Nice to have |
| Two-Level Cache (Caffeine + Redis) | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Event Sourcing Integration | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Service Token Validation (HMAC) | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Key Rotation (zero downtime) | ❌ | ✅ | ✅ | ✅ (build) | Nice to have |
| DPoP Support | ❌ | ❌ | ⚠️ | ❓ | Optional |
| **Coverage** | **3/9 Must** | **3/9 Must** | **3/9 Must** | **9/9 Must** | |

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 9 | 33% (jjwt/Spring/Keycloak) - 100% (Custom) |
| Nice to have | 2 | 0% - 100% |
| Optional | 1 | 0% |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | jjwt (current) | Spring Security RS | Custom Build | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| Unified validation pipeline | Architecture | ❌ | ⚠️ | ✅ | Có — jjwt / Spring |
| Algorithm whitelist (anti-confusion) | OWASP | ⚠️ | ✅ | ✅ | Partial — jjwt needs explicit |
| JWKS key caching with auto-refresh | RFC 7517 | ❌ | ✅ | ✅ | Có — jjwt has no JWKS fetch |
| JTI blacklist with two-level cache | Project requirement | ❌ | ❌ | ✅ | Có — no solution provides this |
| Token type discrimination (5 types) | Current system | ⚠️ | ❌ | ✅ | Có — only custom handles this |
| Validation failure events (Event Sourcing) | Project architecture | ❌ | ❌ | ✅ | Có — no solution is ES-aware |
| Service token validation (HMAC) | FR-021 | ❌ | ❌ | ✅ | Có — project-specific need |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| Validation pipeline | Ad-hoc per component (JwtAuthFilter, ServiceAuthFilter, TokenController) | Unified pipeline: signature → algorithm → claims → blacklist → type → context | No centralized validation chain | HIGH |
| Algorithm enforcement | Implicit (RS256 try, then HMAC fallback) | Explicit whitelist: RS256 primary, HS256 legacy-only with migration deadline | Algorithm confusion risk | HIGH |
| Blacklist checking | JwtAuthFilter checks, but ServiceAuthFilter and introspection don't share logic | Centralized blacklist check in validation pipeline, cached (Caffeine L1 + Redis L2) | Inconsistent blacklist checks | MEDIUM |
| JWKS management | Static key loading from PEM files | JWKS endpoint + runtime key resolution + key rotation support | No key rotation without restart | MEDIUM |
| Token type validation | Scattered type checks (parseMfaToken, parseAnonymousToken) | Type-specific validator registry with clean dispatch | Code duplication, brittle | MEDIUM |
| Validation audit | No validation events logged to event store | TokenValidatedEvent / TokenValidationFailedEvent recorded | No audit trail for validation | LOW |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build | Reuse Spring Security RS | Winner |
|--------|:---:|:---:|:---:|
| Time to market | 5-8 days | 3-5 days (partial, needs custom adapters) | Spring Security RS |
| Maintenance burden | Moderate (own code, well-tested) | Low (Spring maintains core, but custom adapters need maintenance) | Tie |
| Feature coverage | 100% (9/9 Must) | 33% (3/9 Must, rest needs custom anyway) | Custom Build |
| Integration effort | Low (extend existing JwtService) | Medium (replace JwtAuthFilter with Spring Security filter chain) | Custom Build |
| Long-term flexibility | High (full control over pipeline) | Medium (constrained by Spring Security opinions) | Custom Build |
| Risk | Low (incremental improvement) | Medium (breaking change to filter chain) | Custom Build |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | jjwt (current) | Spring Security RS | Custom Build |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 5/10 (1.50) | 5/10 (1.50) | 10/10 (3.00) |
| Integration ease | 25% | 9/10 (2.25) | 6/10 (1.50) | 8/10 (2.00) |
| Maintenance | 20% | 8/10 (1.60) | 9/10 (1.80) | 7/10 (1.40) |
| Community/Support | 15% | 9/10 (1.35) | 10/10 (1.50) | 5/10 (0.75) |
| Learning curve | 10% | 9/10 (0.90) | 7/10 (0.70) | 8/10 (0.80) |
| **Tổng điểm (weighted)** | | **7.60** | **7.00** | **7.95** |

### Reasoning

**Recommended approach**: Build from scratch (augment existing jjwt-based implementation)

**Lý do**:
1. **Feature coverage**: Only custom build covers all 9 must-have features. Spring Security RS covers 3/9 and would still require significant custom code for blacklist, token types, event sourcing, and service tokens — resulting in a hybrid that's harder to maintain than a clean custom solution.
2. **Integration risk**: Replacing `JwtAuthFilter` with Spring Security's `BearerTokenAuthenticationFilter` would break the existing token type discrimination (access/anonymous/mfa), blacklist integration, and session management logic. The risk of regression outweighs the benefit of JWKS auto-fetch (which can be built in < 1 day).
3. **Architecture alignment**: The project uses Clean Architecture with explicit ports and adapters. A custom validation pipeline fits naturally as an application service with clear port dependencies. Spring Security's opinionated approach conflicts with this architecture.

**Trade-offs chấp nhận**:
- **Maintenance burden** — chấp nhận vì the team already maintains JwtService, JwtAuthFilter, ServiceAuthFilter; adding a validation pipeline is incremental
- **No community-backed JWKS management** — chấp nhận vì JWKS key fetch/cache is < 200 LOC and can reference nimbus-jose-jwt's `RemoteJWKSet` pattern

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| Algorithm confusion attack (if whitelist not properly enforced) | LOW | HIGH | Explicit algorithm whitelist in JwtService.parseToken() — reject any alg not in {RS256, HS256} |
| Stale blacklist cache (Caffeine L1 TTL) | MEDIUM | LOW | Configure L1 TTL = 30s; Redis pub/sub invalidation for critical revocations |
| Key rotation downtime | LOW | MEDIUM | Multi-key JWKS endpoint; new key published before signing starts; old key retained until max-token-TTL |
| Service token HMAC key compromise | LOW | HIGH | Rotate HMAC key via config; consider migrating service tokens to RS256 in future |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| jjwt augment (custom pipeline) | 5-8 days | MEDIUM | LOW |
| Spring Security RS (partial adopt) | 6-10 days (setup + custom adapters) | MEDIUM | MEDIUM |
| Keycloak (full migration) | 15-25 days | HIGH | MEDIUM |
| Custom Build (recommended) | 5-8 days | MEDIUM | LOW |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation |

---

> **Next step**: Business Analysis (business_analysis.md)
